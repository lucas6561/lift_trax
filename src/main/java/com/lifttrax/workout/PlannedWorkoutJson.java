package com.lifttrax.workout;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** JSON import/export helpers for planned workout files. */
public final class PlannedWorkoutJson {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final ObjectWriter PRETTY_JSON = JSON.writerWithDefaultPrettyPrinter();

  private PlannedWorkoutJson() {}

  public static PlannedWorkoutFile readString(String json) throws JsonProcessingException {
    return JSON.readValue(json, PlannedWorkoutFile.class);
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
}
