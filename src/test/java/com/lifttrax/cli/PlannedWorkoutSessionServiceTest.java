package com.lifttrax.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lifttrax.db.SqliteDb;
import com.lifttrax.models.ExecutionSet;
import com.lifttrax.models.LiftExecution;
import com.lifttrax.models.LiftRegion;
import com.lifttrax.models.LiftType;
import com.lifttrax.models.SetMetric;
import com.lifttrax.workout.PlannedWorkoutFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlannedWorkoutSessionServiceTest {

  @Test
  void saveWritesSeededWorkoutMetricsSwapAndNotesToNormalExecutionHistory() throws Exception {
    Path dbPath = Files.createTempFile("lifttrax-follow-session", ".db");
    try (SqliteDb db = new SqliteDb(dbPath.toString())) {
      db.addLift("Back Squat", LiftRegion.LOWER, LiftType.SQUAT, List.of(), "");
      db.addLift("Front Squat", LiftRegion.LOWER, LiftType.SQUAT, List.of(), "");
      db.addLift("Farmer Carry", LiftRegion.LOWER, LiftType.CONDITIONING, List.of(), "");

      PlannedWorkoutSessionService.SaveSummary summary =
          PlannedWorkoutSessionService.save(
              db,
              workoutFile(),
              1,
              "MONDAY",
              LocalDate.parse("2026-05-31"),
              """
              [
                {
                  "exerciseKey": "1:0",
                  "plannedLift": "Back Squat",
                  "performedLift": "Front Squat",
                  "state": "complete",
                  "notes": "Rack was busy.",
                  "sets": [
                    {"state":"complete","metricType":"reps","metricValue":"5","metricLeft":"","metricRight":"","weight":"225 lb","rpe":"8"},
                    {"state":"complete","metricType":"reps-lr","metricValue":"","metricLeft":"7","metricRight":"8","weight":"40 lb","rpe":"7.5"},
                    {"state":"complete","metricType":"time","metricValue":"30","metricLeft":"","metricRight":"","weight":"none","rpe":""},
                    {"state":"complete","metricType":"distance","metricValue":"100","metricLeft":"","metricRight":"","weight":"90 lb","rpe":""},
                    {"state":"skipped","metricType":"reps","metricValue":"3","metricLeft":"","metricRight":"","weight":"","rpe":""}
                  ]
                },
                {
                  "exerciseKey": "2:0",
                  "plannedLift": "Farmer Carry",
                  "performedLift": "Farmer Carry",
                  "state": "skipped",
                  "notes": "",
                  "sets": []
                }
              ]
              """);

      assertEquals(1, summary.loggedExecutionCount());
      assertEquals(1, summary.substitutionCount());
      assertEquals(1, summary.skippedExercises());
      assertEquals(1, summary.skippedSets());

      List<LiftExecution> executions = db.getExecutions("Front Squat");
      assertEquals(1, executions.size());
      LiftExecution execution = executions.get(0);
      assertEquals(LocalDate.parse("2026-05-31"), execution.date());
      assertEquals("Rack was busy.", execution.notes());
      assertEquals(4, execution.sets().size());
      assertMetric(execution.sets().get(0), SetMetric.Reps.class, "225 lb", 8.0f);
      assertMetric(execution.sets().get(1), SetMetric.RepsLr.class, "40 lb", 7.5f);
      assertMetric(execution.sets().get(2), SetMetric.TimeSecs.class, "none", null);
      assertMetric(execution.sets().get(3), SetMetric.DistanceFeet.class, "90 lb", null);
      assertTrue(db.getExecutions("Back Squat").isEmpty());
      assertTrue(db.getExecutions("Farmer Carry").isEmpty());
    }
  }

  @Test
  void saveAllowsChangingToAnyLiftInTheLocalLibrary() throws Exception {
    Path dbPath = Files.createTempFile("lifttrax-follow-session-swap", ".db");
    try (SqliteDb db = new SqliteDb(dbPath.toString())) {
      db.addLift("Back Squat", LiftRegion.LOWER, LiftType.SQUAT, List.of(), "");
      db.addLift("Front Squat", LiftRegion.LOWER, LiftType.SQUAT, List.of(), "");
      db.addLift("Farmer Carry", LiftRegion.LOWER, LiftType.CONDITIONING, List.of(), "");
      db.addLift("Bench Press", LiftRegion.UPPER, LiftType.BENCH_PRESS, List.of(), "");

      PlannedWorkoutSessionService.SaveSummary summary =
          PlannedWorkoutSessionService.save(
              db,
              workoutFile(),
              1,
              "MONDAY",
              LocalDate.parse("2026-05-31"),
              """
              [
                {
                  "exerciseKey":"1:0",
                  "plannedLift":"Back Squat",
                  "performedLift":"Bench Press",
                  "state":"complete",
                  "notes":"",
                  "sets":[{"state":"complete","metricType":"reps","metricValue":"5","weight":"225 lb","rpe":""}]
                },
                {
                  "exerciseKey":"2:0",
                  "plannedLift":"Farmer Carry",
                  "performedLift":"Farmer Carry",
                  "state":"skipped",
                  "notes":"",
                  "sets":[]
                }
              ]
              """);

      assertTrue(db.getExecutions("Back Squat").isEmpty());
      assertEquals(1, db.getExecutions("Bench Press").size());
      assertEquals(1, summary.substitutionCount());
    }
  }

  @Test
  void partialSaveWritesSubmittedBlockOnlyAndSkipsExactDuplicateSubmissions() throws Exception {
    Path dbPath = Files.createTempFile("lifttrax-follow-session-partial", ".db");
    try (SqliteDb db = new SqliteDb(dbPath.toString())) {
      db.addLift("Back Squat", LiftRegion.LOWER, LiftType.SQUAT, List.of(), "");
      db.addLift("Farmer Carry", LiftRegion.LOWER, LiftType.CONDITIONING, List.of(), "");
      String blockResults =
          """
          [
            {
              "exerciseKey":"1:0",
              "plannedLift":"Back Squat",
              "performedLift":"Back Squat",
              "state":"complete",
              "notes":"first block done",
              "sets":[{"state":"complete","metricType":"reps","metricValue":"5","weight":"225 lb","rpe":""}]
            }
          ]
          """;

      PlannedWorkoutSessionService.SaveSummary first =
          PlannedWorkoutSessionService.saveSubmittedResults(
              db, workoutFile(), 1, "MONDAY", LocalDate.parse("2026-05-31"), blockResults, false);
      PlannedWorkoutSessionService.SaveSummary duplicate =
          PlannedWorkoutSessionService.saveSubmittedResults(
              db, workoutFile(), 1, "MONDAY", LocalDate.parse("2026-05-31"), blockResults, false);

      assertEquals(1, first.loggedExecutionCount());
      assertEquals(0, duplicate.loggedExecutionCount());
      assertEquals(1, db.getExecutions("Back Squat").size());
      assertTrue(db.getExecutions("Farmer Carry").isEmpty());
    }
  }

  @Test
  void saveRejectsChangedLiftThatIsNotInTheLocalLibrary() throws Exception {
    Path dbPath = Files.createTempFile("lifttrax-follow-session-unknown-swap", ".db");
    try (SqliteDb db = new SqliteDb(dbPath.toString())) {
      db.addLift("Back Squat", LiftRegion.LOWER, LiftType.SQUAT, List.of(), "");
      db.addLift("Farmer Carry", LiftRegion.LOWER, LiftType.CONDITIONING, List.of(), "");

      IllegalArgumentException error =
          assertThrows(
              IllegalArgumentException.class,
              () ->
                  PlannedWorkoutSessionService.save(
                      db,
                      workoutFile(),
                      1,
                      "MONDAY",
                      LocalDate.parse("2026-05-31"),
                      """
                      [
                        {
                          "exerciseKey":"1:0",
                          "plannedLift":"Back Squat",
                          "performedLift":"Imaginary Squat",
                          "state":"complete",
                          "notes":"",
                          "sets":[{"state":"complete","metricType":"reps","metricValue":"5","weight":"225 lb","rpe":""}]
                        },
                        {
                          "exerciseKey":"2:0",
                          "plannedLift":"Farmer Carry",
                          "performedLift":"Farmer Carry",
                          "state":"skipped",
                          "notes":"",
                          "sets":[]
                        }
                      ]
                      """));

      assertTrue(error.getMessage().contains("Lift not found: Imaginary Squat"));
      assertTrue(db.getExecutions("Back Squat").isEmpty());
    }
  }

  private static void assertMetric(
      ExecutionSet set, Class<? extends SetMetric> type, String weight, Float rpe) {
    assertInstanceOf(type, set.metric());
    assertEquals(weight, set.weight());
    assertEquals(rpe, set.rpe());
  }

  private static PlannedWorkoutFile workoutFile() {
    PlannedWorkoutFile.PlannedExercise backSquat =
        new PlannedWorkoutFile.PlannedExercise(
            "Back Squat",
            "LOWER",
            "SQUAT",
            List.of("QUAD"),
            List.of(
                new PlannedWorkoutFile.PlannedSetTarget(
                    1, "reps", 5, null, null, null, null, null, null, 80, null, "STRAIGHT", false)),
            "",
            List.of("Front Squat"));
    PlannedWorkoutFile.PlannedExercise farmerCarry =
        new PlannedWorkoutFile.PlannedExercise(
            "Farmer Carry", "LOWER", "CONDITIONING", List.of("CORE"), List.of(), "", List.of());
    return new PlannedWorkoutFile(
        2,
        new PlannedWorkoutFile.PlannedWorkoutMetadata(
            "Imported Wave", "Ready to train.", 1, List.of()),
        new PlannedWorkoutFile.PlannedWorkoutSource(
            "wave-generation", "conjugate", "Conjugate Wave", null, "2026-05-31T00:00:00Z"),
        List.of(
            new PlannedWorkoutFile.PlannedWorkoutWeek(
                1,
                List.of(
                    new PlannedWorkoutFile.PlannedWorkoutDay(
                        "MONDAY",
                        "Monday",
                        List.of(
                            new PlannedWorkoutFile.PlannedWorkoutBlock(
                                1,
                                "Main Work",
                                "supplemental",
                                null,
                                false,
                                List.of(backSquat),
                                List.of()),
                            new PlannedWorkoutFile.PlannedWorkoutBlock(
                                2,
                                "Conditioning",
                                "conditioning",
                                null,
                                false,
                                List.of(farmerCarry),
                                List.of())),
                        List.of())))),
        List.of());
  }
}
