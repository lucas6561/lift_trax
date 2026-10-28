package com.lifttrax.db;

import com.lifttrax.models.CatalogEntry;
import com.lifttrax.models.CatalogProvenance;
import com.lifttrax.models.Lift;
import com.lifttrax.models.LiftExecution;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Database operations needed by the web training surfaces. */
public interface TrainingDataStore extends Database {
  default List<CatalogEntry> listCatalogEntries() throws Exception {
    Map<String, Boolean> enabled = liftEnabledStatuses();
    return listLifts().stream()
        .map(
            lift ->
                new CatalogEntry(
                    lift, enabled.getOrDefault(lift.name(), true), CatalogProvenance.unknown()))
        .toList();
  }

  default int importCatalog(List<CatalogEntry> entries, String expectedFingerprint)
      throws Exception {
    throw new UnsupportedOperationException("Exercise catalog imports require Postgres.");
  }

  default List<LiftExecutionRow> performedHistory() throws Exception {
    return getExecutionsBetween(LocalDate.of(1, 1, 1), LocalDate.of(9999, 12, 31));
  }

  default List<LiftExecutionRow> liftHistory(String name) throws Exception {
    Lift lift = getLift(name);
    return getExecutions(name).stream()
        .map(execution -> new LiftExecutionRow(lift, execution))
        .toList();
  }

  default List<LiftExecutionRow> historyForExecution(int executionId) throws Exception {
    List<LiftExecutionRow> history = performedHistory();
    String name =
        history.stream()
            .filter(row -> Integer.valueOf(executionId).equals(row.execution().id()))
            .map(row -> row.lift().name())
            .findFirst()
            .orElse(null);
    return name == null
        ? List.of()
        : history.stream().filter(row -> row.lift().name().equals(name)).toList();
  }

  DashboardSnapshot dashboardSnapshot(LocalDate today) throws Exception;

  default List<SavedWorkout> listSavedWorkouts() throws Exception {
    return List.of();
  }

  default String saveWorkout(String name, String workoutJson) throws Exception {
    throw new UnsupportedOperationException("Saved workouts require Postgres.");
  }

  default String getSavedWorkoutJson(String id) throws Exception {
    throw new IllegalArgumentException("Saved workout not found.");
  }

  default void renameSavedWorkout(String id, String name) throws Exception {
    throw new UnsupportedOperationException("Saved workouts require Postgres.");
  }

  default void deleteSavedWorkout(String id) throws Exception {
    throw new UnsupportedOperationException("Saved workouts require Postgres.");
  }

  List<LiftExecutionRow> getExecutionsBetween(LocalDate start, LocalDate end) throws Exception;

  ExecutionHistorySummary executionHistorySummary(LocalDate start, LocalDate end) throws Exception;

  LiftExecution getLastExecution(String liftName, boolean warmup, boolean deload) throws Exception;

  LiftExecution getExecution(String liftName, int executionId) throws Exception;

  Map<String, Boolean> liftEnabledStatuses() throws Exception;

  WorkoutSubmissionReceipt getWorkoutSubmission(String submissionId) throws Exception;

  void recordWorkoutSubmission(String submissionId, WorkoutSubmissionReceipt receipt)
      throws Exception;
}
