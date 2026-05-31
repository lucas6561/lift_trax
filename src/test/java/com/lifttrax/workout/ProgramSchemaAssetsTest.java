package com.lifttrax.workout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProgramSchemaAssetsTest {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final List<Path> SCHEMAS =
      List.of(
          Path.of("shared", "programs", "schema", "program.schema.v1.json"),
          Path.of("shared", "programs", "schema", "program.schema.v2.json"));
  private static final List<Path> EXAMPLES =
      List.of(
          Path.of("shared", "programs", "examples", "conjugate-v1.json"),
          Path.of("shared", "programs", "examples", "hypertrophy-v1.json"),
          Path.of("shared", "programs", "examples", "conjugate-v2.json"),
          Path.of("shared", "programs", "examples", "hypertrophy-v2.json"));
  private static final Path LATEST_CONJUGATE_EXAMPLE =
      Path.of("shared", "programs", "examples", "conjugate-v2.json");
  private static final Path LATEST_HYPERTROPHY_EXAMPLE =
      Path.of("shared", "programs", "examples", "hypertrophy-v2.json");

  @Test
  void schemaAndExamplesAreValidJson() throws IOException {
    for (Path path : allJsonAssets()) {
      assertTrue(Files.isRegularFile(path), path.toString());
      assertFalse(JSON.readTree(path.toFile()).isMissingNode(), path.toString());
    }
  }

  @Test
  void examplesDeclareSupportedVersionsAndRequiredContractSections() throws IOException {
    for (Path path : EXAMPLES) {
      JsonNode root = read(path);

      assertTrue(
          ProgramSchemaVersions.supports(root.path("schemaVersion").asInt()), path.toString());
      assertPresent(root, "program", path);
      assertPresent(root, "exercisePools", path);
      assertPresent(root, "progressionRules", path);
      assertPresent(root, "substitutionRules", path);
      assertPresent(root, "dayTemplates", path);
      assertPresent(root, "weeks", path);
      assertEquals(root.path("program").path("durationWeeks").asInt(), root.path("weeks").size());
      for (JsonNode week : root.path("weeks")) {
        assertEquals(root.path("program").path("daysPerWeek").asInt(), week.path("days").size());
      }
    }
  }

  @Test
  void examplesReferenceDefinedTemplatesPoolsAndRules() throws IOException {
    for (Path path : EXAMPLES) {
      JsonNode root = read(path);

      validateTemplateRefs(root, path);
      validateObjectRefs(root, "poolRef", "exercisePools", path);
      validateObjectRefs(root, "progressionRuleRef", "progressionRules", path);
      validateObjectRefs(root, "substitutionRuleRef", "substitutionRules", path);
      validateAllowedPoolRefs(root, path);
    }
  }

  @Test
  void examplesCoverCurrentBuilderSections() throws IOException {
    JsonNode conjugate = read(LATEST_CONJUGATE_EXAMPLE);
    assertTrue(hasTextValue(conjugate, "Max Effort Single"));
    assertTrue(hasTextValue(conjugate, "Backoff Sets"));
    assertTrue(hasTextValue(conjugate, "Supplemental Sets"));
    assertTrue(hasTextValue(conjugate, "Dynamic Effort"));
    assertTrue(hasTextValue(conjugate, "Accessory Circuit"));
    assertTrue(hasTextValue(conjugate, "Conditioning"));

    JsonNode hypertrophy = read(LATEST_HYPERTROPHY_EXAMPLE);
    assertTrue(hasTextValue(hypertrophy, "Primary Hypertrophy"));
    assertTrue(hasTextValue(hypertrophy, "Secondary Hypertrophy"));
    assertTrue(hasTextValue(hypertrophy, "Accessory"));
    assertTrue(hasTextValue(hypertrophy, "Core"));
  }

  private static List<Path> allJsonAssets() {
    List<Path> paths = new ArrayList<>();
    paths.addAll(SCHEMAS);
    paths.addAll(EXAMPLES);
    return paths;
  }

  private static JsonNode read(Path path) throws IOException {
    return JSON.readTree(path.toFile());
  }

  private static void assertPresent(JsonNode root, String field, Path path) {
    JsonNode value = root.path(field);
    assertFalse(value.isMissingNode(), path + " missing " + field);
    assertFalse(value.isEmpty(), path + " has empty " + field);
  }

  private static void validateTemplateRefs(JsonNode root, Path path) {
    JsonNode templates = root.path("dayTemplates");
    for (JsonNode week : root.path("weeks")) {
      for (JsonNode day : week.path("days")) {
        String ref = day.path("templateRef").asText();
        assertTrue(templates.has(ref), path + " missing day template " + ref);
      }
    }
  }

  private static void validateObjectRefs(
      JsonNode root, String refField, String targetSection, Path path) {
    List<String> refs = new ArrayList<>();
    collectTextFields(root, refField, refs);
    JsonNode targets = root.path(targetSection);
    List<String> missing = refs.stream().filter(ref -> !targets.has(ref)).distinct().toList();
    assertTrue(missing.isEmpty(), path + " has missing " + refField + " values: " + missing);
  }

  private static void validateAllowedPoolRefs(JsonNode root, Path path) {
    JsonNode pools = root.path("exercisePools");
    for (JsonNode rule : root.path("substitutionRules")) {
      for (JsonNode poolRef : rule.path("allowedPoolRefs")) {
        String ref = poolRef.asText();
        assertTrue(pools.has(ref), path + " has missing allowedPoolRefs value: " + ref);
      }
    }
  }

  private static void collectTextFields(JsonNode node, String fieldName, List<String> values) {
    if (node.isObject()) {
      if (node.has(fieldName)) {
        values.add(node.path(fieldName).asText());
      }
      Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
      while (fields.hasNext()) {
        collectTextFields(fields.next().getValue(), fieldName, values);
      }
      return;
    }

    if (node.isArray()) {
      for (JsonNode child : node) {
        collectTextFields(child, fieldName, values);
      }
    }
  }

  private static boolean hasTextValue(JsonNode node, String value) {
    if (node.isTextual()) {
      return value.equals(node.asText());
    }
    if (node.isObject()) {
      Iterator<JsonNode> values = node.elements();
      while (values.hasNext()) {
        if (hasTextValue(values.next(), value)) {
          return true;
        }
      }
      return false;
    }
    if (node.isArray()) {
      for (JsonNode child : node) {
        if (hasTextValue(child, value)) {
          return true;
        }
      }
    }
    return false;
  }
}
