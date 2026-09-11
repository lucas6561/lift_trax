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
  void saveKeepsMissedAttemptsWithZeroOrPartialActualsAndRetriesWithoutDuplicates()
      throws Exception {
    Path dbPath = Files.createTempFile("lifttrax-missed-session", ".db");
    try (SqliteDb db = new SqliteDb(dbPath.toString())) {
      db.addLift("Back Squat", LiftRegion.LOWER, LiftType.SQUAT, List.of(), "");
      String results =
          """
          [{"exerciseKey":"1:0","plannedLift":"Back Squat","performedLift":"Back Squat","state":"complete",
            "notes":"Could not meet the prescribed load at the RPE cap.","sets":[
              {"metricType":"reps","metricValue":"0","weight":"225 lb","rpe":"","missed":true},
              {"metricType":"reps","metricValue":"3","weight":"205 lb","rpe":"9.5","missed":true},
              {"metricType":"reps","metricValue":"5","weight":"185 lb","rpe":"8"},
              {"metricType":"reps-lr","metricLeft":"0","metricRight":"2","weight":"40 lb","missed":true},
              {"metricType":"time","metricValue":"0","weight":"none","missed":true},
              {"metricType":"distance","metricValue":"0","weight":"90 lb","missed":true}
            ]}]
          """;
      LocalDate date = LocalDate.parse("2026-09-08");
      PlannedWorkoutSessionService.saveSubmittedResults(
          db, workoutFile(), 1, "MONDAY", date, results, false, "missed:block:0");
      PlannedWorkoutSessionService.saveSubmittedResults(
          db, workoutFile(), 1, "MONDAY", date, results, false, "missed:block:0");

      List<LiftExecution> executions = db.getExecutions("Back Squat");
      assertEquals(1, executions.size());
      List<ExecutionSet> sets = executions.get(0).sets();
      assertEquals(new ExecutionSet(new SetMetric.Reps(0), "225 lb", null, true), sets.get(0));
      assertEquals(new ExecutionSet(new SetMetric.Reps(3), "205 lb", 9.5f, true), sets.get(1));
      assertEquals(new ExecutionSet(new SetMetric.Reps(5), "185 lb", 8f), sets.get(2));
      assertEquals(new ExecutionSet(new SetMetric.RepsLr(0, 2), "40 lb", null, true), sets.get(3));
      assertEquals(new ExecutionSet(new SetMetric.TimeSecs(0), "none", null, true), sets.get(4));
      assertEquals(
          new ExecutionSet(new SetMetric.DistanceFeet(0), "90 lb", null, true), sets.get(5));
      assertTrue(executions.get(0).notes().contains("RPE cap"));
      assertThrows(
          IllegalArgumentException.class,
          () ->
              PlannedWorkoutSessionService.saveSubmittedResults(
                  db,
                  workoutFile(),
                  1,
                  "MONDAY",
                  date,
                  results.replace("\"missed\":true", "\"missed\":false"),
                  false,
                  "missed:block:0"));
    }
  }

  @Test
  void missesStillRequireNonnegativeActualsAndValidRpe() throws Exception {
    Path dbPath = Files.createTempFile("lifttrax-missed-invalid", ".db");
    try (SqliteDb db = new SqliteDb(dbPath.toString())) {
      db.addLift("Back Squat", LiftRegion.LOWER, LiftType.SQUAT, List.of(), "");
      List<String> invalidSets =
          List.of(
              "{\"metricValue\":\"0\"}",
              "{\"metricValue\":\"0\",\"missed\":false}",
              "{\"metricValue\":\"-1\",\"missed\":true}",
              "{\"metricValue\":\"\",\"missed\":true}",
              "{\"metricValue\":\"1.5\",\"missed\":true}",
              "{\"metricValue\":\"0\",\"missed\":true,\"rpe\":\"11\"}",
              "{\"metricType\":\"reps-lr\",\"metricLeft\":\"0\",\"metricRight\":\"-1\",\"missed\":true}");
      for (String set : invalidSets) {
        String results =
            "[{\"exerciseKey\":\"1:0\",\"plannedLift\":\"Back Squat\",\"performedLift\":\"Back Squat\",\"state\":\"complete\",\"sets\":["
                + set
                + "]}]";
        assertThrows(
            IllegalArgumentException.class,
            () ->
                PlannedWorkoutSessionService.saveSubmittedResults(
                    db, workoutFile(), 1, "MONDAY", LocalDate.parse("2026-09-08"), results, false),
            set);
      }
      assertTrue(db.getExecutions("Back Squat").isEmpty());
    }
  }

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
  void stableSubmissionIdMakesRetriesIdempotentWithoutSuppressingANewIdenticalWorkout()
      throws Exception {
    Path dbPath = Files.createTempFile("lifttrax-follow-session-idempotent", ".db");
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
              "notes":"same work",
              "sets":[{"state":"complete","metricType":"reps","metricValue":"5","weight":"225 lb","rpe":""}]
            }
          ]
          """;

      PlannedWorkoutSessionService.SaveSummary first =
          PlannedWorkoutSessionService.saveSubmittedResults(
              db,
              workoutFile(),
              1,
              "MONDAY",
              LocalDate.parse("2026-05-31"),
              blockResults,
              false,
              "session-a:block:0");
      PlannedWorkoutSessionService.SaveSummary retry =
          PlannedWorkoutSessionService.saveSubmittedResults(
              db,
              workoutFile(),
              1,
              "MONDAY",
              LocalDate.parse("2026-05-31"),
              blockResults,
              false,
              "session-a:block:0");
      PlannedWorkoutSessionService.SaveSummary newWorkout =
          PlannedWorkoutSessionService.saveSubmittedResults(
              db,
              workoutFile(),
              1,
              "MONDAY",
              LocalDate.parse("2026-05-31"),
              blockResults,
              false,
              "session-b:block:0");

      assertEquals(1, first.loggedExecutionCount());
      assertEquals(1, retry.loggedExecutionCount());
      assertEquals(1, newWorkout.loggedExecutionCount());
      assertEquals(2, db.getExecutions("Back Squat").size());
    }
  }

  @Test
  void stableSubmissionIdRejectsChangedPayloadInsteadOfWritingAConflict() throws Exception {
    Path dbPath = Files.createTempFile("lifttrax-follow-session-conflict", ".db");
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
              "notes":"original",
              "sets":[{"state":"complete","metricType":"reps","metricValue":"5","weight":"225 lb","rpe":""}]
            }
          ]
          """;
      PlannedWorkoutSessionService.saveSubmittedResults(
          db,
          workoutFile(),
          1,
          "MONDAY",
          LocalDate.parse("2026-05-31"),
          blockResults,
          false,
          "session-a:block:0");

      IllegalArgumentException conflict =
          assertThrows(
              IllegalArgumentException.class,
              () ->
                  PlannedWorkoutSessionService.saveSubmittedResults(
                      db,
                      workoutFile(),
                      1,
                      "MONDAY",
                      LocalDate.parse("2026-05-31"),
                      blockResults.replace("original", "changed"),
                      false,
                      "session-a:block:0"));

      assertTrue(conflict.getMessage().contains("changed after it was already submitted"));
      assertEquals(1, db.getExecutions("Back Squat").size());
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
