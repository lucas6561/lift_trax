package com.lifttrax.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifttrax.db.SqliteDb;
import com.lifttrax.models.Lift;
import com.lifttrax.models.LiftRegion;
import com.lifttrax.models.LiftType;
import com.lifttrax.workout.PlannedWorkoutFile;
import com.lifttrax.workout.PlannedWorkoutJson;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PlannedWorkoutGuidanceTest {
  @Test
  void disclosuresAndOptionalCalculatorRenderWithDocumentedDefaults() throws Exception {
    String html =
        PlannedWorkoutSessionHtml.renderPage(
            workout(),
            1,
            "MONDAY",
            List.of(new Lift("Back Squat", LiftRegion.LOWER, LiftType.SQUAT, List.of(), "")),
            LocalDate.parse("2026-11-02"));
    for (String pane : List.of("history", "warmups", "plates")) {
      assertTrue(html.contains("data-pane='" + pane + "'>"), pane);
      assertFalse(html.contains("data-pane='" + pane + "' open"), pane);
    }
    assertTrue(html.contains("data-pane='targets' open><summary>Planned Targets</summary>"));
    assertTrue(
        html.contains("class='session-history' aria-label='Exercise history' aria-live='polite'"));
    assertTrue(html.contains("<option value='45' selected>45 lb (default)</option>"));
    assertTrue(html.contains("Live plate calculation requires JavaScript."));
    assertFalse(html.contains("name='bar'"));
    assertTrue(html.contains("formnovalidate>Save Workout</button>"));
  }

  @Test
  void nativeCompletedWorkUsesTheSameValidationAndPreservesWeights() throws Exception {
    var workout = workout();
    var day = PlannedWorkoutSessionService.findDay(workout, 1, "MONDAY");
    var form = completed(day);
    String results = PlannedWorkoutNativeSession.results(day, form);
    Path path = Files.createTempFile("lifttrax-native-session", ".db");
    try (var db = new SqliteDb(path.toString())) {
      db.addLift("Back Squat", LiftRegion.LOWER, LiftType.SQUAT, List.of(), "");
      var saved =
          PlannedWorkoutSessionService.save(
              db, workout, 1, "MONDAY", LocalDate.parse("2026-11-02"), results);
      assertEquals(1, saved.loggedExecutionCount());
      var execution = db.getExecutions("Back Squat").get(0);
      assertEquals(2, execution.sets().size());
      assertEquals("185 lb", execution.sets().get(0).weight());
      form.put(
          "native-"
              + PlannedWorkoutSessionService.plannedExercises(day).get(0).key()
              + "-metricValue",
          "0");
      assertThrows(
          IllegalArgumentException.class,
          () ->
              PlannedWorkoutSessionService.save(
                  db,
                  workout,
                  1,
                  "MONDAY",
                  LocalDate.parse("2026-11-02"),
                  PlannedWorkoutNativeSession.results(day, form)));
    } finally {
      Files.deleteIfExists(path);
    }
  }

  @Test
  void nativeRenderingEscapesValuesAndSupportsAllExistingMetricLabels() {
    for (String metric : List.of("reps", "reps-lr", "time", "distance")) {
      StringBuilder html = new StringBuilder();
      PlannedWorkoutNativeSession.append(
          html,
          "1:0",
          Set.of("Lift <&'"),
          new WebUiRenderer.AddExecutionPrefill(
              "Lift <&'",
              "185 lb",
              "2",
              "",
              metric,
              "5",
              "6",
              "7",
              "2026-11-02",
              false,
              false,
              ""));
      assertFalse(html.toString().contains("Lift <&'"));
      assertTrue(html.toString().contains("Lift &lt;&amp;&#39;"));
      assertTrue(html.toString().contains("native-1:0-weight"));
      assertTrue(
          html.toString()
              .contains(
                  metric.equals("reps-lr")
                      ? "Left reps"
                      : metric.equals("time")
                          ? "Seconds"
                          : metric.equals("distance") ? "Feet" : "Reps"));
    }
  }

  @Test
  void nativeEmptyWorkStaysSkippedAndExcessiveSetCountsAreRejected() throws Exception {
    var day = PlannedWorkoutSessionService.findDay(workout(), 1, "MONDAY");
    var json = new ObjectMapper().readTree(PlannedWorkoutNativeSession.results(day, Map.of()));
    json.forEach(result -> assertEquals("skipped", result.path("state").asText()));
    var form = completed(day);
    String prefix =
        "native-" + PlannedWorkoutSessionService.plannedExercises(day).get(0).key() + "-";
    for (String count : List.of("0", "1001", "invalid")) {
      form.put(prefix + "setCount", count);
      assertThrows(
          IllegalArgumentException.class, () -> PlannedWorkoutNativeSession.results(day, form));
    }
  }

  private static Map<String, String> completed(PlannedWorkoutFile.PlannedWorkoutDay day) {
    String prefix =
        "native-" + PlannedWorkoutSessionService.plannedExercises(day).get(0).key() + "-";
    var form = new HashMap<String, String>();
    for (var entry :
        Map.of(
                "state",
                "complete",
                "lift",
                "Back Squat",
                "weight",
                "185 lb",
                "metricType",
                "reps",
                "metricValue",
                "5",
                "setCount",
                "2")
            .entrySet()) {
      form.put(prefix + entry.getKey(), entry.getValue());
    }
    return form;
  }

  private static PlannedWorkoutFile workout() throws Exception {
    return PlannedWorkoutJson.readPath(Path.of("shared/workouts/examples/conjugate-wave-v2.json"));
  }
}
