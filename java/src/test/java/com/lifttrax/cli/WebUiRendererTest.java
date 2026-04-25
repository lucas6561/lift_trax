package com.lifttrax.cli;

import com.lifttrax.db.SqliteDb;
import com.lifttrax.models.ExecutionSet;
import com.lifttrax.models.LiftRegion;
import com.lifttrax.models.LiftType;
import com.lifttrax.models.LiftExecution;
import com.lifttrax.models.SetMetric;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebUiRendererTest {

    @Test
    void lastWeekContentGroupsByDayAndUsesLiftOrderWithoutDatePrefix() throws Exception {
        Path dbPath = Files.createTempFile("lifttrax-last-week-ui", ".db");
        try (SqliteDb db = new SqliteDb(dbPath.toString())) {
            db.addLift("Front Squat", LiftRegion.LOWER, LiftType.SQUAT, List.of(), "");
            db.addLift("Conventional Deadlift", LiftRegion.LOWER, LiftType.DEADLIFT, List.of(), "");
            db.addLift("Band Pull Apart", LiftRegion.UPPER, LiftType.ACCESSORY, List.of(), "");
            db.addLift("Push Ups", LiftRegion.UPPER, LiftType.BENCH_PRESS, List.of(), "");

            LocalDate april13 = LocalDate.of(2026, 4, 13);
            LocalDate april14 = LocalDate.of(2026, 4, 14);
            db.addLiftExecution("Conventional Deadlift", execution(april13, false));
            db.addLiftExecution("Front Squat", execution(april13, false));
            db.addLiftExecution("Band Pull Apart", execution(april13, false));
            db.addLiftExecution("Push Ups", execution(april13, true));
            db.addLiftExecution("Front Squat", execution(april14, false));

            String html = WebUiRenderer.renderLastWeekContent(
                    db,
                    db.listLifts(),
                    LocalDate.of(2026, 4, 12),
                    LocalDate.of(2026, 4, 18)
            );

            assertTrue(html.contains("<h4>2026-04-13</h4>"));
            assertTrue(html.contains("<h4>2026-04-14</h4>"));

            int warmupIndex = html.indexOf("Push Ups —");
            int squatIndex = html.indexOf("Front Squat —");
            int deadliftIndex = html.indexOf("Conventional Deadlift —");
            int accessoryIndex = html.indexOf("Band Pull Apart —");
            assertTrue(warmupIndex >= 0 && warmupIndex < squatIndex);
            assertTrue(squatIndex >= 0 && squatIndex < deadliftIndex);
            assertTrue(deadliftIndex >= 0 && deadliftIndex < accessoryIndex);

            assertFalse(html.contains("2026-04-13 —"));
            assertFalse(html.contains("2026-04-14 —"));
        }
    }

    private static LiftExecution execution(LocalDate date, boolean warmup) {
        return new LiftExecution(
                null,
                date,
                List.of(new ExecutionSet(new SetMetric.Reps(5), "225 lb", 8.0f)),
                warmup,
                false,
                ""
        );
    }
}
