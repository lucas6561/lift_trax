package com.lifttrax.workout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lifttrax.models.ExecutionSet;
import com.lifttrax.models.LiftExecution;
import com.lifttrax.models.SetMetric;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkoutHistoryMissesTest {
  @Test
  void missesStayVisibleWithoutRaisingPercentBasedRecommendations() {
    var target =
        new PlannedWorkoutFile.PlannedSetTarget(
            1, "reps", 5, null, null, null, null, null, null, 80, null, "STRAIGHT", false);
    var failedSingle = execution(new ExecutionSet(new SetMetric.Reps(1), "400 lb", 10f, true));
    var failedSet = execution(new ExecutionSet(new SetMetric.Reps(3), "350 lb", 10f, true));
    var missedOnly = List.of(failedSingle, failedSet);
    assertNull(WorkoutHistoryFormatter.bestOneRepMax(missedOnly));
    assertNull(WorkoutHistoryFormatter.suggestedWeight(missedOnly, target));
    assertTrue(
        WorkoutHistoryFormatter.lastExecutionSummary(missedOnly, false, true)
            .contains("missed target"));

    var goodSingle = execution(new ExecutionSet(new SetMetric.Reps(1), "300 lb", 10f));
    var mixed = List.of(goodSingle, failedSingle, failedSet);
    assertEquals("300 lb", WorkoutHistoryFormatter.bestOneRepMax(mixed));
    assertEquals(
        WorkoutHistoryFormatter.suggestedWeight(List.of(goodSingle), target),
        WorkoutHistoryFormatter.suggestedWeight(mixed, target));

    var goodSet = execution(new ExecutionSet(new SetMetric.Reps(5), "200 lb", 8f));
    assertEquals(
        WorkoutHistoryFormatter.suggestedWeight(List.of(goodSet), target),
        WorkoutHistoryFormatter.suggestedWeight(List.of(goodSet, failedSingle, failedSet), target));
  }

  private static LiftExecution execution(ExecutionSet set) {
    return new LiftExecution(null, LocalDate.of(2026, 9, 8), List.of(set), false, false, "");
  }
}
