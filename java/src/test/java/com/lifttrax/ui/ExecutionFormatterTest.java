package com.lifttrax.ui;

import com.lifttrax.models.ExecutionSet;
import com.lifttrax.models.LiftExecution;
import com.lifttrax.models.SetMetric;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExecutionFormatterTest {

    @Test
    void formatsExecutionWithMetricVariantsAndTags() {
        LiftExecution execution = new LiftExecution(
                1,
                LocalDate.of(2025, 1, 15),
                List.of(
                        new ExecutionSet(new SetMetric.Reps(5), "225 lb", 8.0f),
                        new ExecutionSet(new SetMetric.RepsLr(8, 7), "50 lb", null),
                        new ExecutionSet(new SetMetric.RepsRange(10, 12), "bodyweight", 7.5f),
                        new ExecutionSet(new SetMetric.TimeSecs(45), "none", null),
                        new ExecutionSet(new SetMetric.DistanceFeet(100), "sled", 9.0f)
                ),
                true,
                true,
                "Moved quickly"
        );

        String formatted = ExecutionFormatter.formatExecution(execution);

        assertEquals(
                "2025-01-15: 5 reps @ 225 lb RPE 8, 8L/7R reps @ 50 lb, 10-12 reps @ bodyweight RPE 7.5, 45 sec, 100 ft @ sled RPE 9 [warmup] [deload] — Moved quickly",
                formatted
        );
    }

    @Test
    void omitsOptionalTagsAndNotesWhenNotPresent() {
        LiftExecution execution = new LiftExecution(
                2,
                LocalDate.of(2025, 2, 1),
                List.of(new ExecutionSet(new SetMetric.Reps(3), "275 lb", null)),
                false,
                false,
                "   "
        );

        String formatted = ExecutionFormatter.formatExecution(execution);

        assertEquals("2025-02-01: 3 reps @ 275 lb", formatted);
    }

    @Test
    void groupsIdenticalSequentialSetsWithMultiplierPrefix() {
        LiftExecution execution = new LiftExecution(
                3,
                LocalDate.of(2025, 3, 1),
                List.of(
                        new ExecutionSet(new SetMetric.Reps(15), "95 lb", null),
                        new ExecutionSet(new SetMetric.Reps(15), "95 lb", null),
                        new ExecutionSet(new SetMetric.Reps(12), "95 lb", null),
                        new ExecutionSet(new SetMetric.Reps(12), "95 lb", null),
                        new ExecutionSet(new SetMetric.Reps(12), "95 lb", null)
                ),
                false,
                false,
                ""
        );

        String formatted = ExecutionFormatter.formatExecution(execution);

        assertEquals("2025-03-01: 2x15 reps @ 95 lb, 3x12 reps @ 95 lb", formatted);
    }
}
