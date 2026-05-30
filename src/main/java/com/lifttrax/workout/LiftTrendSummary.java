package com.lifttrax.workout;

import com.lifttrax.models.ExecutionSet;
import com.lifttrax.models.LiftExecution;
import com.lifttrax.models.SetMetric;
import com.lifttrax.models.WeightText;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Summarizes recent lift history for lift detail pages. */
public record LiftTrendSummary(
    LocalDate windowStart,
    LocalDate windowEnd,
    int totalExecutions,
    int recentExecutions,
    int recentTrainingDays,
    int recentWorkSets,
    int recentRepVolume,
    int recentTonnageLbs,
    LiftExecution lastExecution,
    BestSet bestRecentSet) {
  public static final int RECENT_WINDOW_DAYS = 90;

  public static LiftTrendSummary from(List<LiftExecution> executions, LocalDate today) {
    LocalDate windowEnd = today == null ? LocalDate.now() : today;
    LocalDate windowStart = windowEnd.minusDays(RECENT_WINDOW_DAYS - 1L);
    List<LiftExecution> safeExecutions = executions == null ? List.of() : executions;
    LiftExecution lastExecution =
        safeExecutions.stream()
            .max(
                Comparator.comparing(LiftExecution::date)
                    .thenComparing(execution -> execution.id() == null ? 0 : execution.id()))
            .orElse(null);
    Accumulator accumulator = new Accumulator();
    for (LiftExecution execution : safeExecutions) {
      if (isInWindow(execution, windowStart, windowEnd)) {
        accumulator.add(execution);
      }
    }
    return new LiftTrendSummary(
        windowStart,
        windowEnd,
        safeExecutions.size(),
        accumulator.recentExecutions(),
        accumulator.recentTrainingDays(),
        accumulator.recentWorkSets(),
        accumulator.recentRepVolume(),
        accumulator.recentTonnageLbs(),
        lastExecution,
        accumulator.bestRecentSet());
  }

  public boolean hasAnyExecutions() {
    return totalExecutions > 0;
  }

  public boolean hasRecentExecutions() {
    return recentExecutions > 0;
  }

  public boolean hasSparseRecentHistory() {
    return recentExecutions > 0 && recentExecutions < 3;
  }

  private static boolean isInWindow(
      LiftExecution execution, LocalDate windowStart, LocalDate windowEnd) {
    return execution.date() != null
        && !execution.date().isBefore(windowStart)
        && !execution.date().isAfter(windowEnd);
  }

  public record BestSet(LocalDate date, SetMetric metric, String weight, int pounds, Float rpe) {}

  private static final class Accumulator {
    private final Set<LocalDate> trainingDays = new HashSet<>();
    private int executionCount;
    private int workSetCount;
    private int repVolumeCount;
    private int tonnagePounds;
    private BestSet strongestSet;

    void add(LiftExecution execution) {
      executionCount++;
      trainingDays.add(execution.date());
      if (execution.warmup() || execution.deload()) {
        return;
      }
      for (ExecutionSet set : execution.sets()) {
        addSet(execution.date(), set);
      }
    }

    private void addSet(LocalDate date, ExecutionSet set) {
      workSetCount++;
      int reps = repCount(set.metric());
      repVolumeCount += reps;
      int pounds = (int) Math.round(WeightText.toPounds(set.weight()));
      if (reps > 0 && pounds > 0) {
        tonnagePounds += pounds * reps;
        BestSet candidate = new BestSet(date, set.metric(), set.weight(), pounds, set.rpe());
        if (isBetter(candidate, strongestSet)) {
          strongestSet = candidate;
        }
      }
    }

    int recentExecutions() {
      return executionCount;
    }

    int recentTrainingDays() {
      return trainingDays.size();
    }

    int recentWorkSets() {
      return workSetCount;
    }

    int recentRepVolume() {
      return repVolumeCount;
    }

    int recentTonnageLbs() {
      return tonnagePounds;
    }

    BestSet bestRecentSet() {
      return strongestSet;
    }
  }

  private static int repCount(SetMetric metric) {
    if (metric instanceof SetMetric.Reps reps) {
      return reps.reps();
    }
    if (metric instanceof SetMetric.RepsLr repsLr) {
      return repsLr.left() + repsLr.right();
    }
    if (metric instanceof SetMetric.RepsRange range) {
      return range.max();
    }
    return 0;
  }

  private static boolean isBetter(BestSet candidate, BestSet current) {
    if (current == null) {
      return true;
    }
    if (candidate.pounds() != current.pounds()) {
      return candidate.pounds() > current.pounds();
    }
    int candidateReps = repCount(candidate.metric());
    int currentReps = repCount(current.metric());
    if (candidateReps != currentReps) {
      return candidateReps > currentReps;
    }
    return candidate.date().isAfter(current.date());
  }
}
