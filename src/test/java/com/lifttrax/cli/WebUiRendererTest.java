package com.lifttrax.cli;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lifttrax.db.SqliteDb;
import com.lifttrax.models.ExecutionSet;
import com.lifttrax.models.Lift;
import com.lifttrax.models.LiftExecution;
import com.lifttrax.models.LiftRegion;
import com.lifttrax.models.LiftType;
import com.lifttrax.models.SetMetric;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

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

      String html =
          WebUiRenderer.renderLastWeekContent(
              db, db.listLifts(), LocalDate.of(2026, 4, 12), LocalDate.of(2026, 4, 18));

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

  @Test
  void executionListIncludesDeleteLiftForm() throws Exception {
    Path dbPath = Files.createTempFile("lifttrax-exec-delete-lift", ".db");
    try (SqliteDb db = new SqliteDb(dbPath.toString())) {
      db.addLift("Back Squat", LiftRegion.LOWER, LiftType.SQUAT, List.of(), "");
      List<Lift> lifts = db.listLifts();

      String html = WebUiRenderer.renderExecutionList(db, lifts, "", "all lifts");

      assertTrue(html.contains("action='/delete-lift'"));
      assertTrue(html.contains("action='/update-lift'"));
      assertTrue(html.contains("name='currentName' value='Back Squat'"));
      assertTrue(html.contains("Save lift"));
      assertTrue(html.contains("name='tab' value='executions'"));
      assertTrue(html.contains("name='lift' value='Back Squat'"));
      assertTrue(html.contains("Delete lift"));
      assertTrue(html.contains("Delete this lift and all its executions?"));
    }
  }

  @Test
  void executionRowsIncludeEditContextForExecutionsTab() throws Exception {
    Path dbPath = Files.createTempFile("lifttrax-exec-rows-context", ".db");
    try (SqliteDb db = new SqliteDb(dbPath.toString())) {
      db.addLift("Back Squat", LiftRegion.LOWER, LiftType.SQUAT, List.of(), "");
      db.addLiftExecution("Back Squat", execution(LocalDate.of(2026, 4, 20), false));

      String html = WebUiRenderer.renderExecutionRows(db, "Back Squat");

      assertTrue(html.contains("name='executionId'"));
      assertTrue(html.contains("name='tab' value='executions'"));
      assertTrue(html.contains("name='liftQuery' value='Back Squat'"));
      assertTrue(html.contains("action='/update-execution'"));
      assertTrue(html.contains("action='/delete-execution'"));
    }
  }

  private static LiftExecution execution(LocalDate date, boolean warmup) {
    return new LiftExecution(
        null,
        date,
        List.of(new ExecutionSet(new SetMetric.Reps(5), "225 lb", 8.0f)),
        warmup,
        false,
        "");
  }
}
