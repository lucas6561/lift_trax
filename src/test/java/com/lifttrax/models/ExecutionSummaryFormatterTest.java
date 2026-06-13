package com.lifttrax.models;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExecutionSummaryFormatterTest {

  @Test
  void compactSummaryKeepsMatchingSetDisplay() {
    LiftExecution execution =
        new LiftExecution(
            null,
            LocalDate.parse("2026-06-12"),
            List.of(
                new ExecutionSet(new SetMetric.Reps(10), "125 lb", null),
                new ExecutionSet(new SetMetric.Reps(10), "125 lb", null),
                new ExecutionSet(new SetMetric.Reps(10), "125 lb", null),
                new ExecutionSet(new SetMetric.Reps(10), "125 lb", null)),
            false,
            false,
            "");

    assertEquals(
        "4 sets x 10 reps @ 125 lb", ExecutionSummaryFormatter.formatCompactSummary(execution));
  }

  @Test
  void compactSummaryListsDifferentSetsInsteadOfRepeatingTheFirstSet() {
    LiftExecution execution =
        new LiftExecution(
            null,
            LocalDate.parse("2026-06-12"),
            List.of(
                new ExecutionSet(new SetMetric.Reps(10), "125 lb", null),
                new ExecutionSet(new SetMetric.Reps(8), "125 lb", null),
                new ExecutionSet(new SetMetric.Reps(6), "125 lb", 8.5f),
                new ExecutionSet(new SetMetric.Reps(4), "115 lb", null)),
            false,
            false,
            "rain speedrun");

    assertEquals(
        "4 sets: 10 reps @ 125 lb; 8 reps @ 125 lb; 6 reps @ 125 lb RPE 8.5; 4 reps @ 115 lb - rain speedrun",
        ExecutionSummaryFormatter.formatCompactSummary(execution));
  }
}
