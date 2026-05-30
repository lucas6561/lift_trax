package com.lifttrax.workout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lifttrax.models.ExecutionSet;
import com.lifttrax.models.LiftExecution;
import com.lifttrax.models.SetMetric;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class LiftTrendSummaryTest {

  @Test
  void summarizesRecentFrequencyVolumeAndBestWeightedSet() {
    LocalDate today = LocalDate.of(2026, 5, 30);
    LiftExecution oldExecution = execution(1, LocalDate.of(2026, 1, 15), "225 lb", 5, false);
    LiftExecution warmup = execution(2, LocalDate.of(2026, 5, 15), "405 lb", 1, true);
    LiftExecution recentVolume =
        new LiftExecution(
            3,
            LocalDate.of(2026, 5, 20),
            List.of(
                new ExecutionSet(new SetMetric.Reps(5), "225 lb", 8.0f),
                new ExecutionSet(new SetMetric.Reps(3), "275 lb", 8.5f)),
            false,
            false,
            "");
    LiftExecution recentBest = execution(4, LocalDate.of(2026, 5, 25), "315 lb", 1, false);

    LiftTrendSummary summary =
        LiftTrendSummary.from(List.of(oldExecution, warmup, recentVolume, recentBest), today);

    assertEquals(LocalDate.of(2026, 3, 2), summary.windowStart());
    assertEquals(4, summary.totalExecutions());
    assertEquals(3, summary.recentExecutions());
    assertEquals(3, summary.recentTrainingDays());
    assertEquals(3, summary.recentWorkSets());
    assertEquals(9, summary.recentRepVolume());
    assertEquals(2265, summary.recentTonnageLbs());
    assertEquals(LocalDate.of(2026, 5, 25), summary.bestRecentSet().date());
    assertEquals("315 lb", summary.bestRecentSet().weight());
    assertEquals(315, summary.bestRecentSet().pounds());
    assertFalse(summary.hasSparseRecentHistory());
  }

  @Test
  void keepsLastExecutionWhenHistoryIsOutsideRecentWindow() {
    LiftExecution oldExecution = execution(1, LocalDate.of(2026, 1, 15), "225 lb", 5, false);

    LiftTrendSummary summary =
        LiftTrendSummary.from(List.of(oldExecution), LocalDate.of(2026, 5, 30));

    assertTrue(summary.hasAnyExecutions());
    assertFalse(summary.hasRecentExecutions());
    assertEquals(oldExecution, summary.lastExecution());
    assertNull(summary.bestRecentSet());
  }

  @Test
  void emptyHistoryProducesZeroCounts() {
    LiftTrendSummary summary = LiftTrendSummary.from(List.of(), LocalDate.of(2026, 5, 30));

    assertFalse(summary.hasAnyExecutions());
    assertEquals(0, summary.recentExecutions());
    assertEquals(0, summary.recentWorkSets());
    assertNull(summary.lastExecution());
    assertNull(summary.bestRecentSet());
  }

  private static LiftExecution execution(
      int id, LocalDate date, String weight, int reps, boolean warmup) {
    return new LiftExecution(
        id,
        date,
        List.of(new ExecutionSet(new SetMetric.Reps(reps), weight, 8.0f)),
        warmup,
        false,
        "");
  }
}
