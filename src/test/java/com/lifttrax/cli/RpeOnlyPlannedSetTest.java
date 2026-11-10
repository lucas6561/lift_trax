package com.lifttrax.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lifttrax.db.SqliteDb;
import com.lifttrax.models.ExecutionSet;
import com.lifttrax.models.Lift;
import com.lifttrax.models.LiftExecution;
import com.lifttrax.models.LiftRegion;
import com.lifttrax.models.LiftType;
import com.lifttrax.models.SetMetric;
import com.lifttrax.models.WeightText;
import com.lifttrax.workout.PlannedWorkoutFile;
import com.lifttrax.workout.PlannedWorkoutJson;
import com.lifttrax.workout.PlannedWorkoutText;
import com.lifttrax.workout.WorkoutHistoryFormatter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class RpeOnlyPlannedSetTest {
  private static final String LIFT_NAME = "Pinch Block Lift";

  @Test
  void schemaV7ImportAndSerializationKeepRpeOnlySetsUnweighted() throws Exception {
    PlannedWorkoutFile workout = PlannedWorkoutJson.readString(rpeOnlyWorkoutJson());
    PlannedWorkoutFile.PlannedSetTarget target = firstTarget(workout);

    assertEquals(7.0f, target.rpe());
    assertNull(target.weight());
    assertNull(target.percent());
    assertNull(target.percentOf());
    String plannedSets = PlannedWorkoutText.plannedSets(firstExercise(workout).plannedSets());
    assertTrue(plannedSets.contains("5-8 reps"));
    assertTrue(plannedSets.contains("RPE 7"));
    assertFalse(plannedSets.contains("lb"));

    String serialized = PlannedWorkoutJson.writeString(workout);
    assertFalse(serialized.contains("\"weight\""));
    assertEquals(workout, PlannedWorkoutJson.readString(serialized));
  }

  @Test
  void previewAndPrintDoNotTurnHistoryIntoAPlannedWeight() throws Exception {
    PlannedWorkoutFile workout = PlannedWorkoutJson.readString(rpeOnlyWorkoutJson());
    Path dbPath = Files.createTempFile("lifttrax-rpe-only-preview", ".db");
    try (SqliteDb db = historyDatabase(dbPath)) {
      String preview = PlannedWorkoutHtml.renderPage(workout, db);
      String print = PlannedWorkoutPrintHtml.renderPage(workout, db);

      assertTrue(preview.contains("5-8 reps"));
      assertTrue(preview.contains("RPE 7"));
      assertTrue(print.contains("5-8 reps"));
      assertTrue(print.contains("RPE 7"));
      assertTrue(preview.contains("<strong>Last:</strong> 3 sets x 5 reps @ 37.5 lb"));
      assertTrue(print.contains("<strong>Last:</strong> 3 sets x 5 reps @ 37.5 lb"));
      assertTrue(preview.contains("<strong>Best 1RM:</strong> 47.5|52.5 lb"));
      assertFalse(preview.contains("<strong>Suggested:</strong>"));
      assertFalse(print.contains("<strong>Suggested:</strong>"));
      assertFalse(preview.contains("@ 70 lb"));
      assertFalse(preview.contains("@ 75 lb"));
    }
  }

  @Test
  void workAlongLeavesRpeOnlyWeightOpenAndShowsHistorySeparately() throws Exception {
    PlannedWorkoutFile workout = PlannedWorkoutJson.readString(rpeOnlyWorkoutJson());
    Path dbPath = Files.createTempFile("lifttrax-rpe-only-session", ".db");
    try (SqliteDb db = historyDatabase(dbPath)) {
      String html =
          PlannedWorkoutSessionHtml.renderPage(
              workout,
              1,
              "MONDAY",
              List.of(new Lift(LIFT_NAME, LiftRegion.UPPER, LiftType.ACCESSORY, List.of(), "")),
              LocalDate.parse("2026-11-09"),
              db);

      assertTrue(html.contains("Target: 5-8 reps"));
      assertTrue(html.contains("RPE 7"));
      assertTrue(html.contains("class='js-weight-hidden' value=''"));
      assertTrue(html.contains("name='weightValue' data-focus-target='add-weight' value=''"));
      assertTrue(html.contains("<strong>Last:</strong> 3 sets x 5 reps @ 37.5 lb"));
      assertFalse(html.contains("Prescribed: 70 lb"));
      assertFalse(html.contains("Prescribed: 75 lb"));
      assertFalse(html.contains("Suggested: 70 lb"));
      assertFalse(html.contains("Suggested: 75 lb"));
      assertFalse(html.contains("&quot;weight&quot;"));
    }
  }

  @Test
  void leftRightHistoryRemainsLeftRightAndCannotSupplyAScalarSuggestion() throws Exception {
    PlannedWorkoutFile workout = PlannedWorkoutJson.readString(rpeOnlyWorkoutJson());
    PlannedWorkoutFile.PlannedSetTarget rpeOnly = firstTarget(workout);
    List<LiftExecution> history = history();

    assertEquals(100.0, WeightText.toPounds("47.5|52.5 lb"));
    assertEquals("47.5|52.5 lb", WorkoutHistoryFormatter.bestOneRepMax(history));
    assertNull(WorkoutHistoryFormatter.suggestedWeight(history, rpeOnly));

    PlannedWorkoutFile.PlannedSetTarget percentage =
        new PlannedWorkoutFile.PlannedSetTarget(
            1, "reps", 5, null, null, null, null, null, null, 70, null, "STRAIGHT", false);
    assertEquals("30 lb", WorkoutHistoryFormatter.suggestedWeight(history, percentage));
  }

  @Test
  void exactAndPercentageTargetsAndOlderWorkoutSchemasRemainSupported() throws Exception {
    PlannedWorkoutFile v7 =
        PlannedWorkoutJson.readPath(
            Path.of("shared", "workouts", "examples", "conjugate-wave-v7.json"));
    List<PlannedWorkoutFile.PlannedSetTarget> targets =
        v7.weeks().get(0).days().get(0).blocks().get(0).exercises().get(0).plannedSets();

    assertEquals(70, targets.get(0).percent());
    assertEquals("225 lb", targets.get(1).weight());
    assertTrue(PlannedWorkoutText.plannedSet(targets.get(0)).contains("@ 70%"));
    assertTrue(PlannedWorkoutText.plannedSet(targets.get(1)).contains("@ 225 lb"));

    for (int version = 1; version <= 6; version++) {
      PlannedWorkoutFile legacy =
          PlannedWorkoutJson.readPath(
              Path.of("shared", "workouts", "examples", "conjugate-wave-v" + version + ".json"));
      assertEquals(version, legacy.schemaVersion());
    }
  }

  private static SqliteDb historyDatabase(Path dbPath) throws Exception {
    SqliteDb db = new SqliteDb(dbPath.toString());
    db.addLift(LIFT_NAME, LiftRegion.UPPER, LiftType.ACCESSORY, List.of(), "");
    for (LiftExecution execution : history()) {
      db.addLiftExecution(LIFT_NAME, execution);
    }
    return db;
  }

  private static List<LiftExecution> history() {
    ExecutionSet recentSet = new ExecutionSet(new SetMetric.Reps(5), "37.5 lb", null);
    LiftExecution recent =
        new LiftExecution(
            null,
            LocalDate.parse("2026-11-08"),
            List.of(recentSet, recentSet, recentSet),
            false,
            false,
            "");
    LiftExecution olderLeftRight =
        new LiftExecution(
            null,
            LocalDate.parse("2026-10-01"),
            List.of(new ExecutionSet(new SetMetric.Reps(1), "47.5|52.5 lb", null)),
            false,
            false,
            "");
    return List.of(recent, olderLeftRight);
  }

  private static PlannedWorkoutFile.PlannedExercise firstExercise(PlannedWorkoutFile workout) {
    return workout.weeks().get(0).days().get(0).blocks().get(0).exercises().get(0);
  }

  private static PlannedWorkoutFile.PlannedSetTarget firstTarget(PlannedWorkoutFile workout) {
    return firstExercise(workout).plannedSets().get(0);
  }

  private static String rpeOnlyWorkoutJson() {
    return """
        {
          "schemaVersion": 7,
          "metadata": {
            "name": "Grip day",
            "description": "RPE-only grip work",
            "totalWeeks": 1,
            "tags": []
          },
          "source": {
            "kind": "import",
            "generator": "test",
            "programName": "Grip",
            "programSchemaVersion": null,
            "generatedAt": "2026-11-09T00:00:00Z"
          },
          "weeks": [{
            "weekNumber": 1,
            "days": [{
              "dayOfWeek": "MONDAY",
              "title": "Grip",
              "blocks": [{
                "order": 1,
                "title": "Grip",
                "blockType": "accessory",
                "warmup": false,
                "exercises": [{
                  "name": "Pinch Block Lift",
                  "region": "UPPER",
                  "type": "OTHER",
                  "muscles": [],
                  "plannedSets": [
                    {
                      "setNumber": 1,
                      "metricType": "reps_range",
                      "repsMin": 5,
                      "repsMax": 8,
                      "rpe": 7.0,
                      "rest": {"minimumSeconds": 60, "maximumSeconds": 90},
                      "deload": false
                    },
                    {
                      "setNumber": 2,
                      "metricType": "reps_range",
                      "repsMin": 5,
                      "repsMax": 8,
                      "rpe": 7.0,
                      "rest": {"minimumSeconds": 60, "maximumSeconds": 90},
                      "deload": false
                    }
                  ],
                  "notes": "",
                  "substitutionOptions": []
                }],
                "notes": []
              }],
              "notes": []
            }]
          }],
          "completedWorkouts": []
        }
        """;
  }
}
