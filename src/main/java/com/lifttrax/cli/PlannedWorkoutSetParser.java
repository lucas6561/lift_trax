package com.lifttrax.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.lifttrax.models.ExecutionSet;
import com.lifttrax.models.SetMetric;
import java.util.ArrayList;
import java.util.List;

/** Parses and validates completed set payloads from a planned workout session. */
final class PlannedWorkoutSetParser {
  private static final String COMPLETE = "complete";
  private static final String SKIPPED = "skipped";

  private PlannedWorkoutSetParser() {}

  static ParsedSets parseSets(JsonNode setsNode) {
    if (!setsNode.isArray()) {
      throw new IllegalArgumentException("Completed exercise sets must be a JSON array.");
    }
    List<ExecutionSet> sets = new ArrayList<>();
    int skippedSets = 0;
    for (JsonNode setResult : setsNode) {
      String state = requiredState(setResult.path("state").asText(COMPLETE));
      if (SKIPPED.equals(state)) {
        skippedSets++;
        continue;
      }
      SetMetric metric = parseMetric(setResult);
      String weight = setResult.path("weight").asText("").trim();
      Float rpe = parseRpe(setResult.path("rpe").asText(""));
      sets.add(new ExecutionSet(metric, weight, rpe, setResult.path("missed").asBoolean(false)));
    }
    return new ParsedSets(List.copyOf(sets), skippedSets);
  }

  private static SetMetric parseMetric(JsonNode setResult) {
    String metricType = setResult.path("metricType").asText("reps");
    return switch (metricType) {
      case "reps-lr" ->
          new SetMetric.RepsLr(
              completedMetricValue(setResult, "metricLeft", "Left reps"),
              completedMetricValue(setResult, "metricRight", "Right reps"));
      case "time" ->
          new SetMetric.TimeSecs(completedMetricValue(setResult, "metricValue", "Seconds"));
      case "distance" ->
          new SetMetric.DistanceFeet(completedMetricValue(setResult, "metricValue", "Feet"));
      case "reps" -> new SetMetric.Reps(completedMetricValue(setResult, "metricValue", "Reps"));
      default ->
          throw new IllegalArgumentException("Unsupported completed set metric: " + metricType);
    };
  }

  private static int completedMetricValue(JsonNode node, String field, String label) {
    String value = node.path(field).asText("").trim();
    boolean missed = node.path("missed").asBoolean(false);
    try {
      int parsed = Integer.parseInt(value);
      if (parsed > 0 || (missed && parsed == 0)) {
        return parsed;
      }
    } catch (NumberFormatException ignored) {
      // Report the same actionable validation message for blank and malformed values.
    }
    throw new IllegalArgumentException(
        label
            + (missed ? " must be 0 or greater for a missed target." : " must be greater than 0."));
  }

  private static Float parseRpe(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      float parsed = Float.parseFloat(value.trim());
      if (parsed >= 0.0f && parsed <= 10.0f) {
        return parsed;
      }
    } catch (NumberFormatException ignored) {
      // Report a consistent validation message below.
    }
    throw new IllegalArgumentException("RPE must be between 0 and 10.");
  }

  static String requiredState(String state) {
    if (COMPLETE.equals(state) || SKIPPED.equals(state)) {
      return state;
    }
    throw new IllegalArgumentException("Workout state must be complete or skipped.");
  }

  record ParsedSets(List<ExecutionSet> completedSets, int skippedSets) {}
}
