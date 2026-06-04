package com.lifttrax.workout;

import com.lifttrax.models.SetMetric;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Shared human-readable formatting for planned-workout display formats. */
public final class PlannedWorkoutText {
  private PlannedWorkoutText() {}

  public static String blockMeta(PlannedWorkoutFile.PlannedWorkoutBlock block) {
    List<String> parts = new ArrayList<>();
    parts.add(block.blockType().replace('_', ' '));
    if (block.rounds() != null) {
      parts.add(block.rounds() + " rounds");
    }
    if (block.warmup()) {
      parts.add("warm-up");
    }
    return join(parts);
  }

  public static String exerciseDetails(PlannedWorkoutFile.PlannedExercise exercise) {
    List<String> details = new ArrayList<>();
    if (exercise.region() != null && !exercise.region().isBlank()) {
      details.add(exercise.region());
    }
    if (exercise.type() != null && !exercise.type().isBlank()) {
      details.add(exercise.type());
    }
    if (!exercise.muscles().isEmpty()) {
      details.add(join(exercise.muscles()));
    }
    return join(details);
  }

  public static String plannedSets(List<PlannedWorkoutFile.PlannedSetTarget> sets) {
    if (sets.isEmpty()) {
      return "No planned set targets.";
    }
    List<String> groups = new ArrayList<>();
    int index = 0;
    while (index < sets.size()) {
      PlannedWorkoutFile.PlannedSetTarget target = sets.get(index);
      int count = 1;
      while (index + count < sets.size() && samePlannedTarget(target, sets.get(index + count))) {
        count++;
      }
      String formatted = plannedSet(target);
      groups.add(count > 1 ? count + "x " + formatted : formatted);
      index += count;
    }
    return join(groups);
  }

  public static String plannedSet(PlannedWorkoutFile.PlannedSetTarget target) {
    List<String> parts = new ArrayList<>();
    parts.add(plannedMetric(target));
    if (target.percent() != null) {
      parts.add("@ " + target.percent() + "%");
    }
    if (target.rpe() != null) {
      parts.add("RPE " + String.format(Locale.ROOT, "%.1f", target.rpe()));
    }
    if (target.accommodatingResistance() != null
        && !target.accommodatingResistance().isBlank()
        && !"STRAIGHT".equals(target.accommodatingResistance())) {
      parts.add(target.accommodatingResistance());
    }
    if (target.deload()) {
      parts.add("deload");
    }
    return String.join(" ", parts);
  }

  public static SetMetric historyMetric(
      PlannedWorkoutFile.PlannedWorkoutBlock block, PlannedWorkoutFile.PlannedExercise exercise) {
    if (block.rounds() != null || exercise.plannedSets().isEmpty()) {
      return null;
    }
    PlannedWorkoutFile.PlannedSetTarget target = exercise.plannedSets().get(0);
    return switch (target.metricType()) {
      case "reps" -> target.reps() == null ? null : new SetMetric.Reps(target.reps());
      case "reps_lr" ->
          target.repsLeft() == null || target.repsRight() == null
              ? null
              : new SetMetric.RepsLr(target.repsLeft(), target.repsRight());
      case "time_seconds" ->
          target.seconds() == null ? null : new SetMetric.TimeSecs(target.seconds());
      case "distance_feet" ->
          target.distanceFeet() == null ? null : new SetMetric.DistanceFeet(target.distanceFeet());
      default -> null;
    };
  }

  private static boolean samePlannedTarget(
      PlannedWorkoutFile.PlannedSetTarget first, PlannedWorkoutFile.PlannedSetTarget second) {
    return Objects.equals(first.metricType(), second.metricType())
        && Objects.equals(first.reps(), second.reps())
        && Objects.equals(first.repsLeft(), second.repsLeft())
        && Objects.equals(first.repsRight(), second.repsRight())
        && Objects.equals(first.repsMin(), second.repsMin())
        && Objects.equals(first.repsMax(), second.repsMax())
        && Objects.equals(first.seconds(), second.seconds())
        && Objects.equals(first.distanceFeet(), second.distanceFeet())
        && Objects.equals(first.percent(), second.percent())
        && Objects.equals(first.rpe(), second.rpe())
        && Objects.equals(first.accommodatingResistance(), second.accommodatingResistance())
        && first.deload() == second.deload();
  }

  private static String plannedMetric(PlannedWorkoutFile.PlannedSetTarget target) {
    return switch (target.metricType()) {
      case "reps" -> target.reps() == null ? repRange(target) : target.reps() + " reps";
      case "reps_lr" -> target.repsLeft() + "L/" + target.repsRight() + "R reps";
      case "reps_range" -> repRange(target);
      case "time_seconds" -> target.seconds() + " sec";
      case "distance_feet" -> target.distanceFeet() + " ft";
      default -> "planned work";
    };
  }

  private static String repRange(PlannedWorkoutFile.PlannedSetTarget target) {
    if (target.repsMin() != null && target.repsMax() != null) {
      return target.repsMin() + "-" + target.repsMax() + " reps";
    }
    if (target.repsMin() != null) {
      return target.repsMin() + "+ reps";
    }
    if (target.repsMax() != null) {
      return "up to " + target.repsMax() + " reps";
    }
    return "reps";
  }

  private static String join(List<String> values) {
    return String.join(", ", values);
  }
}
