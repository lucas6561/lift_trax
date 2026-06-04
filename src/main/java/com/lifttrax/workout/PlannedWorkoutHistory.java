package com.lifttrax.workout;

import com.lifttrax.db.Database;

/** Shared local-history lookup for planned-workout display formats. */
public final class PlannedWorkoutHistory {
  private PlannedWorkoutHistory() {}

  public static Summary lookup(
      Database db,
      PlannedWorkoutFile.PlannedWorkoutBlock block,
      PlannedWorkoutFile.PlannedExercise exercise) {
    if (db == null) {
      return Summary.empty();
    }

    try {
      boolean includeDeload =
          exercise.plannedSets().stream().anyMatch(PlannedWorkoutFile.PlannedSetTarget::deload);
      String last =
          WorkoutHistoryFormatter.lastExecutionSummary(
              db,
              exercise.name(),
              block.warmup(),
              PlannedWorkoutText.historyMetric(block, exercise),
              includeDeload);
      String best = WorkoutHistoryFormatter.bestOneRepMax(db, exercise.name());
      return new Summary(last, best, false);
    } catch (Exception e) {
      return new Summary(null, null, true);
    }
  }

  public record Summary(String last, String bestOneRepMax, boolean unavailable) {
    public static Summary empty() {
      return new Summary(null, null, false);
    }

    public boolean isEmpty() {
      return last == null && bestOneRepMax == null;
    }
  }
}
