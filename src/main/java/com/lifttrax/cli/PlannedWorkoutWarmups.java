package com.lifttrax.cli;

import com.lifttrax.models.WeightText;
import com.lifttrax.workout.PlannedWorkoutFile;
import com.lifttrax.workout.PlannedWorkoutHistory;
import com.lifttrax.workout.WorkingSetWarmups;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Adapts working-set warm-ups to the non-logging work-along view. */
final class PlannedWorkoutWarmups {
  private PlannedWorkoutWarmups() {}

  static Map<String, WarmupPlan> forDay(
      PlannedWorkoutFile.PlannedWorkoutDay day, PlannedWorkoutHistory.Snapshot history) {
    Map<String, WarmupPlan> plans = new LinkedHashMap<>();
    Set<String> warmedExercises = new HashSet<>();
    for (int blockIndex = 0; blockIndex < day.blocks().size(); blockIndex++) {
      PlannedWorkoutFile.PlannedWorkoutBlock block = day.blocks().get(blockIndex);
      if (block.warmup()) {
        continue;
      }
      for (int exerciseIndex = 0; exerciseIndex < block.exercises().size(); exerciseIndex++) {
        PlannedWorkoutFile.PlannedExercise exercise = block.exercises().get(exerciseIndex);
        if (isCircuitAccessoryOrConditioning(block, exercise)) {
          continue;
        }
        PlannedWorkoutFile.PlannedSetTarget target = firstRepTarget(exercise);
        Integer reps = target == null ? null : targetReps(target);
        if (reps == null || reps < 1 || !warmedExercises.add(normalizeName(exercise.name()))) {
          continue;
        }
        plans.put(
            block.order() + ":" + exerciseIndex,
            plan(day, blockIndex, exerciseIndex, exercise, target, reps, history));
      }
    }
    return Map.copyOf(plans);
  }

  private static WarmupPlan plan(
      PlannedWorkoutFile.PlannedWorkoutDay day,
      int blockIndex,
      int exerciseIndex,
      PlannedWorkoutFile.PlannedExercise exercise,
      PlannedWorkoutFile.PlannedSetTarget target,
      int reps,
      PlannedWorkoutHistory.Snapshot history) {
    String workingWeight = history.suggestedWeight(exercise.name(), target);
    double pounds = WeightText.toPounds(workingWeight);
    List<WarmupSet> sets;
    String loadWarning = "";
    if (Double.isFinite(pounds) && pounds > 0) {
      WorkingSetWarmups.Result generated = WorkingSetWarmups.generate(pounds, reps, 5, 0, false);
      sets =
          generated.sets().stream()
              .map(
                  set ->
                      new WarmupSet(
                          generatedLoadLabel(pounds, set),
                          String.valueOf(set.reps()),
                          formatPounds(set.load())))
              .toList();
      if (!generated.finalGapSatisfied()) {
        loadWarning =
            "The available 5 lb increments cannot meet the final warm-up gap while "
                + "staying at or below 95% of working weight. Use a smaller available increment "
                + "or review the planned work weight before starting.";
      }
    } else {
      workingWeight = "";
      sets =
          WorkingSetWarmups.template(reps).stream()
              .map(
                  stage ->
                      new WarmupSet(
                          percentageLabel(stage.percent()), String.valueOf(stage.reps()), ""))
              .toList();
    }
    String backoffNote =
        reps == 1 && hasLaterBackoff(day, blockIndex, exerciseIndex, exercise.name())
            ? "After the single, rest and go directly to the back-off load; do not warm up again."
            : "";
    return new WarmupPlan(
        "Warm-up ramp",
        target.loadReference(exercise.name()),
        sets,
        "Prepare for the first working set without fatigue. Subsequent work sets of this "
            + "exercise do not need another ramp.",
        backoffNote,
        reps,
        workingWeight,
        loadWarning);
  }

  private static String percentageLabel(int percent) {
    return percent + "% of working weight";
  }

  private static String generatedLoadLabel(double workingWeight, WorkingSetWarmups.WarmupSet set) {
    String label = percentageLabel(set.percent());
    double nearestLoad = Math.round(workingWeight * (set.percent() / 100.0) / 5) * 5;
    return nearestLoad == set.load() ? label : "Final bridge (adjusted from " + label + ")";
  }

  private static String formatPounds(double pounds) {
    return java.math.BigDecimal.valueOf(pounds).stripTrailingZeros().toPlainString() + " lb";
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

  private static boolean isCircuitAccessoryOrConditioning(
      PlannedWorkoutFile.PlannedWorkoutBlock block, PlannedWorkoutFile.PlannedExercise exercise) {
    String exerciseType = exercise.type() == null ? "" : exercise.type();
    String category =
        (block.blockType() + " " + block.title() + " " + exerciseType).toLowerCase(Locale.ROOT);
    return category.contains("accessory")
        || category.contains("conditioning")
        || category.contains("circuit");
  }

  private static String normalizeName(String name) {
    return name.trim().toLowerCase(Locale.ROOT);
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
      PlannedWorkoutFile.PlannedWorkoutBlock block = day.blocks().get(laterBlockIndex);
      if (block.warmup()) {
        continue;
      }
      List<PlannedWorkoutFile.PlannedExercise> exercises = block.exercises();
      int start = laterBlockIndex == blockIndex ? exerciseIndex + 1 : 0;
      for (int laterExerciseIndex = start;
          laterExerciseIndex < exercises.size();
          laterExerciseIndex++) {
        PlannedWorkoutFile.PlannedExercise later = exercises.get(laterExerciseIndex);
        if (normalizeName(later.name()).equals(normalizeName(exerciseName))
            && !isCircuitAccessoryOrConditioning(block, later)
            && later.plannedSets().stream()
                .map(PlannedWorkoutWarmups::targetReps)
                .anyMatch(reps -> reps != null && reps > 1)) {
          return true;
        }
      }
    }
    return false;
  }

  record WarmupPlan(
      String title,
      String reference,
      List<WarmupSet> sets,
      String intent,
      String backoffNote,
      int workingReps,
      String workingWeight,
      String loadWarning) {
    WarmupPlan {
      sets = List.copyOf(sets);
    }
  }

  record WarmupSet(String load, String reps, String suggestedWeight) {}
}
