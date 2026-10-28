package com.lifttrax.catalog;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifttrax.models.CatalogEntry;
import com.lifttrax.models.CatalogProvenance;
import com.lifttrax.models.Lift;
import com.lifttrax.models.LiftRegion;
import com.lifttrax.models.LiftType;
import com.lifttrax.models.Muscle;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Validates the standalone exercise-catalog v1 contract before preview or persistence. */
public final class ExerciseCatalogFile {
  private static final ObjectMapper JSON =
      new ObjectMapper()
          .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
          .enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

  private ExerciseCatalogFile() {}

  public static List<CatalogEntry> parse(String json) {
    JsonNode root;
    try {
      root = JSON.readTree(json);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Malformed JSON: " + e.getOriginalMessage(), e);
    }
    require(root != null && root.isObject(), "$", "expected an object");
    fields(root, Set.of("schemaVersion", "exercises"), "$");
    JsonNode version = root.path("schemaVersion");
    require(
        version.isIntegralNumber() && version.canConvertToInt(),
        "$.schemaVersion",
        "expected an integer");
    if (version.intValue() != 1) {
      throw new IllegalArgumentException(
          "Unsupported exercise-catalog schema version: " + version + ". Supported versions: 1.");
    }
    JsonNode exercises = root.path("exercises");
    require(
        exercises.isArray() && !exercises.isEmpty() && exercises.size() <= 1000,
        "$.exercises",
        "expected 1 to 1000 exercises");
    List<CatalogEntry> result = new ArrayList<>();
    for (int i = 0; i < exercises.size(); i++) {
      result.add(exercise(exercises.get(i), "$.exercises[" + i + "]"));
    }
    CatalogImportPlan.validateDefinitions(result);
    return List.copyOf(result);
  }

  private static CatalogEntry exercise(JsonNode node, String path) {
    require(node.isObject(), path, "expected an object");
    fields(node, Set.of("name", "enabled", "region", "main", "muscles", "notes", "source"), path);
    String name = text(node, "name", path, 200, false);
    require(name.equals(name.trim()), path + ".name", "remove leading or trailing whitespace");
    require(node.path("enabled").isBoolean(), path + ".enabled", "expected a boolean");
    LiftRegion region =
        enumeration(LiftRegion.class, text(node, "region", path, 20, false), path + ".region");
    require(node.has("main"), path + ".main", "required; use null for no main lift classification");
    LiftType main =
        node.path("main").isNull()
            ? null
            : enumeration(LiftType.class, text(node, "main", path, 30, false), path + ".main");
    JsonNode tags = node.path("muscles");
    require(tags.isArray(), path + ".muscles", "expected an array");
    List<Muscle> muscles = new ArrayList<>();
    Set<Muscle> unique = EnumSet.noneOf(Muscle.class);
    for (JsonNode tag : tags) {
      require(tag.isTextual(), path + ".muscles", "expected enum strings");
      Muscle muscle = enumeration(Muscle.class, tag.asText(), path + ".muscles");
      require(unique.add(muscle), path + ".muscles", "duplicate muscle tag");
      muscles.add(muscle);
    }
    String notes = text(node, "notes", path, 10000, true);
    CatalogProvenance provenance = new CatalogProvenance("file_import", null, null, null, null);
    if (node.has("source")) {
      JsonNode source = node.get("source");
      require(source.isObject(), path + ".source", "expected an object");
      fields(source, Set.of("namespace", "key", "revision"), path + ".source");
      String namespace = text(source, "namespace", path + ".source", 200, false);
      String key = text(source, "key", path + ".source", 200, false);
      String revision =
          source.has("revision") ? text(source, "revision", path + ".source", 200, false) : null;
      provenance =
          new CatalogProvenance(
              "lifttrax:starter".equals(namespace) ? "starter" : "file_import",
              namespace,
              key,
              revision,
              null);
    }
    return new CatalogEntry(
        new Lift(name, region, main, List.copyOf(muscles), notes),
        node.get("enabled").booleanValue(),
        provenance);
  }

  private static <E extends Enum<E>> E enumeration(Class<E> type, String value, String path) {
    try {
      return Enum.valueOf(type, value);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "Schema validation failed at " + path + ": unsupported value " + value, e);
    }
  }

  private static String text(JsonNode node, String field, String path, int max, boolean empty) {
    JsonNode value = node.path(field);
    require(
        value.isTextual()
            && value.textValue().length() <= max
            && (empty || !value.textValue().isBlank()),
        path + "." + field,
        "expected " + (empty ? "a" : "a nonblank") + " string of at most " + max + " characters");
    return value.textValue();
  }

  private static void fields(JsonNode node, Set<String> allowed, String path) {
    node.fieldNames()
        .forEachRemaining(
            field -> require(allowed.contains(field), path + "." + field, "unknown field"));
  }

  private static void require(boolean condition, String path, String message) {
    if (!condition) {
      throw new IllegalArgumentException("Schema validation failed at " + path + ": " + message);
    }
  }
}
