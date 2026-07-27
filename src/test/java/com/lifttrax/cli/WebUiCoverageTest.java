package com.lifttrax.cli;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lifttrax.db.SqliteDb;
import com.lifttrax.models.ExecutionSet;
import com.lifttrax.models.Lift;
import com.lifttrax.models.LiftExecution;
import com.lifttrax.models.LiftRegion;
import com.lifttrax.models.LiftStats;
import com.lifttrax.models.LiftType;
import com.lifttrax.models.Muscle;
import com.lifttrax.models.SetMetric;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WebUiCoverageTest {

  @Test
  void liftListAndMetricFormattingCoverFilteringEscapingAndEveryMetric() {
    List<Lift> lifts =
        List.of(
            new Lift(
                "Back <Squat>",
                LiftRegion.LOWER,
                LiftType.SQUAT,
                List.of(Muscle.QUAD, Muscle.GLUTE),
                ""),
            new Lift("Carry", LiftRegion.UPPER, null, List.of(Muscle.FOREARM), ""));

    String all = WebUiRenderer.renderLiftList(lifts, "", "All <lifts>");
    assertTrue(all.contains("All &lt;lifts&gt;"));
    assertTrue(all.contains("Back &lt;Squat&gt;"));
    assertTrue(all.contains("data-main='Unknown'"));
    assertTrue(all.contains("data-muscles='QUAD,GLUTE'"));

    String filtered = WebUiRenderer.renderLiftList(lifts, "carry", "Filtered");
    assertTrue(filtered.contains("Carry"));
    assertFalse(filtered.contains("Back &lt;Squat&gt;"));

    assertTrue(WebUiRenderer.formatMetric(new SetMetric.Reps(5)).contains("5 reps"));
    assertTrue(WebUiRenderer.formatMetric(new SetMetric.RepsLr(4, 3)).contains("4L/3R"));
    assertTrue(WebUiRenderer.formatMetric(new SetMetric.RepsRange(8, 12)).contains("8-12"));
    assertTrue(WebUiRenderer.formatMetric(new SetMetric.TimeSecs(30)).contains("30 sec"));
    assertTrue(WebUiRenderer.formatMetric(new SetMetric.DistanceFeet(100)).contains("100 ft"));
  }

  @Test
  void queryContentCoversRecentHistoryNoHistoryAndStatsFailures() throws Exception {
    Path dbPath = Files.createTempFile("lifttrax-query-coverage", ".db");
    try (SqliteDb db = new SqliteDb(dbPath.toString())) {
      db.addLift("Back Squat", LiftRegion.LOWER, LiftType.SQUAT, List.of(), "");
      db.addLiftExecution(
          "Back Squat",
          new LiftExecution(
              null,
              LocalDate.now().minusDays(10),
              List.of(new ExecutionSet(new SetMetric.Reps(5), "315 lb", 8.0f)),
              false,
              false,
              "recent"));
      db.addLiftExecution(
          "Back Squat",
          new LiftExecution(
              null,
              LocalDate.now().minusDays(500),
              List.of(new ExecutionSet(new SetMetric.Reps(3), "335 lb", 9.0f)),
              false,
              false,
              "old"));

      String content = WebUiRenderer.renderQueryContent(db, "Back Squat");
      assertTrue(content.contains("315 lb"));
      assertFalse(content.contains("old"));
      assertTrue(content.contains("5 reps"));
      assertTrue(WebUiRenderer.renderQueryContent(db, "").contains("Select a lift"));
      assertTrue(
          WebUiRenderer.renderQueryContent(db, "Missing Lift")
              .contains("Failed to load best-by-reps data"));
    } finally {
      Files.deleteIfExists(dbPath);
    }

    Path unsupportedPath = Files.createTempFile("lifttrax-query-unsupported", ".db");
    try (SqliteDb unsupported =
        new SqliteDb(unsupportedPath.toString()) {
          @Override
          public LiftStats liftStats(String name) {
            throw new UnsupportedOperationException("not supported");
          }
        }) {
      unsupported.addLift("Press", LiftRegion.UPPER, LiftType.BENCH_PRESS, List.of(), "");
      assertTrue(
          WebUiRenderer.renderQueryContent(unsupported, "Press")
              .contains("Not available in the Java port yet"));
    } finally {
      Files.deleteIfExists(unsupportedPath);
    }
  }

  @Test
  void completeWavePlannerRendersAllRotationAndDeloadControls() throws Exception {
    Path dbPath = Files.createTempFile("lifttrax-wave-planner-coverage", ".db");
    try (SqliteDb db = new SqliteDb(dbPath.toString())) {
      seedMainLifts(db);
      Map<String, String> values = new HashMap<>();
      values.put("waveType", "conjugate");
      values.put("meLowerWeek1", "Squat 4");
      values.put("meUpperWeek1", "Bench 4");
      values.put("deSquat", "Squat");
      values.put("deSquatAr", "BANDS");

      String planner = WebUiRenderer.renderWaveContent(db, 7, values);
      assertTrue(planner.contains("Max Effort Rotation"));
      assertTrue(planner.contains("Deload Weeks"));
      assertTrue(planner.contains("Week 7 Lower Squat"));
      assertTrue(planner.contains("Dynamic Effort Lifts"));
      assertTrue(planner.contains("Generate Wave"));

      String hypertrophy =
          WebUiRenderer.renderWaveContent(
              db, 1, Map.of("waveType", "hypertrophy", "waveGenerate", "true"));
      assertTrue(hypertrophy.contains("Configured weeks"));

      String deload =
          WebUiRenderer.renderWaveContent(
              db, 1, Map.of("waveType", "deload", "waveGenerate", "true"));
      assertTrue(deload.contains("Configured weeks"));
    } finally {
      Files.deleteIfExists(dbPath);
    }
  }

  private static void seedMainLifts(SqliteDb db) throws Exception {
    db.addLift("Squat", LiftRegion.LOWER, LiftType.SQUAT, List.of(), "");
    db.addLift("Deadlift", LiftRegion.LOWER, LiftType.DEADLIFT, List.of(), "");
    db.addLift("Bench Press", LiftRegion.UPPER, LiftType.BENCH_PRESS, List.of(), "");
    db.addLift("Overhead Press", LiftRegion.UPPER, LiftType.OVERHEAD_PRESS, List.of(), "");
    for (int i = 2; i <= 5; i++) {
      db.addLift("Squat " + i, LiftRegion.LOWER, LiftType.SQUAT, List.of(), "");
      db.addLift("Deadlift " + i, LiftRegion.LOWER, LiftType.DEADLIFT, List.of(), "");
      db.addLift("Bench " + i, LiftRegion.UPPER, LiftType.BENCH_PRESS, List.of(), "");
      db.addLift("Overhead " + i, LiftRegion.UPPER, LiftType.OVERHEAD_PRESS, List.of(), "");
    }
  }
}
