package com.lifttrax.workout;

import com.lifttrax.db.Database;
import com.lifttrax.models.Lift;
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
    return load(db, day, List.of());
  }

  public static Snapshot load(
      Database db,
      PlannedWorkoutFile.PlannedWorkoutDay day,
      Collection<String> additionalLiftNames) {
    Set<String> liftNames = new LinkedHashSet<>(additionalLiftNames);
    addLiftNames(liftNames, day);
    return load(db, liftNames);
  }

  private static Snapshot load(Database db, Collection<String> liftNames) {
    if (db == null) {
      return Snapshot.empty();
    }
    try {
      Map<String, List<LiftExecution>> executionsByLift = db.getExecutionsByLift(liftNames);
      try {
        Set<String> knownLiftNames = new LinkedHashSet<>();
        for (Lift lift : db.listLifts()) {
          knownLiftNames.add(lift.name());
        }
        return new Snapshot(executionsByLift, Set.copyOf(knownLiftNames), true, false);
      } catch (Exception ignored) {
        // Keep history-based suggestions available even if the catalog cannot be inspected.
        return new Snapshot(executionsByLift, Set.of(), false, false);
      }
    } catch (Exception e) {
      return new Snapshot(Map.of(), Set.of(), false, true);
    }
  }

  private static void addLiftNames(
      Set<String> liftNames, PlannedWorkoutFile.PlannedWorkoutDay day) {
    for (PlannedWorkoutFile.PlannedWorkoutBlock block : day.blocks()) {
      for (PlannedWorkoutFile.PlannedExercise exercise : block.exercises()) {
        liftNames.add(exercise.name());
        for (PlannedWorkoutFile.PlannedSetTarget target : exercise.plannedSets()) {
          liftNames.add(target.loadReference(exercise.name()));
        }
      }
    }
  }

  public static final class Snapshot {
    private final Map<String, List<LiftExecution>> executionsByLift;
    private final Set<String> knownLiftNames;
    private final boolean catalogAvailable;
    private final boolean unavailable;

    private Snapshot(
        Map<String, List<LiftExecution>> executionsByLift,
        Set<String> knownLiftNames,
        boolean catalogAvailable,
        boolean unavailable) {
      this.executionsByLift = executionsByLift;
      this.knownLiftNames = knownLiftNames;
      this.catalogAvailable = catalogAvailable;
      this.unavailable = unavailable;
    }

    public Summary lookup(
        PlannedWorkoutFile.PlannedWorkoutBlock block, PlannedWorkoutFile.PlannedExercise exercise) {
      boolean includeDeload =
          exercise.plannedSets().stream().anyMatch(PlannedWorkoutFile.PlannedSetTarget::deload);
      return lookup(exercise.name(), block.warmup(), includeDeload, 3);
    }

    public Summary lookup(String liftName, boolean warmup, boolean includeDeload, int limit) {
      if (unavailable) {
        return new Summary(null, null, true);
      }
      try {
        List<LiftExecution> executions = executionsByLift.getOrDefault(liftName, List.of());
        String last =
            WorkoutHistoryFormatter.lastExecutionSummary(executions, warmup, includeDeload, limit);
        String best = WorkoutHistoryFormatter.bestOneRepMax(executions);
        return new Summary(last, best, false);
      } catch (Exception e) {
        return new Summary(null, null, true);
      }
    }

    public String suggestedWeight(String liftName, PlannedWorkoutFile.PlannedSetTarget target) {
      return resolveWeight(liftName, target).weight();
    }

    public WeightResolution resolveWeight(
        String liftName, PlannedWorkoutFile.PlannedSetTarget target) {
      if (unavailable) {
        return WeightResolution.empty();
      }
      try {
        String referenceLiftName = target.loadReference(liftName);
        boolean explicitReference =
            target.percent() != null && target.percentOf() != null && !target.percentOf().isBlank();
        if (explicitReference && catalogAvailable && !knownLiftNames.contains(referenceLiftName)) {
          return WeightResolution.error(
              "Referenced lift \""
                  + referenceLiftName
                  + "\" does not exist in this account. Add or import that lift, or correct percentOf.");
        }
        String suggested =
            WorkoutHistoryFormatter.suggestedWeight(
                executionsByLift.getOrDefault(referenceLiftName, List.of()), target);
        if (suggested != null) {
          return WeightResolution.weight(suggested);
        }
        if (explicitReference) {
          return WeightResolution.error(
              "Referenced lift \""
                  + referenceLiftName
                  + "\" has no usable max/reference weight. Log a weighted rep set for that lift before using this percentage.");
        }
        return WeightResolution.empty();
      } catch (Exception e) {
        return WeightResolution.empty();
      }
    }

    private static Snapshot empty() {
      return new Snapshot(Map.of(), Set.of(), false, false);
    }
  }

  public record WeightResolution(String weight, String error) {
    public WeightResolution {
      weight = weight == null ? "" : weight;
      error = error == null ? "" : error;
    }

    public static WeightResolution empty() {
      return new WeightResolution("", "");
    }

    public static WeightResolution weight(String weight) {
      return new WeightResolution(weight, "");
    }

    public static WeightResolution error(String error) {
      return new WeightResolution("", error);
    }

    public boolean hasError() {
      return !error.isBlank();
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
