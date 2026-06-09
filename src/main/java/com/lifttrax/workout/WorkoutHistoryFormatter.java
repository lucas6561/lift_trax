package com.lifttrax.workout;

import com.lifttrax.db.Database;
import com.lifttrax.models.ExecutionSet;
import com.lifttrax.models.ExecutionSummaryFormatter;
import com.lifttrax.models.LiftExecution;
import com.lifttrax.models.SetMetric;
import com.lifttrax.models.WeightText;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Shared history summaries for planned workouts and generated waves. */
public final class WorkoutHistoryFormatter {
  private static final double[][] RPE_PERCENT_TABLE = {
    {6.0, 86.3, 83.7, 81.1, 78.6, 76.2, 73.9, 70.7, 68.0, 65.3, 62.6, 59.9, 57.4},
    {6.5, 87.8, 85.0, 82.4, 79.9, 77.4, 75.1, 72.3, 69.4, 66.7, 64.0, 61.3, 58.6},
    {7.0, 89.2, 86.3, 83.7, 81.1, 78.6, 76.2, 73.9, 70.7, 68.0, 65.3, 62.6, 59.9},
    {7.5, 90.7, 87.8, 85.0, 82.4, 79.9, 77.4, 75.1, 72.3, 69.4, 66.7, 64.0, 61.3},
    {8.0, 92.2, 89.2, 86.3, 83.7, 81.1, 78.6, 76.2, 73.9, 70.7, 68.0, 65.3, 62.6},
    {8.5, 93.9, 90.7, 87.8, 85.0, 82.4, 79.9, 77.4, 75.1, 72.3, 69.4, 66.7, 64.0},
    {9.0, 95.5, 92.2, 89.2, 86.3, 83.7, 81.1, 78.6, 76.2, 73.9, 70.7, 68.0, 65.3},
    {9.5, 97.8, 93.9, 90.7, 87.8, 85.0, 82.4, 79.9, 77.4, 75.1, 72.3, 69.4, 66.7},
    {10.0, 100.0, 95.5, 92.2, 89.2, 86.3, 83.7, 81.1, 78.6, 76.2, 73.9, 70.7, 68.0}
  };

  private WorkoutHistoryFormatter() {}

  public static String lastExecutionSummary(
      Database db, String liftName, boolean warmup, SetMetric metric, boolean includeDeload)
      throws Exception {
    List<LiftExecution> executions =
        db.getExecutions(liftName).stream()
            .filter(e -> e.warmup() == warmup && (includeDeload || !e.deload()))
            .sorted(
                Comparator.comparing(LiftExecution::date)
                    .thenComparing(e -> e.id() == null ? Integer.MIN_VALUE : e.id())
                    .reversed())
            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));

    if (executions.isEmpty()) {
      return null;
    }

    if (metric != null) {
      prioritizeMetric(executions, metric);
    }

    List<String> summaries = new ArrayList<>();
    for (int i = 0; i < Math.min(3, executions.size()); i++) {
      summaries.add(ExecutionSummaryFormatter.formatCompactSummary(executions.get(i)));
    }
    return String.join(" | ", summaries);
  }

  public static String bestOneRepMax(Database db, String liftName) throws Exception {
    String bestWeight = null;
    double bestWeightLbs = 0.0;
    for (LiftExecution exec : db.getExecutions(liftName)) {
      if (exec.warmup()) {
        continue;
      }
      for (ExecutionSet set : exec.sets()) {
        if (set.metric() instanceof SetMetric.Reps reps
            && reps.reps() == 1
            && set.weight() != null
            && !"none".equalsIgnoreCase(set.weight())) {
          double weightLbs = WeightText.toPounds(set.weight());
          if (bestWeight == null || weightLbs > bestWeightLbs) {
            bestWeight = set.weight();
            bestWeightLbs = weightLbs;
          }
        }
      }
    }
    return bestWeight;
  }

  public static String percentageOfBestOneRepMax(Database db, String liftName, double percent)
      throws Exception {
    BestOneRepMax best = bestOneRepMaxValue(db, liftName);
    if (best == null) {
      return null;
    }
    return formatPercentage(best.weightLbs(), percent);
  }

  public static String suggestedWeight(
      Database db, String liftName, PlannedWorkoutFile.PlannedSetTarget target) throws Exception {
    if (db == null || target == null) {
      return null;
    }
    Double targetPercent = targetPercent(target);
    if (targetPercent == null) {
      return null;
    }
    BestOneRepMax base = trainingMaxValue(db, liftName);
    if (base == null) {
      return null;
    }
    return formatPercentage(base.weightLbs(), targetPercent);
  }

  private static BestOneRepMax bestOneRepMaxValue(Database db, String liftName) throws Exception {
    String bestWeight = null;
    double bestWeightLbs = 0.0;
    for (LiftExecution exec : db.getExecutions(liftName)) {
      if (exec.warmup()) {
        continue;
      }
      for (ExecutionSet set : exec.sets()) {
        if (set.metric() instanceof SetMetric.Reps reps
            && reps.reps() == 1
            && set.weight() != null
            && !"none".equalsIgnoreCase(set.weight())) {
          double weightLbs = WeightText.toPounds(set.weight());
          if (weightLbs > 0.0 && (bestWeight == null || weightLbs > bestWeightLbs)) {
            bestWeight = set.weight();
            bestWeightLbs = weightLbs;
          }
        }
      }
    }
    return bestWeight == null ? null : new BestOneRepMax(bestWeightLbs);
  }

  private static BestOneRepMax trainingMaxValue(Database db, String liftName) throws Exception {
    BestOneRepMax trueOneRepMax = bestOneRepMaxValue(db, liftName);
    if (trueOneRepMax != null) {
      return trueOneRepMax;
    }

    HistoricalSet fallback = null;
    for (LiftExecution exec : db.getExecutions(liftName)) {
      if (exec.warmup()) {
        continue;
      }
      for (ExecutionSet set : exec.sets()) {
        if (!(set.metric() instanceof SetMetric.Reps reps)
            || set.weight() == null
            || "none".equalsIgnoreCase(set.weight())) {
          continue;
        }
        double weightLbs = WeightText.toPounds(set.weight());
        if (weightLbs <= 0.0) {
          continue;
        }
        HistoricalSet candidate = new HistoricalSet(weightLbs, reps.reps(), set.rpe());
        if (fallback == null
            || candidate.weightLbs() > fallback.weightLbs()
            || (candidate.weightLbs() == fallback.weightLbs()
                && candidate.reps() < fallback.reps())) {
          fallback = candidate;
        }
      }
    }
    if (fallback == null) {
      return null;
    }
    double base = fallback.weightLbs();
    if (fallback.rpe() != null) {
      Double historicalPercent = rpePercent(fallback.reps(), fallback.rpe());
      if (historicalPercent != null && historicalPercent > 0.0) {
        base = fallback.weightLbs() / (historicalPercent / 100.0);
      }
    }
    return new BestOneRepMax(base);
  }

  private static Double targetPercent(PlannedWorkoutFile.PlannedSetTarget target) {
    if (target.percent() != null) {
      return target.percent().doubleValue();
    }
    if (target.rpe() == null) {
      return null;
    }
    Integer reps = targetReps(target);
    return reps == null ? null : rpePercent(reps, target.rpe());
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

  private static Double rpePercent(int reps, float rpe) {
    if (reps < 1 || rpe < RPE_PERCENT_TABLE[0][0]) {
      return null;
    }
    int repIndex = Math.min(reps, RPE_PERCENT_TABLE[0].length - 1);
    if (rpe >= RPE_PERCENT_TABLE[RPE_PERCENT_TABLE.length - 1][0]) {
      return RPE_PERCENT_TABLE[RPE_PERCENT_TABLE.length - 1][repIndex];
    }
    for (int i = 0; i < RPE_PERCENT_TABLE.length - 1; i++) {
      double lowerRpe = RPE_PERCENT_TABLE[i][0];
      double upperRpe = RPE_PERCENT_TABLE[i + 1][0];
      if (rpe >= lowerRpe && rpe <= upperRpe) {
        double lowerPercent = RPE_PERCENT_TABLE[i][repIndex];
        double upperPercent = RPE_PERCENT_TABLE[i + 1][repIndex];
        double ratio = (rpe - lowerRpe) / (upperRpe - lowerRpe);
        return lowerPercent + ((upperPercent - lowerPercent) * ratio);
      }
    }
    return null;
  }

  private static String formatPercentage(double weightLbs, double percent) {
    double multiplier = percent / 100.0;
    return roundUpToFivePounds(weightLbs * multiplier) + " lb";
  }

  private static long roundUpToFivePounds(double value) {
    return (long) Math.ceil(value / 5.0) * 5L;
  }

  private static void prioritizeMetric(List<LiftExecution> executions, SetMetric target) {
    for (int i = 0; i < executions.size(); i++) {
      ExecutionSet first =
          executions.get(i).sets().isEmpty() ? null : executions.get(i).sets().get(0);
      if (first != null && first.metric().equals(target)) {
        LiftExecution match = executions.remove(i);
        executions.add(0, match);
        return;
      }
    }

    int bestIdx = -1;
    int bestDiff = Integer.MAX_VALUE;
    for (int i = 0; i < executions.size(); i++) {
      ExecutionSet first =
          executions.get(i).sets().isEmpty() ? null : executions.get(i).sets().get(0);
      if (first == null) {
        continue;
      }
      Integer diff = metricDistance(first.metric(), target);
      if (diff != null && diff < bestDiff) {
        bestDiff = diff;
        bestIdx = i;
      }
    }
    if (bestIdx >= 0) {
      LiftExecution match = executions.remove(bestIdx);
      executions.add(0, match);
    }
  }

  private static Integer metricDistance(SetMetric candidate, SetMetric target) {
    if (candidate instanceof SetMetric.Reps a && target instanceof SetMetric.Reps b) {
      return Math.abs(a.reps() - b.reps());
    }
    if (candidate instanceof SetMetric.RepsLr a && target instanceof SetMetric.RepsLr b) {
      return Math.abs(a.left() - b.left()) + Math.abs(a.right() - b.right());
    }
    if (candidate instanceof SetMetric.RepsLr a && target instanceof SetMetric.Reps b) {
      return Math.abs(((a.left() + a.right()) / 2) - b.reps());
    }
    if (candidate instanceof SetMetric.Reps a && target instanceof SetMetric.RepsLr b) {
      return Math.abs(a.reps() - ((b.left() + b.right()) / 2));
    }
    if (candidate instanceof SetMetric.TimeSecs a && target instanceof SetMetric.TimeSecs b) {
      return Math.abs(a.seconds() - b.seconds());
    }
    if (candidate instanceof SetMetric.DistanceFeet a
        && target instanceof SetMetric.DistanceFeet b) {
      return Math.abs(a.feet() - b.feet());
    }
    return null;
  }

  private record BestOneRepMax(double weightLbs) {}

  private record HistoricalSet(double weightLbs, int reps, Float rpe) {}
}
