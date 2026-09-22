package com.lifttrax.db;

import com.lifttrax.models.LiftExecution;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Database operations needed by the web training surfaces. */
public interface TrainingDataStore extends Database {
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
