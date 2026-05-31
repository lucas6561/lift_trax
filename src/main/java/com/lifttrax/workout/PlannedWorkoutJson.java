package com.lifttrax.workout;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** JSON import/export helpers for planned workout files. */
public final class PlannedWorkoutJson {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final ObjectWriter PRETTY_JSON = JSON.writerWithDefaultPrettyPrinter();
  private static final Map<Integer, PlannedWorkoutReader> READERS =
      Map.of(
          1, PlannedWorkoutJson::readCompatibleShape,
          2, PlannedWorkoutJson::readCompatibleShape);

  private PlannedWorkoutJson() {}

  public static PlannedWorkoutFile readString(String json) throws JsonProcessingException {
    JsonNode root = JSON.readTree(json);
    if (root == null || !root.isObject()) {
      throw invalid("Planned workout must be a JSON object.");
    }

    JsonNode version = root.path("schemaVersion");
    if (!version.isIntegralNumber() || !version.canConvertToInt()) {
      throw invalid("Planned workout schemaVersion must be an integer.");
    }

    int schemaVersion = version.asInt();
    PlannedWorkoutReader reader = READERS.get(schemaVersion);
    if (reader == null) {
      throw invalid(PlannedWorkoutSchemaVersions.unsupportedVersionMessage(schemaVersion));
    }
    return reader.read(root);
  }

  public static PlannedWorkoutFile readPath(Path path) throws IOException {
    return readString(Files.readString(path, StandardCharsets.UTF_8));
  }

  public static String writeString(PlannedWorkoutFile workoutFile) throws JsonProcessingException {
    return PRETTY_JSON.writeValueAsString(workoutFile);
  }

  public static void writePath(Path path, PlannedWorkoutFile workoutFile) throws IOException {
    Files.writeString(path, writeString(workoutFile), StandardCharsets.UTF_8);
  }

  private static PlannedWorkoutFile readCompatibleShape(JsonNode root)
      throws JsonProcessingException {
    return JSON.treeToValue(root, PlannedWorkoutFile.class);
  }

  private static JsonMappingException invalid(String message) {
    return JsonMappingException.from((JsonParser) null, message);
  }

  @FunctionalInterface
  private interface PlannedWorkoutReader {
    PlannedWorkoutFile read(JsonNode root) throws JsonProcessingException;
  }
}
