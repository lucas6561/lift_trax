package com.lifttrax.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifttrax.db.TrainingDataStore;
import com.lifttrax.db.WorkoutSubmissionReceipt;
import com.lifttrax.models.ExecutionSet;
import com.lifttrax.models.LiftExecution;
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
    SaveSummary prior = priorSubmission(db, normalizedSubmissionId, fingerprint);
    if (prior != null) {
      return prior;
    }
    Map<String, PlannedExerciseItem> plannedByKey = plannedByKey(day);
    JsonNode submitted = readSubmittedResults(resultsJson);
    validateSubmissionSize(requireEveryPlannedExercise, submitted.size(), plannedByKey.size());
    if (emptyPartialSubmission(requireEveryPlannedExercise, submitted)) {
      SaveSummary summary = new SaveSummary(List.of(), 0, 0);
      recordSubmission(db, normalizedSubmissionId, fingerprint, summary);
      return summary;
    }

    List<PendingExecution> pending = new ArrayList<>();
    Set<String> submittedKeys = new HashSet<>();
    int skippedExercises = 0;
    int skippedSets = 0;
    for (JsonNode exerciseResult : submitted) {
      ParsedExercise parsed = parseExercise(db, exerciseResult, plannedByKey, submittedKeys, date);
      if (parsed.skippedExercise()) {
        skippedExercises++;
        continue;
      }
      skippedSets += parsed.skippedSets();
      if (parsed.pending() != null) {
        pending.add(parsed.pending());
      }
    }
    List<LoggedExercise> logged = logPending(db, pending, normalizedSubmissionId);
    SaveSummary summary = new SaveSummary(List.copyOf(logged), skippedExercises, skippedSets);
    recordSubmission(db, normalizedSubmissionId, fingerprint, summary);
    return summary;
  }

  private static SaveSummary priorSubmission(
      TrainingDataStore db, String submissionId, String fingerprint) throws Exception {
    if (submissionId.isBlank()) {
      return null;
    }
    WorkoutSubmissionReceipt receipt = db.getWorkoutSubmission(submissionId);
    if (receipt == null) {
      return null;
    }
    if (!receipt.fingerprint().equals(fingerprint)) {
      throw new IllegalArgumentException(
          "This workout block changed after it was already submitted. Resume the saved version or start a new workout.");
    }
    return SaveSummary.fromReceipt(receipt);
  }

  private static void validateSubmissionSize(
      boolean requireEvery, int submittedCount, int plannedCount) {
    if (requireEvery && submittedCount != plannedCount) {
      throw new IllegalArgumentException("Submit a result for every planned exercise.");
    }
  }

  private static boolean emptyPartialSubmission(boolean requireEvery, JsonNode submitted) {
    return !requireEvery && submitted.isEmpty();
  }

  private static ParsedExercise parseExercise(
      TrainingDataStore db,
      JsonNode result,
      Map<String, PlannedExerciseItem> plannedByKey,
      Set<String> submittedKeys,
      LocalDate date)
      throws Exception {
    PlannedExerciseItem planned = plannedExercise(result, plannedByKey, submittedKeys);
    String state = PlannedWorkoutSetParser.requiredState(result.path("state").asText(""));
    if (SKIPPED.equals(state)) {
      return new ParsedExercise(true, 0, null);
    }
    PlannedWorkoutSetParser.ParsedSets sets =
        PlannedWorkoutSetParser.parseSets(result.path("sets"));
    if (sets.completedSets().isEmpty()) {
      return new ParsedExercise(false, sets.skippedSets(), null);
    }
    String performedLift = requiredText(result, "performedLift", "Performed lift");
    validatePerformedLift(db, performedLift);
    LiftExecution execution = execution(result, planned, sets.completedSets(), date);
    PendingExecution pending =
        new PendingExecution(
            performedLift, execution, !performedLift.equals(planned.exercise().name()));
    return new ParsedExercise(false, sets.skippedSets(), pending);
  }

  private static PlannedExerciseItem plannedExercise(
      JsonNode result, Map<String, PlannedExerciseItem> plannedByKey, Set<String> submittedKeys) {
    String key = requiredText(result, "exerciseKey", "Exercise key");
    if (!submittedKeys.add(key)) {
      throw new IllegalArgumentException("Each planned exercise can only be submitted once.");
    }
    PlannedExerciseItem planned = plannedByKey.get(key);
    if (planned == null) {
      throw new IllegalArgumentException("Unknown planned exercise: " + key);
    }
    String plannedLift = requiredText(result, "plannedLift", "Planned lift");
    if (!planned.exercise().name().equals(plannedLift)) {
      throw new IllegalArgumentException("Planned exercise does not match the imported workout.");
    }
    return planned;
  }

  private static void validatePerformedLift(TrainingDataStore db, String performedLift)
      throws Exception {
    db.getLift(performedLift);
    if (!db.isLiftEnabled(performedLift)) {
      throw new IllegalArgumentException(
          "Lift is disabled: " + performedLift + ". Enable it before logging new executions.");
    }
  }

  private static LiftExecution execution(
      JsonNode result, PlannedExerciseItem planned, List<ExecutionSet> sets, LocalDate date) {
    boolean warmup = result.path("warmup").asBoolean(planned.block().warmup());
    boolean plannedDeload =
        planned.exercise().plannedSets().stream()
            .anyMatch(PlannedWorkoutFile.PlannedSetTarget::deload);
    boolean deload = result.path("deload").asBoolean(plannedDeload);
    return new LiftExecution(null, date, sets, warmup, deload, result.path("notes").asText(""));
  }

  private static List<LoggedExercise> logPending(
      TrainingDataStore db, List<PendingExecution> pending, String submissionId) throws Exception {
    List<LoggedExercise> logged = new ArrayList<>();
    for (PendingExecution item : pending) {
      if (submissionId.isBlank()
          && hasMatchingExecution(db, item.performedLift(), item.execution())) {
        continue;
      }
      db.addLiftExecution(item.performedLift(), item.execution());
      logged.add(
          new LoggedExercise(
              item.performedLift(), item.execution().sets().size(), item.substitution()));
    }
    return logged;
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

  private record ParsedExercise(
      boolean skippedExercise, int skippedSets, PendingExecution pending) {}
}
