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
}
