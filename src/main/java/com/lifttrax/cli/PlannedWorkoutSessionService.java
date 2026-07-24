package com.lifttrax.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifttrax.db.TrainingDataStore;
import com.lifttrax.db.WorkoutSubmissionReceipt;
import com.lifttrax.models.ExecutionSet;
import com.lifttrax.models.LiftExecution;
import com.lifttrax.models.SetMetric;
import com.lifttrax.workout.PlannedWorkoutFile;
import com.lifttrax.workout.PlannedWorkoutJson;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Validates and saves completed work from an imported planned-workout day. */
final class PlannedWorkoutSessionService {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String COMPLETE = "complete";
  private static final String SKIPPED = "skipped";

  private PlannedWorkoutSessionService() {}

  static PlannedWorkoutFile.PlannedWorkoutDay findDay(
      PlannedWorkoutFile workoutFile, int weekNumber, String dayOfWeek) {
    for (PlannedWorkoutFile.PlannedWorkoutWeek week : workoutFile.weeks()) {
      if (week.weekNumber() != weekNumber) {
        continue;
      }
      for (PlannedWorkoutFile.PlannedWorkoutDay day : week.days()) {
        if (day.dayOfWeek().equals(dayOfWeek)) {
          return day;
        }
      }
    }
    throw new IllegalArgumentException(
        "Planned workout day not found: week " + weekNumber + " / " + dayOfWeek);
  }

  static List<PlannedExerciseItem> plannedExercises(PlannedWorkoutFile.PlannedWorkoutDay day) {
    List<PlannedExerciseItem> items = new ArrayList<>();
    for (PlannedWorkoutFile.PlannedWorkoutBlock block : day.blocks()) {
      for (int exerciseIndex = 0; exerciseIndex < block.exercises().size(); exerciseIndex++) {
        items.add(
            new PlannedExerciseItem(
                block.order() + ":" + exerciseIndex, block, block.exercises().get(exerciseIndex)));
      }
    }
    return items;
  }

  static SaveSummary save(
      TrainingDataStore db,
      PlannedWorkoutFile workoutFile,
      int weekNumber,
      String dayOfWeek,
      LocalDate date,
      String resultsJson)
      throws Exception {
    return saveSubmittedResults(db, workoutFile, weekNumber, dayOfWeek, date, resultsJson, true);
  }

  static SaveSummary saveSubmittedResults(
      TrainingDataStore db,
      PlannedWorkoutFile workoutFile,
      int weekNumber,
      String dayOfWeek,
      LocalDate date,
      String resultsJson,
      boolean requireEveryPlannedExercise)
      throws Exception {
    return saveSubmittedResults(
        db, workoutFile, weekNumber, dayOfWeek, date, resultsJson, requireEveryPlannedExercise, "");
  }

  static SaveSummary saveSubmittedResults(
      TrainingDataStore db,
      PlannedWorkoutFile workoutFile,
      int weekNumber,
      String dayOfWeek,
      LocalDate date,
      String resultsJson,
      boolean requireEveryPlannedExercise,
      String submissionId)
      throws Exception {
    PlannedWorkoutFile.PlannedWorkoutDay day = findDay(workoutFile, weekNumber, dayOfWeek);
    String normalizedSubmissionId = normalizeSubmissionId(submissionId);
    String fingerprint =
        normalizedSubmissionId.isBlank()
            ? ""
            : submissionFingerprint(
                workoutFile, weekNumber, dayOfWeek, date, resultsJson, requireEveryPlannedExercise);
    if (!normalizedSubmissionId.isBlank()) {
      WorkoutSubmissionReceipt receipt = db.getWorkoutSubmission(normalizedSubmissionId);
      if (receipt != null) {
        if (!receipt.fingerprint().equals(fingerprint)) {
          throw new IllegalArgumentException(
              "This workout block changed after it was already submitted. Resume the saved version or start a new workout.");
        }
        return SaveSummary.fromReceipt(receipt);
      }
    }
    Map<String, PlannedExerciseItem> plannedByKey = plannedByKey(day);
    JsonNode submitted = readSubmittedResults(resultsJson);
    if (requireEveryPlannedExercise && submitted.size() != plannedByKey.size()) {
      throw new IllegalArgumentException("Submit a result for every planned exercise.");
    }
    if (!requireEveryPlannedExercise && submitted.size() == 0) {
      SaveSummary summary = new SaveSummary(List.of(), 0, 0);
      recordSubmission(db, normalizedSubmissionId, fingerprint, summary);
      return summary;
    }

    List<PendingExecution> pending = new ArrayList<>();
    Set<String> submittedKeys = new HashSet<>();
    int skippedExercises = 0;
    int skippedSets = 0;
    for (JsonNode exerciseResult : submitted) {
      String key = requiredText(exerciseResult, "exerciseKey", "Exercise key");
      if (!submittedKeys.add(key)) {
        throw new IllegalArgumentException("Each planned exercise can only be submitted once.");
      }
      PlannedExerciseItem planned = plannedByKey.get(key);
      if (planned == null) {
        throw new IllegalArgumentException("Unknown planned exercise: " + key);
      }
      String plannedLift = requiredText(exerciseResult, "plannedLift", "Planned lift");
      if (!planned.exercise().name().equals(plannedLift)) {
        throw new IllegalArgumentException("Planned exercise does not match the imported workout.");
      }
      String state = requiredState(exerciseResult.path("state").asText(""));
      if (SKIPPED.equals(state)) {
        skippedExercises++;
        continue;
      }

      ParsedSets parsedSets = parseSets(exerciseResult.path("sets"));
      skippedSets += parsedSets.skippedSets();
      if (parsedSets.completedSets().isEmpty()) {
        continue;
      }
      String performedLift = requiredText(exerciseResult, "performedLift", "Performed lift");
      db.getLift(performedLift);
      boolean warmup = exerciseResult.path("warmup").asBoolean(planned.block().warmup());
      boolean deload =
          exerciseResult
              .path("deload")
              .asBoolean(
                  planned.exercise().plannedSets().stream()
                      .anyMatch(PlannedWorkoutFile.PlannedSetTarget::deload));
      LiftExecution execution =
          new LiftExecution(
              null,
              date,
              parsedSets.completedSets(),
              warmup,
              deload,
              exerciseResult.path("notes").asText(""));
      pending.add(
          new PendingExecution(
              performedLift, execution, !performedLift.equals(planned.exercise().name())));
    }

    List<LoggedExercise> logged = new ArrayList<>();
    for (PendingExecution item : pending) {
      if (normalizedSubmissionId.isBlank()
          && hasMatchingExecution(db, item.performedLift(), item.execution())) {
        continue;
      }
      db.addLiftExecution(item.performedLift(), item.execution());
      logged.add(
          new LoggedExercise(
              item.performedLift(), item.execution().sets().size(), item.substitution()));
    }
    SaveSummary summary = new SaveSummary(List.copyOf(logged), skippedExercises, skippedSets);
    recordSubmission(db, normalizedSubmissionId, fingerprint, summary);
    return summary;
  }

  private static void recordSubmission(
      TrainingDataStore db, String submissionId, String fingerprint, SaveSummary summary)
      throws Exception {
    if (submissionId.isBlank()) {
      return;
    }
    db.recordWorkoutSubmission(
        submissionId,
        new WorkoutSubmissionReceipt(
            fingerprint,
            summary.loggedExecutionCount(),
            summary.skippedExercises(),
            summary.skippedSets()));
  }

  private static String normalizeSubmissionId(String submissionId) {
    String value = submissionId == null ? "" : submissionId.trim();
    if (value.length() > 200 || (!value.isBlank() && !value.matches("[A-Za-z0-9:_-]+"))) {
      throw new IllegalArgumentException("Workout submission ID is invalid.");
    }
    return value;
  }

  private static String submissionFingerprint(
      PlannedWorkoutFile workoutFile,
      int weekNumber,
      String dayOfWeek,
      LocalDate date,
      String resultsJson,
      boolean requireEveryPlannedExercise)
      throws Exception {
    String payload =
        PlannedWorkoutJson.writeString(workoutFile)
            + "\n"
            + weekNumber
            + "\n"
            + dayOfWeek
            + "\n"
            + date
            + "\n"
            + requireEveryPlannedExercise
            + "\n"
            + resultsJson;
    byte[] digest =
        MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8));
    return HexFormat.of().formatHex(digest);
  }

  private static boolean hasMatchingExecution(
      TrainingDataStore db, String liftName, LiftExecution execution) throws Exception {
    for (LiftExecution existing : db.getExecutions(liftName)) {
      if (existing.date().equals(execution.date())
          && existing.warmup() == execution.warmup()
          && existing.deload() == execution.deload()
          && text(existing.notes()).equals(text(execution.notes()))
          && existing.sets().equals(execution.sets())) {
        return true;
      }
    }
    return false;
  }

  static Set<String> recommendedLiftNames(PlannedWorkoutFile.PlannedExercise exercise) {
    Set<String> names = new java.util.LinkedHashSet<>();
    names.add(exercise.name());
    names.addAll(exercise.substitutionOptions());
    return names;
  }

  private static Map<String, PlannedExerciseItem> plannedByKey(
      PlannedWorkoutFile.PlannedWorkoutDay day) {
    Map<String, PlannedExerciseItem> items = new HashMap<>();
    for (PlannedExerciseItem item : plannedExercises(day)) {
      items.put(item.key(), item);
    }
    return items;
  }

  private static JsonNode readSubmittedResults(String resultsJson) {
    JsonNode root;
    try {
      root = JSON.readTree(resultsJson);
    } catch (Exception e) {
      throw new IllegalArgumentException("Workout session results must be valid JSON.", e);
    }
    if (root == null || !root.isArray()) {
      throw new IllegalArgumentException("Workout session results must be a JSON array.");
    }
    return root;
  }

  private static ParsedSets parseSets(JsonNode setsNode) {
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
      sets.add(new ExecutionSet(metric, weight, rpe));
    }
    return new ParsedSets(List.copyOf(sets), skippedSets);
  }

  private static SetMetric parseMetric(JsonNode setResult) {
    String metricType = setResult.path("metricType").asText("reps");
    return switch (metricType) {
      case "reps-lr" ->
          new SetMetric.RepsLr(
              positiveInt(setResult, "metricLeft", "Left reps"),
              positiveInt(setResult, "metricRight", "Right reps"));
      case "time" -> new SetMetric.TimeSecs(positiveInt(setResult, "metricValue", "Seconds"));
      case "distance" -> new SetMetric.DistanceFeet(positiveInt(setResult, "metricValue", "Feet"));
      case "reps" -> new SetMetric.Reps(positiveInt(setResult, "metricValue", "Reps"));
      default ->
          throw new IllegalArgumentException("Unsupported completed set metric: " + metricType);
    };
  }

  private static int positiveInt(JsonNode node, String field, String label) {
    String value = node.path(field).asText("").trim();
    try {
      int parsed = Integer.parseInt(value);
      if (parsed > 0) {
        return parsed;
      }
    } catch (NumberFormatException ignored) {
      // Report the same actionable validation message for blank and malformed values.
    }
    throw new IllegalArgumentException(label + " must be greater than 0.");
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

  private static String requiredState(String state) {
    if (COMPLETE.equals(state) || SKIPPED.equals(state)) {
      return state;
    }
    throw new IllegalArgumentException("Workout state must be complete or skipped.");
  }

  private static String requiredText(JsonNode node, String field, String label) {
    String value = node.path(field).asText("").trim();
    if (value.isBlank()) {
      throw new IllegalArgumentException(label + " is required.");
    }
    return value;
  }

  private static String text(String value) {
    return value == null ? "" : value;
  }

  record PlannedExerciseItem(
      String key,
      PlannedWorkoutFile.PlannedWorkoutBlock block,
      PlannedWorkoutFile.PlannedExercise exercise) {}

  record LoggedExercise(String liftName, int setCount, boolean substitution) {}

  record SaveSummary(
      List<LoggedExercise> loggedExercises,
      int skippedExercises,
      int skippedSets,
      int confirmedLoggedExecutionCount) {
    SaveSummary(List<LoggedExercise> loggedExercises, int skippedExercises, int skippedSets) {
      this(loggedExercises, skippedExercises, skippedSets, loggedExercises.size());
    }

    static SaveSummary fromReceipt(WorkoutSubmissionReceipt receipt) {
      return new SaveSummary(
          List.of(),
          receipt.skippedExercises(),
          receipt.skippedSets(),
          receipt.loggedExecutionCount());
    }

    int loggedExecutionCount() {
      return confirmedLoggedExecutionCount;
    }

    long substitutionCount() {
      return loggedExercises.stream().filter(LoggedExercise::substitution).count();
    }
  }

  private record PendingExecution(
      String performedLift, LiftExecution execution, boolean substitution) {}

  private record ParsedSets(List<ExecutionSet> completedSets, int skippedSets) {}
}
