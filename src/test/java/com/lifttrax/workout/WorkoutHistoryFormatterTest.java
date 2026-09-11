package com.lifttrax.workout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.lifttrax.models.ExecutionSet;
import com.lifttrax.models.LiftExecution;
import com.lifttrax.models.SetMetric;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkoutHistoryFormatterTest {
  @Test
  void showsThreeNewestEligibleExecutionsWithSameDayIdTieBreak() {
    var executions =
        List.of(
            execution(10, "2026-09-08", 5, false, false),
            execution(1, "2026-09-10", 3, false, false),
            execution(4, "2026-09-11", 20, true, false),
            execution(2, "2026-09-10", 8, false, false),
            execution(5, "2026-09-11", 15, false, true),
            execution(9, "2026-09-09", 1, false, false));

    assertEquals(
        "1 sets x 8 reps | 1 sets x 3 reps | 1 sets x 1 reps",
        WorkoutHistoryFormatter.lastExecutionSummary(executions, false, false));
    assertEquals(
        "1 sets x 15 reps (deload) | 1 sets x 8 reps | 1 sets x 3 reps",
        WorkoutHistoryFormatter.lastExecutionSummary(executions, false, true));
    assertEquals(
        "1 sets x 20 reps (warm-up)",
        WorkoutHistoryFormatter.lastExecutionSummary(executions, true, false));
  }

  @Test
  void returnsNoSummaryWhenNoExecutionsQualify() {
    assertNull(
        WorkoutHistoryFormatter.lastExecutionSummary(
            List.of(execution(1, "2026-09-11", 5, true, false)), false, false));
  }

  private static LiftExecution execution(
      int id, String date, int reps, boolean warmup, boolean deload) {
    return new LiftExecution(
        id,
        LocalDate.parse(date),
        List.of(new ExecutionSet(new SetMetric.Reps(reps), "none", null)),
        warmup,
        deload,
        "");
  }
}
