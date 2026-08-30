package com.lifttrax.cli;

import com.lifttrax.workout.PlannedWorkoutFile;
import com.lifttrax.workout.PlannedWorkoutHistory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Builds non-logging warm-up ramps for the work-along view. */
final class PlannedWorkoutWarmups {
  private static final String OVERHEAD_PRESS = "Overhead Press";

  private PlannedWorkoutWarmups() {}

  static Map<String, WarmupPlan> forDay(
      PlannedWorkoutFile.PlannedWorkoutDay day, PlannedWorkoutHistory.Snapshot history) {
    Map<String, WarmupPlan> plans = new LinkedHashMap<>();
    for (int blockIndex = 0; blockIndex < day.blocks().size(); blockIndex++) {
      PlannedWorkoutFile.PlannedWorkoutBlock block = day.blocks().get(blockIndex);
      if (block.warmup()) {
        continue;
      }
      for (int exerciseIndex = 0; exerciseIndex < block.exercises().size(); exerciseIndex++) {
        PlannedWorkoutFile.PlannedExercise exercise = block.exercises().get(exerciseIndex);
        if (isCircuitAccessoryOrConditioning(block, exercise)
            || isBackoffAfterSingle(day, blockIndex, exerciseIndex, exercise.name())) {
          continue;
        }
        String key = block.order() + ":" + exerciseIndex;
        PlannedWorkoutFile.PlannedSetTarget target = firstRepTarget(exercise);
        if (isContinentalCleanAndPress(exercise.name())) {
          plans.put(key, continentalPlan(target, history));
          continue;
        }
        Integer reps = target == null ? null : targetReps(target);
        if (reps == null || reps < 1) {
          continue;
        }
        plans.put(
            key, standardPlan(day, blockIndex, exerciseIndex, exercise, target, reps, history));
      }
    }
    return Map.copyOf(plans);
  }

  private static WarmupPlan standardPlan(
      PlannedWorkoutFile.PlannedWorkoutDay day,
      int blockIndex,
      int exerciseIndex,
      PlannedWorkoutFile.PlannedExercise exercise,
      PlannedWorkoutFile.PlannedSetTarget target,
      int reps,
      PlannedWorkoutHistory.Snapshot history) {
    String reference = target.loadReference(exercise.name());
    List<WarmupSet> sets = new ArrayList<>();
    String intent;
    if (reps == 1) {
      sets.add(new WarmupSet(startingLoad(exercise), "8-10", ""));
      addWorkingPercentageSets(
          sets,
          history,
          exercise.name(),
          target,
          new int[] {40, 5},
          new int[] {55, 3},
          new int[] {70, 2},
          new int[] {80, 1});
      intent = "Arrive ready for the single without fatigue or psyching up.";
    } else if (reps <= 3) {
      sets.add(new WarmupSet(startingLoad(exercise), "8", ""));
      addWorkingPercentageSets(
          sets,
          history,
          exercise.name(),
          target,
          new int[] {40, 5},
          new int[] {55, 3},
          new int[] {70, 2});
      if (history.targetAtLeast(target, 80)) {
        addWorkingPercentageSets(sets, history, exercise.name(), target, new int[] {75, 1});
      }
      intent = "Increase load without accumulating extra repetitions.";
    } else if (reps <= 6) {
      sets.add(new WarmupSet(startingLoad(exercise), "8", ""));
      addWorkingPercentageSets(
          sets,
          history,
          exercise.name(),
          target,
          new int[] {40, 5},
          new int[] {55, 3},
          new int[] {65, 2});
      if (history.targetAtLeast(target, 75)) {
        addWorkingPercentageSets(sets, history, exercise.name(), target, new int[] {70, 1});
      }
      intent = "Preserve energy for the work sets; avoid a pump or breathing fatigue.";
    } else {
      sets.add(new WarmupSet("Very light load", "8-10", ""));
      sets.add(
          new WarmupSet(
              "About 50% of working weight",
              "5", history.suggestedWeightFractionOfTarget(exercise.name(), target, 0.50)));
      sets.add(
          new WarmupSet(
              "About 70% of working weight",
              "3", history.suggestedWeightFractionOfTarget(exercise.name(), target, 0.70)));
      intent = "Confirm the groove and load without pre-fatiguing the target muscle.";
    }
    String backoffNote =
        reps == 1 && hasLaterBackoff(day, blockIndex, exerciseIndex, exercise.name())
            ? backoffNote(history, exercise.name(), target)
            : "";
    return new WarmupPlan("Warm-up ramp", reference, sets, intent, backoffNote);
  }

  private static WarmupPlan continentalPlan(
      PlannedWorkoutFile.PlannedSetTarget target, PlannedWorkoutHistory.Snapshot history) {
    List<WarmupSet> sets = new ArrayList<>();
    sets.add(new WarmupSet("Empty/light axle", "3", ""));
    addWorkingPercentageSets(sets, history, OVERHEAD_PRESS, target, new int[] {40, 2});
    sets.add(
        new WarmupSet(
            "50% of working weight",
            "1-2", suggestedWorkingPercentage(history, OVERHEAD_PRESS, target, 50)));
    return new WarmupPlan(
        "Continental Clean and Press warm-up (if needed)",
        OVERHEAD_PRESS,
        sets,
        "Make every warm-up clean deliberate; do not test the clean during the ramp.",
        "");
  }

  private static void addWorkingPercentageSets(
      List<WarmupSet> sets,
      PlannedWorkoutHistory.Snapshot history,
      String liftName,
      PlannedWorkoutFile.PlannedSetTarget target,
      int[]... percentagesAndReps) {
    for (int[] entry : percentagesAndReps) {
      int percent = entry[0];
      sets.add(
          new WarmupSet(
              percent + "% of working weight",
              String.valueOf(entry[1]),
              suggestedWorkingPercentage(history, liftName, target, percent)));
    }
  }

  private static String suggestedWorkingPercentage(
      PlannedWorkoutHistory.Snapshot history,
      String liftName,
      PlannedWorkoutFile.PlannedSetTarget target,
      int percent) {
    return target == null
        ? ""
        : history.suggestedWeightFractionOfTarget(liftName, target, percent / 100.0);
  }

  private static PlannedWorkoutFile.PlannedSetTarget firstRepTarget(
      PlannedWorkoutFile.PlannedExercise exercise) {
    for (PlannedWorkoutFile.PlannedSetTarget target : exercise.plannedSets()) {
      if (targetReps(target) != null) {
        return target;
      }
    }
    return null;
  }

  private static Integer targetReps(PlannedWorkoutFile.PlannedSetTarget target) {
    return switch (target.metricType()) {
      case "reps" -> target.reps();
      case "reps_range" -> target.repsMax() == null ? target.repsMin() : target.repsMax();
      case "reps_lr" ->
          target.repsLeft() == null || target.repsRight() == null
              ? null
              : Math.max(target.repsLeft(), target.repsRight());
      default -> null;
    };
  }

  private static String startingLoad(PlannedWorkoutFile.PlannedExercise exercise) {
    String type = exercise.type() == null ? "" : exercise.type().toUpperCase(Locale.ROOT);
    return switch (type) {
      case "BENCH_PRESS" -> "Empty bar";
      case "SQUAT", "DEADLIFT" -> "Lightest practical load";
      default -> "Empty bar / lightest practical load";
    };
  }

  private static boolean isCircuitAccessoryOrConditioning(
      PlannedWorkoutFile.PlannedWorkoutBlock block, PlannedWorkoutFile.PlannedExercise exercise) {
    String exerciseType = exercise.type() == null ? "" : exercise.type();
    String category =
        (block.blockType() + " " + block.title() + " " + exerciseType).toLowerCase(Locale.ROOT);
    return category.contains("accessory")
        || category.contains("conditioning")
        || category.contains("circuit");
  }

  private static boolean isBackoffAfterSingle(
      PlannedWorkoutFile.PlannedWorkoutDay day,
      int blockIndex,
      int exerciseIndex,
      String exerciseName) {
    for (int earlierBlockIndex = 0; earlierBlockIndex <= blockIndex; earlierBlockIndex++) {
      List<PlannedWorkoutFile.PlannedExercise> exercises =
          day.blocks().get(earlierBlockIndex).exercises();
      int end = earlierBlockIndex == blockIndex ? exerciseIndex : exercises.size();
      for (int earlierExerciseIndex = 0; earlierExerciseIndex < end; earlierExerciseIndex++) {
        PlannedWorkoutFile.PlannedExercise earlier = exercises.get(earlierExerciseIndex);
        if (earlier.name().equalsIgnoreCase(exerciseName)
            && earlier.plannedSets().stream()
                .map(PlannedWorkoutWarmups::targetReps)
                .anyMatch(reps -> reps != null && reps == 1)) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean isContinentalCleanAndPress(String exerciseName) {
    String normalized =
        exerciseName.toLowerCase(Locale.ROOT).replace("&", "and").replaceAll("[^a-z]+", " ").trim();
    return "continental clean and press".equals(normalized);
  }

  private static boolean hasLaterBackoff(
      PlannedWorkoutFile.PlannedWorkoutDay day,
      int blockIndex,
      int exerciseIndex,
      String exerciseName) {
    PlannedWorkoutFile.PlannedExercise current =
        day.blocks().get(blockIndex).exercises().get(exerciseIndex);
    if (current.plannedSets().stream()
        .skip(1)
        .map(PlannedWorkoutWarmups::targetReps)
        .anyMatch(reps -> reps != null && reps > 1)) {
      return true;
    }
    for (int laterBlockIndex = blockIndex;
        laterBlockIndex < day.blocks().size();
        laterBlockIndex++) {
      List<PlannedWorkoutFile.PlannedExercise> exercises =
          day.blocks().get(laterBlockIndex).exercises();
      int start = laterBlockIndex == blockIndex ? exerciseIndex + 1 : 0;
      for (int laterExerciseIndex = start;
          laterExerciseIndex < exercises.size();
          laterExerciseIndex++) {
        PlannedWorkoutFile.PlannedExercise later = exercises.get(laterExerciseIndex);
        if (later.name().equalsIgnoreCase(exerciseName)
            && later.plannedSets().stream()
                .map(PlannedWorkoutWarmups::targetReps)
                .anyMatch(reps -> reps != null && reps > 1)) {
          return true;
        }
      }
    }
    return false;
  }

  private static String backoffNote(
      PlannedWorkoutHistory.Snapshot history,
      String liftName,
      PlannedWorkoutFile.PlannedSetTarget target) {
    String low = suggestedWorkingPercentage(history, liftName, target, 60);
    String high = suggestedWorkingPercentage(history, liftName, target, 65);
    String interruptionWeight =
        low.isBlank() || high.isBlank() ? "" : " (about " + low + " to " + high + ")";
    return "After the single, rest and go directly to the back-off load; do not warm up again. "
        + "If setup is interrupted for more than 10 minutes, take one easy single or double at "
        + "60-65% of the working weight"
        + interruptionWeight
        + ".";
  }

  record WarmupPlan(
      String title, String reference, List<WarmupSet> sets, String intent, String backoffNote) {
    WarmupPlan {
      sets = List.copyOf(sets);
    }
  }

  record WarmupSet(String load, String reps, String suggestedWeight) {}
}
