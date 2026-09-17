package com.lifttrax.cli;

import com.lifttrax.models.WeightText;
import com.lifttrax.workout.PlannedWorkoutFile;
import com.lifttrax.workout.PlannedWorkoutHistory;
import com.lifttrax.workout.WarmupLoading;
import com.lifttrax.workout.WorkingSetWarmups;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Adapts working-set warm-ups to the non-logging work-along view. */
final class PlannedWorkoutWarmups {
  private PlannedWorkoutWarmups() {}

  static Map<String, WarmupPlan> forDay(
      PlannedWorkoutFile.PlannedWorkoutDay day, PlannedWorkoutHistory.Snapshot history) {
    Map<String, WarmupPlan> plans = new LinkedHashMap<>();
    Set<String> warmedExercises = new HashSet<>();
    Optional<PlannedWorkoutFile.PlannedExercise> previous = Optional.empty();
    for (int blockIndex = 0; blockIndex < day.blocks().size(); blockIndex++) {
      PlannedWorkoutFile.PlannedWorkoutBlock block = day.blocks().get(blockIndex);
      if (block.warmup()) {
        previous = Optional.empty();
        continue;
      }
      for (int exerciseIndex = 0; exerciseIndex < block.exercises().size(); exerciseIndex++) {
        PlannedWorkoutFile.PlannedExercise exercise = block.exercises().get(exerciseIndex);
        if (isCircuitAccessoryOrConditioning(block, exercise)) {
          previous = Optional.empty();
          continue;
        }
        PlannedWorkoutFile.PlannedSetTarget target = firstRepTarget(exercise);
        Integer reps = target == null ? null : targetReps(target);
        if (reps == null || reps < 1 || !warmedExercises.add(normalizeName(exercise.name()))) {
          previous = Optional.of(exercise);
          continue;
        }
        plans.put(
            block.order() + ":" + exerciseIndex,
            plan(
                day,
                blockIndex,
                exerciseIndex,
                exercise,
                target,
                reps,
                history,
                relatedLoadedExercise(previous, exercise, target, history)));
        previous = Optional.of(exercise);
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
      PlannedWorkoutHistory.Snapshot history,
      boolean alreadyWarm) {
    String workingWeight = history.suggestedWeight(exercise.name(), target);
    double pounds = WeightText.toPounds(workingWeight);
    boolean shortened = alreadyWarm && Double.isFinite(pounds) && pounds > 0;
    List<WarmupSet> sets;
    String loadWarning = "";
    WarmupLoading loading = loadingFor(exercise.name());
    if (Double.isFinite(pounds) && pounds > 0) {
      WorkingSetWarmups.Result generated =
          WorkingSetWarmups.generate(pounds, reps, loading, shortened);
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
            "The assumed equipment cannot meet the final warm-up gap while "
                + "staying below working weight. Use a smaller available increment or a lighter implement "
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
            + "exercise do not need another ramp."
            + (shortened ? " The ramp is shortened after a related loaded exercise." : ""),
        backoffNote,
        reps,
        workingWeight,
        loadWarning,
        loading.system() == WarmupLoading.LoadingSystem.BARBELL
            ? "Assumes a 45 lb bar with 45, 25, 10, 5 and 2.5 lb plates. Early loads favor simple plates; later loads prioritize the bridge target. Adjust for your equipment."
            : "Assumes available loads in 5 lb increments. Adjust for your equipment.");
  }

  private static String percentageLabel(double percent) {
    return java.math.BigDecimal.valueOf(percent).stripTrailingZeros().toPlainString()
        + "% of working weight";
  }

  private static String generatedLoadLabel(double workingWeight, WorkingSetWarmups.WarmupSet set) {
    String label = percentageLabel(set.percent());
    double targetLoad = workingWeight * (set.percent() / 100.0);
    return Math.abs(targetLoad - set.load()) < 1e-9
        ? label
        : "Target " + label + " (practical load)";
  }

  private static WarmupLoading loadingFor(String name) {
    String normalized = normalizeName(name);
    boolean otherEquipment =
        normalized.matches(
            ".*\\b(dumbbell|dumbbells|db|kettlebell|kb|machine|cable|smith|trap|axle|log|continental|landmine)\\b.*");
    boolean standardBarbell =
        normalized.contains("barbell")
            || Set.of(
                    "bench press",
                    "back squat",
                    "front squat",
                    "overhead press",
                    "deadlift",
                    "conventional deadlift",
                    "sumo deadlift",
                    "romanian deadlift",
                    "paused deadlift")
                .contains(normalized);
    return !otherEquipment && standardBarbell
        ? WarmupLoading.barbell()
        : WarmupLoading.fixedIncrement(5, 0);
  }

  private static boolean relatedLoadedExercise(
      Optional<PlannedWorkoutFile.PlannedExercise> previousExercise,
      PlannedWorkoutFile.PlannedExercise exercise,
      PlannedWorkoutFile.PlannedSetTarget target,
      PlannedWorkoutHistory.Snapshot history) {
    if (previousExercise.isEmpty()) {
      return false;
    }
    PlannedWorkoutFile.PlannedExercise previous = previousExercise.get();
    PlannedWorkoutFile.PlannedSetTarget previousTarget = firstRepTarget(previous);
    if (previousTarget == null) {
      return false;
    }
    double previousWeight =
        WeightText.toPounds(history.suggestedWeight(previous.name(), previousTarget));
    if (!Double.isFinite(previousWeight) || previousWeight <= 0) {
      return false;
    }
    String reference = normalizeName(target.loadReference(exercise.name()));
    return reference.equals(normalizeName(previous.name()))
        || reference.equals(normalizeName(previousTarget.loadReference(previous.name())));
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
      String loadWarning,
      String loadingNote) {
    WarmupPlan {
      sets = List.copyOf(sets);
    }
  }

  record WarmupSet(String load, String reps, String suggestedWeight) {}
}
