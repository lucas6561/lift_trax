package com.lifttrax.workout;

import com.lifttrax.db.Database;
import com.lifttrax.models.LiftExecution;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    return load(db, List.of(exercise.name())).lookup(block, exercise);
  }

  public static Snapshot load(Database db, PlannedWorkoutFile workoutFile) {
    Set<String> liftNames = new LinkedHashSet<>();
    for (PlannedWorkoutFile.PlannedWorkoutWeek week : workoutFile.weeks()) {
      for (PlannedWorkoutFile.PlannedWorkoutDay day : week.days()) {
        addLiftNames(liftNames, day);
      }
    }
    return load(db, liftNames);
  }

  public static Snapshot load(Database db, PlannedWorkoutFile.PlannedWorkoutDay day) {
    Set<String> liftNames = new LinkedHashSet<>();
    addLiftNames(liftNames, day);
    return load(db, liftNames);
  }

  private static Snapshot load(Database db, Collection<String> liftNames) {
    if (db == null) {
      return Snapshot.empty();
    }
    try {
      return new Snapshot(db.getExecutionsByLift(liftNames), false);
    } catch (Exception e) {
      return new Snapshot(Map.of(), true);
    }
  }

  private static void addLiftNames(
      Set<String> liftNames, PlannedWorkoutFile.PlannedWorkoutDay day) {
    for (PlannedWorkoutFile.PlannedWorkoutBlock block : day.blocks()) {
      for (PlannedWorkoutFile.PlannedExercise exercise : block.exercises()) {
        liftNames.add(exercise.name());
      }
    }
  }

  public static final class Snapshot {
    private final Map<String, List<LiftExecution>> executionsByLift;
    private final boolean unavailable;

    private Snapshot(Map<String, List<LiftExecution>> executionsByLift, boolean unavailable) {
      this.executionsByLift = executionsByLift;
      this.unavailable = unavailable;
    }

    public Summary lookup(
        PlannedWorkoutFile.PlannedWorkoutBlock block, PlannedWorkoutFile.PlannedExercise exercise) {
      if (unavailable) {
        return new Summary(null, null, true);
      }
      try {
        List<LiftExecution> executions = executionsByLift.getOrDefault(exercise.name(), List.of());
        boolean includeDeload =
            exercise.plannedSets().stream().anyMatch(PlannedWorkoutFile.PlannedSetTarget::deload);
        String last =
            WorkoutHistoryFormatter.lastExecutionSummary(
                executions,
                block.warmup(),
                PlannedWorkoutText.historyMetric(block, exercise),
                includeDeload);
        String best = WorkoutHistoryFormatter.bestOneRepMax(executions);
        return new Summary(last, best, false);
      } catch (Exception e) {
        return new Summary(null, null, true);
      }
    }

    public String suggestedWeight(String liftName, PlannedWorkoutFile.PlannedSetTarget target) {
      if (unavailable) {
        return "";
      }
      try {
        String suggested =
            WorkoutHistoryFormatter.suggestedWeight(
                executionsByLift.getOrDefault(liftName, List.of()), target);
        return suggested == null ? "" : suggested;
      } catch (Exception e) {
        return "";
      }
    }

    private static Snapshot empty() {
      return new Snapshot(Map.of(), false);
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
