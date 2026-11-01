package com.lifttrax.cli;

import static com.lifttrax.cli.WebServerCliTestSupport.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lifttrax.db.SqliteDb;
import com.lifttrax.models.ExecutionSet;
import com.lifttrax.models.LiftExecution;
import com.lifttrax.models.LiftRegion;
import com.lifttrax.models.LiftType;
import com.lifttrax.models.Muscle;
import com.lifttrax.models.SetMetric;
import com.lifttrax.workout.PlannedWorkoutFile;
import com.lifttrax.workout.PlannedWorkoutJson;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class WebServerCliRoutesTest {
  @Test
  void selectExecutionForFlagsMatchesWarmupAndDeload() {
    LiftExecution normal =
        new LiftExecution(
            1,
            LocalDate.parse("2026-01-01"),
            List.of(new ExecutionSet(new SetMetric.Reps(5), "225 lb", null)),
            false,
            false,
            "");
    LiftExecution warmup =
        new LiftExecution(
            2,
            LocalDate.parse("2026-01-02"),
            List.of(new ExecutionSet(new SetMetric.Reps(3), "185 lb", null)),
            true,
            false,
            "");
    LiftExecution deload =
        new LiftExecution(
            3,
            LocalDate.parse("2026-01-03"),
            List.of(new ExecutionSet(new SetMetric.Reps(2), "135 lb", null)),
            false,
            true,
            "");
    LiftExecution both =
        new LiftExecution(
            4,
            LocalDate.parse("2026-01-04"),
            List.of(new ExecutionSet(new SetMetric.Reps(2), "95 lb", null)),
            true,
            true,
            "");

    List<LiftExecution> executions = List.of(both, deload, warmup, normal);

    assertEquals(
        normal, WebExecutionRequestParser.selectExecutionForFlags(executions, false, false));
    assertEquals(
        warmup, WebExecutionRequestParser.selectExecutionForFlags(executions, true, false));
    assertEquals(
        deload, WebExecutionRequestParser.selectExecutionForFlags(executions, false, true));
    assertEquals(both, WebExecutionRequestParser.selectExecutionForFlags(executions, true, true));
  }

  @Test
  void findExecutionByIdReturnsMatch() {
    LiftExecution first =
        new LiftExecution(
            10,
            LocalDate.parse("2026-01-01"),
            List.of(new ExecutionSet(new SetMetric.Reps(5), "225 lb", null)),
            false,
            false,
            "");
    LiftExecution second =
        new LiftExecution(
            11,
            LocalDate.parse("2026-01-02"),
            List.of(new ExecutionSet(new SetMetric.Reps(3), "185 lb", null)),
            false,
            false,
            "");

    LiftExecution found = WebExecutionRequestParser.findExecutionById(List.of(first, second), 11);
    LiftExecution missing = WebExecutionRequestParser.findExecutionById(List.of(first, second), 99);

    assertEquals(second, found);
    assertTrue(missing == null);
  }

  @Test
  void updateExecutionRouteUpdatesExistingExecutionAndRedirectsToList() throws Exception {
    Path dbPath = Files.createTempFile("lifttrax-route-update-exec", ".db");
    try (SqliteDb db = new SqliteDb(dbPath.toString())) {
      db.addLift("Back Squat", LiftRegion.LOWER, LiftType.SQUAT, List.of(), "");
      db.addLiftExecution(
          "Back Squat",
          new LiftExecution(
              null,
              LocalDate.of(2026, 4, 20),
              List.of(new ExecutionSet(new SetMetric.Reps(5), "225 lb", 8.0f)),
              false,
              true,
              "old"));
      int executionId = db.getExecutions("Back Squat").get(0).id();
      TestExchange exchange =
          TestExchange.post(
              "/update-execution",
              form(
                  "lift",
                  "Back Squat",
                  "executionId",
                  String.valueOf(executionId),
                  "tab",
                  "executions",
                  "date",
                  "2026-04-21",
                  "notes",
                  "fixed",
                  "warmup",
                  "on",
                  "detailedSets",
                  """
                      [{"metricType":"reps","metricValue":"3","weight":"245 lb","rpe":"8.5"}]
                      """));

      invokeHandler("handleUpdateExecution", exchange, db);

      LiftExecution updated = db.getExecution("Back Squat", executionId);
      assertEquals(303, exchange.status());
      assertTrue(exchange.location().contains("status=Execution+updated"));
      assertTrue(exchange.location().contains("focus=execution-list"));
      assertEquals(LocalDate.of(2026, 4, 21), updated.date());
      assertTrue(updated.warmup());
      assertFalse(updated.deload());
      assertEquals("fixed", updated.notes());
      assertEquals(
          List.of(new ExecutionSet(new SetMetric.Reps(3), "245 lb", 8.5f)), updated.sets());
    } finally {
      Files.deleteIfExists(dbPath);
    }
  }

  @Test
  void updateExecutionRouteRedirectsWithErrorForMissingExecution() throws Exception {
    Path dbPath = Files.createTempFile("lifttrax-route-update-missing-exec", ".db");
    try (SqliteDb db = new SqliteDb(dbPath.toString())) {
      db.addLift("Back Squat", LiftRegion.LOWER, LiftType.SQUAT, List.of(), "");
      TestExchange exchange =
          TestExchange.post(
              "/update-execution",
              form(
                  "lift",
                  "Back Squat",
                  "executionId",
                  "999",
                  "tab",
                  "executions",
                  "date",
                  "2026-04-21"));

      invokeHandler("handleUpdateExecution", exchange, db);

      assertEquals(303, exchange.status());
      assertTrue(exchange.location().contains("statusType=error"));
      assertTrue(exchange.location().contains("Execution+not+found+for+Back+Squat"));
    } finally {
      Files.deleteIfExists(dbPath);
    }
  }

  @Test
  void deleteExecutionRouteDeletesExistingExecutionAndRedirectsToList() throws Exception {
    Path dbPath = Files.createTempFile("lifttrax-route-delete-exec", ".db");
    try (SqliteDb db = new SqliteDb(dbPath.toString())) {
      db.addLift("Bench Press", LiftRegion.UPPER, LiftType.BENCH_PRESS, List.of(), "");
      db.addLiftExecution(
          "Bench Press",
          new LiftExecution(
              null,
              LocalDate.of(2026, 4, 20),
              List.of(new ExecutionSet(new SetMetric.Reps(5), "185 lb", null)),
              false,
              false,
              ""));
      int executionId = db.getExecutions("Bench Press").get(0).id();
      TestExchange exchange =
          TestExchange.post(
              "/delete-execution",
              form("executionId", String.valueOf(executionId), "tab", "executions"));

      invokeHandler("handleDeleteExecution", exchange, db);

      assertEquals(303, exchange.status());
      assertTrue(exchange.location().contains("status=Execution+deleted"));
      assertTrue(exchange.location().contains("focus=execution-list"));
      assertTrue(db.getExecutions("Bench Press").isEmpty());
    } finally {
      Files.deleteIfExists(dbPath);
    }
  }

  @Test
  void deleteExecutionRouteRedirectsWithErrorForInvalidId() throws Exception {
    Path dbPath = Files.createTempFile("lifttrax-route-delete-invalid-exec", ".db");
    try (SqliteDb db = new SqliteDb(dbPath.toString())) {
      TestExchange exchange =
          TestExchange.post("/delete-execution", form("executionId", "", "tab", "executions"));

      invokeHandler("handleDeleteExecution", exchange, db);

      assertEquals(303, exchange.status());
      assertTrue(exchange.location().contains("statusType=error"));
      assertTrue(exchange.location().contains("Failed+to+delete+execution"));
    } finally {
      Files.deleteIfExists(dbPath);
    }
  }

  @Test
  void liftCrudFragmentAndDetailRoutesCoverSuccessfulAndInvalidRequests() throws Exception {
    Path dbPath = Files.createTempFile("lifttrax-route-lift-crud", ".db");
    try (SqliteDb db = new SqliteDb(dbPath.toString())) {
      assertLiftCreate(db);
      assertLiftUpdate(db);
      assertLiftStatusAndViews(db);
      assertLiftDeleteAndValidation(db);
    } finally {
      Files.deleteIfExists(dbPath);
    }
  }

  private static void assertLiftCreate(SqliteDb db) throws Exception {
    TestExchange create =
        TestExchange.post(
            "/add-lift",
            form(
                "name",
                "Safety Bar Squat",
                "region",
                "LOWER",
                "main",
                "SQUAT",
                "muscles",
                "QUAD, GLUTE",
                "notes",
                "upright"));
    invokeHandler("handleAddLift", create, db);
    assertEquals(303, create.status());
    assertEquals("upright", db.getLift("Safety Bar Squat").notes());
  }

  private static void assertLiftUpdate(SqliteDb db) throws Exception {
    TestExchange update =
        TestExchange.post(
            "/update-lift",
            form(
                "currentName",
                "Safety Bar Squat",
                "name",
                "SSB Squat",
                "region",
                "LOWER",
                "main",
                "SQUAT",
                "muscles",
                "QUAD,HAMSTRING",
                "notes",
                "updated",
                "tab",
                "executions",
                "lastWeekStart",
                "2026-07-20",
                "lastWeekEnd",
                "2026-07-26"));
    invokeHandler("handleUpdateLift", update, db);
    assertEquals(303, update.status());
    assertTrue(update.location().contains("Updated+lift"));
    assertEquals("updated", db.getLift("SSB Squat").notes());
  }

  private static void assertLiftStatusAndViews(SqliteDb db) throws Exception {
    TestExchange disable =
        TestExchange.post(
            "/set-lift-enabled", form("lift", "SSB Squat", "enabled", "0", "tab", "executions"));
    invokeHandler("handleSetLiftEnabled", disable, db);
    assertFalse(db.isLiftEnabled("SSB Squat"));
    assertTrue(disable.location().contains("Disabled+lift"));

    db.setLiftEnabled("SSB Squat", true);
    db.addLiftExecution(
        "SSB Squat",
        new LiftExecution(
            null,
            LocalDate.parse("2026-07-27"),
            List.of(new ExecutionSet(new SetMetric.Reps(3), "315 lb", 8.5f)),
            false,
            false,
            "solid"));

    TestExchange detail = TestExchange.get("/lift?name=SSB+Squat");
    invokeHandler("handleLift", detail, db);
    assertEquals(200, detail.status());
    assertTrue(detail.responseBody().contains("315 lb"));
    assertTrue(detail.responseBody().contains("solid"));

    TestExchange fragment = TestExchange.get("/executions-fragment?lift=SSB+Squat");
    invokeHandler("handleExecutionsFragment", fragment, db);
    assertEquals(200, fragment.status());
    assertTrue(fragment.responseBody().contains("315 lb"));

    TestExchange missingFragment = TestExchange.get("/executions-fragment");
    invokeHandler("handleExecutionsFragment", missingFragment, db);
    assertEquals(400, missingFragment.status());

    TestExchange missingDetail = TestExchange.get("/lift");
    invokeHandler("handleLift", missingDetail, db);
    assertTrue(missingDetail.responseBody().contains("Missing lift name"));
  }

  private static void assertLiftDeleteAndValidation(SqliteDb db) throws Exception {
    TestExchange delete =
        TestExchange.post("/delete-lift", form("lift", "SSB Squat", "tab", "executions"));
    invokeHandler("handleDeleteLift", delete, db);
    assertEquals(303, delete.status());
    assertTrue(delete.location().contains("Deleted+catalog+lift"));

    TestExchange missingName = TestExchange.post("/add-lift", form("name", "", "region", "UPPER"));
    invokeHandler("handleAddLift", missingName, db);
    assertTrue(missingName.location().contains("name%20is%20required"));

    TestExchange invalidMethod = TestExchange.put("/add-lift", "");
    invokeHandler("handleAddLift", invalidMethod, db);
    assertEquals(405, invalidMethod.status());
  }

  @Test
  void loadLastJsonReturnsEverySetAndRespectsTrainingFlags() throws Exception {
    Path dbPath = Files.createTempFile("lifttrax-load-last-json", ".db");
    try (SqliteDb db = new SqliteDb(dbPath.toString())) {
      db.addLift("Carry", LiftRegion.LOWER, LiftType.CONDITIONING, List.of(), "");
      TestExchange missingLift = TestExchange.get("/load-last-execution?format=json");
      LastExecutionResponse.handle(missingLift, db);
      assertEquals(400, missingLift.status());
      TestExchange noPrior = TestExchange.get("/load-last-execution?format=json&lift=Carry");
      LastExecutionResponse.handle(noPrior, db);
      assertEquals(404, noPrior.status());
      assertTrue(noPrior.responseBody().contains("No prior execution"));

      List<ExecutionSet> sets =
          List.of(
              new ExecutionSet(new SetMetric.RepsLr(4, 3), "40|45 kg", 8.0f),
              new ExecutionSet(new SetMetric.Reps(0), "90 lb", 9.5f, true),
              new ExecutionSet(new SetMetric.TimeSecs(45), "red+blue", null),
              new ExecutionSet(new SetMetric.DistanceFeet(100), "none", null));
      db.addLiftExecution(
          "Carry",
          new LiftExecution(null, LocalDate.of(2026, 7, 20), sets, false, false, "Previous notes"));
      db.addLiftExecution(
          "Carry",
          new LiftExecution(
              null,
              LocalDate.of(2026, 7, 21),
              List.of(new ExecutionSet(new SetMetric.Reps(5), "50 lb", null)),
              true,
              true,
              "Warm-up deload"));
      TestExchange loaded = TestExchange.get("/load-last-execution?format=json&lift=Carry");
      LastExecutionResponse.handle(loaded, db);
      assertEquals(200, loaded.status());
      var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(loaded.responseBody());
      assertEquals(4, json.path("sets").size());
      assertEquals("reps-lr", json.path("sets").get(0).path("metricType").asText());
      assertEquals("3", json.path("sets").get(0).path("metricRight").asText());
      assertTrue(json.path("sets").get(1).path("missed").asBoolean());
      assertEquals("0", json.path("sets").get(1).path("metricValue").asText());
      assertEquals("9.5", json.path("sets").get(1).path("rpe").asText());
      assertEquals("time", json.path("sets").get(2).path("metricType").asText());
      assertEquals("distance", json.path("sets").get(3).path("metricType").asText());
      assertEquals("lr", json.path("weight").path("mode").asText());
      assertEquals("kg", json.path("weight").path("lrUnit").asText());
      assertEquals("Previous notes", json.path("notes").asText());
      assertEquals("2026-07-20", json.path("date").asText());
      TestExchange warmup =
          TestExchange.get("/load-last-execution?format=json&lift=Carry&warmup=1&deload=1");
      LastExecutionResponse.handle(warmup, db);
      assertEquals(200, warmup.status());
      assertTrue(warmup.responseBody().contains("Warm-up deload"));
    } finally {
      Files.deleteIfExists(dbPath);
    }
  }

  @Test
  void loadLastRouteCoversMissingPriorAndEveryMetricPrefill() throws Exception {
    Path dbPath = Files.createTempFile("lifttrax-route-load-last-coverage", ".db");
    try (SqliteDb db = new SqliteDb(dbPath.toString())) {
      db.addLift("Carry", LiftRegion.LOWER, LiftType.CONDITIONING, List.of(), "");

      TestExchange missingLift = TestExchange.get("/load-last-execution");
      invokeHandler("handleLoadLastExecution", missingLift, db);
      assertTrue(missingLift.location().contains("Lift%20is%20required"));

      TestExchange noPrior =
          TestExchange.get(
              "/load-last-execution?lift=Carry&date=bad-date&weight=80+lb&setCount=2&notes=keep");
      invokeHandler("handleLoadLastExecution", noPrior, db);
      assertTrue(noPrior.location().contains("No+prior+executions"));
      assertTrue(noPrior.location().contains("prefillWeight=80+lb"));

      List<SetMetric> metrics =
          List.of(
              new SetMetric.Reps(5),
              new SetMetric.RepsLr(4, 3),
              new SetMetric.TimeSecs(45),
              new SetMetric.DistanceFeet(100));
      List<String> expectedTypes = List.of("reps", "reps-lr", "time", "distance");
      for (int i = 0; i < metrics.size(); i++) {
        db.addLiftExecution(
            "Carry",
            new LiftExecution(
                null,
                LocalDate.of(2026, 7, 20 + i),
                List.of(new ExecutionSet(metrics.get(i), "90 lb", 7.5f)),
                i == 1,
                i == 2,
                "loaded"));
        String flags = (i == 1 ? "&warmup=1" : "") + (i == 2 ? "&deload=1" : "");
        TestExchange loaded =
            TestExchange.get("/load-last-execution?lift=Carry&date=2026-07-27" + flags);
        invokeHandler("handleLoadLastExecution", loaded, db);
        assertEquals(303, loaded.status());
        assertTrue(loaded.location().contains("prefillMetricType=" + expectedTypes.get(i)));
        assertTrue(loaded.location().contains("prefillNotes=loaded"));
      }
    } finally {
      Files.deleteIfExists(dbPath);
    }
  }

  @Test
  void indexNavigationAndMutationValidationBranchesRemainRecoverable() throws Exception {
    Path dbPath = Files.createTempFile("lifttrax-route-validation-coverage", ".db");
    try (SqliteDb db = new SqliteDb(dbPath.toString())) {
      db.addLift("Press", LiftRegion.UPPER, LiftType.BENCH_PRESS, List.of(), "");
      for (String navigation : List.of("prev", "next", "current", "last", "unknown")) {
        TestExchange index =
            TestExchange.get(
                "/?tab=last-week&lastWeekStart=bad&lastWeekEnd=bad&waveWeeks=999&lastWeekNav="
                    + navigation);
        invokeHandler("handleIndex", index, db);
        assertEquals(200, index.status());
        assertTrue(index.responseBody().contains("Last Week"));
      }

      TestExchange invalidIndexMethod = TestExchange.put("/", "");
      invokeHandler("handleIndex", invalidIndexMethod, db);
      assertEquals(405, invalidIndexMethod.status());

      TestExchange missingExecutionLift =
          TestExchange.post("/add-execution", form("lift", "", "setCount", "1"));
      invokeHandler("handleAddExecution", missingExecutionLift, db);
      assertTrue(missingExecutionLift.location().contains("Lift%20is%20required"));

      TestExchange invalidExecution =
          TestExchange.post(
              "/add-execution",
              form("lift", "Press", "setCount", "0", "metricType", "reps", "metricValue", "5"));
      invokeHandler("handleAddExecution", invalidExecution, db);
      assertTrue(invalidExecution.location().contains("Failed+to+save+execution"));

      TestExchange missingUpdate =
          TestExchange.post(
              "/update-lift", form("currentName", "", "name", "", "tab", "executions"));
      invokeHandler("handleUpdateLift", missingUpdate, db);
      assertTrue(missingUpdate.location().contains("name%20is%20required"));

      TestExchange missingEnable =
          TestExchange.post("/set-lift-enabled", form("lift", "", "tab", "executions"));
      invokeHandler("handleSetLiftEnabled", missingEnable, db);
      assertTrue(missingEnable.location().contains("Lift%20is%20required"));

      TestExchange unknownEnable =
          TestExchange.post(
              "/set-lift-enabled", form("lift", "Missing", "enabled", "1", "tab", "executions"));
      invokeHandler("handleSetLiftEnabled", unknownEnable, db);
      assertTrue(unknownEnable.location().contains("Failed+to+update+lift+status"));

      TestExchange missingDelete =
          TestExchange.post("/delete-lift", form("lift", "", "tab", "executions"));
      invokeHandler("handleDeleteLift", missingDelete, db);
      assertTrue(missingDelete.location().contains("Lift%20is%20required"));
    } finally {
      Files.deleteIfExists(dbPath);
    }
  }

  @Test
  void plannedWorkoutRoutesCoverPreviewDownloadsResumeAndIdempotentEmptySaves() throws Exception {
    String json =
        Files.readString(Path.of("shared", "workouts", "examples", "conjugate-wave-v2.json"));
    PlannedWorkoutFile workout = PlannedWorkoutJson.readString(json);
    int weekNumber = workout.weeks().get(0).weekNumber();
    String dayOfWeek = workout.weeks().get(0).days().get(0).dayOfWeek();
    Path dbPath = Files.createTempFile("lifttrax-route-planned-workout", ".db");
    try (SqliteDb db = new SqliteDb(dbPath.toString())) {
      assertPlannedPreviewRoutes(db, json, workout);
      assertPlannedStartAndBlockSave(db, json, weekNumber, dayOfWeek);
      assertPlannedSessionSaveAndErrors(db, json, weekNumber, dayOfWeek);
    } finally {
      Files.deleteIfExists(dbPath);
    }
  }

  private static void assertPlannedPreviewRoutes(
      SqliteDb db, String json, PlannedWorkoutFile workout) throws Exception {
    String workoutForm = form("plannedWorkoutJson", json);

    TestExchange preview = TestExchange.post("/planned-workout-preview", workoutForm);
    invokeHandler("handlePlannedWorkoutPreview", preview, db);
    assertEquals(200, preview.status());
    assertTrue(preview.responseBody().contains(workout.metadata().name()));

    TestExchange workAlong = TestExchange.post("/planned-workout-work-along", workoutForm);
    invokeStaticHandler("handlePlannedWorkoutWorkAlong", workAlong);
    assertTrue(workAlong.responseBody().contains("Start Workout"));

    TestExchange print = TestExchange.post("/planned-workout-print", workoutForm);
    invokeHandler("handlePlannedWorkoutPrint", print, db);
    assertEquals(200, print.status());
    assertTrue(print.responseBody().contains("Print"));

    TestExchange markdown = TestExchange.post("/planned-workout-markdown", workoutForm);
    invokeHandler("handlePlannedWorkoutMarkdown", markdown, db);
    assertEquals(200, markdown.status());
    assertTrue(markdown.responseHeaders.getFirst("Content-Disposition").endsWith(".md\""));

    TestExchange downloadJson = TestExchange.post("/planned-workout-json", workoutForm);
    invokeStaticHandler("handlePlannedWorkoutJson", downloadJson);
    assertEquals(200, downloadJson.status());
    assertTrue(downloadJson.responseHeaders.getFirst("Content-Disposition").endsWith(".json\""));

    TestExchange resume = TestExchange.get("/planned-workout-session?draft=draft-key");
    invokeHandler("handlePlannedWorkoutSession", resume, db);
    assertTrue(resume.responseBody().contains("Resume Workout"));
  }

  private static void assertPlannedStartAndBlockSave(
      SqliteDb db, String json, int weekNumber, String dayOfWeek) throws Exception {
    TestExchange start =
        TestExchange.post(
            "/planned-workout-session",
            form(
                "plannedWorkoutJson",
                json,
                "weekNumber",
                String.valueOf(weekNumber),
                "dayOfWeek",
                dayOfWeek));
    invokeHandler("handlePlannedWorkoutSession", start, db);
    assertEquals(200, start.status());
    assertTrue(start.responseBody().contains("Train " + dayOfWeek));

    TestExchange saveBlock =
        TestExchange.post(
            "/save-planned-workout-block",
            form(
                "plannedWorkoutJson",
                json,
                "weekNumber",
                String.valueOf(weekNumber),
                "dayOfWeek",
                dayOfWeek,
                "sessionDate",
                "2026-07-27",
                "sessionResultsJson",
                "[]",
                "submissionId",
                "coverage-block-1"));
    invokeHandler("handleSavePlannedWorkoutBlock", saveBlock, db);
    assertEquals(200, saveBlock.status());
    assertTrue(saveBlock.responseBody().contains("\"loggedExecutionCount\":0"));
  }

  private static void assertPlannedSessionSaveAndErrors(
      SqliteDb db, String json, int weekNumber, String dayOfWeek) throws Exception {
    TestExchange saveSession =
        TestExchange.post(
            "/save-planned-workout-session",
            form(
                "plannedWorkoutJson",
                json,
                "weekNumber",
                String.valueOf(weekNumber),
                "dayOfWeek",
                dayOfWeek,
                "sessionDate",
                "2026-07-27",
                "sessionResultsJson",
                "[]",
                "submissionId",
                "coverage-session-1",
                "savedSessionLoggedCount",
                "2",
                "savedSessionSkippedExercises",
                "bad",
                "savedSessionSkippedSets",
                "-4"));
    invokeHandler("handleSavePlannedWorkoutSession", saveSession, db);
    assertEquals(200, saveSession.status());
    assertTrue(saveSession.responseBody().contains("Workout Saved"));

    TestExchange invalidPreview = TestExchange.post("/planned-workout-preview", "");
    invokeHandler("handlePlannedWorkoutPreview", invalidPreview, db);
    assertTrue(invalidPreview.responseBody().contains("Import Error"));

    TestExchange invalidBlock = TestExchange.post("/save-planned-workout-block", "");
    invokeHandler("handleSavePlannedWorkoutBlock", invalidBlock, db);
    assertEquals(400, invalidBlock.status());
    assertTrue(invalidBlock.responseBody().contains("error"));
  }

  @SuppressWarnings("unchecked")
  @Test
  void serverParsingHelpersCoverBoundsDatesDetailedSetsAndDownloadNames() throws Exception {
    assertEquals(
        7,
        invokePrivate(
            "parseBoundedInt",
            new Class<?>[] {String.class, int.class, int.class, int.class},
            null,
            7,
            1,
            24));
    assertEquals(
        24,
        invokePrivate(
            "parseBoundedInt",
            new Class<?>[] {String.class, int.class, int.class, int.class},
            "99",
            7,
            1,
            24));
    assertEquals(
        7,
        invokePrivate(
            "parseBoundedInt",
            new Class<?>[] {String.class, int.class, int.class, int.class},
            "bad",
            7,
            1,
            24));
    assertEquals(
        LocalDate.parse("2026-07-27"),
        invokePrivate(
            "parseDateOrDefault",
            new Class<?>[] {String.class, LocalDate.class},
            "2026-07-27",
            LocalDate.MIN));
    assertEquals(
        LocalDate.MIN,
        invokePrivate(
            "parseDateOrDefault",
            new Class<?>[] {String.class, LocalDate.class},
            "bad",
            LocalDate.MIN));
    assertEquals(
        List.of(Muscle.QUAD, Muscle.GLUTE),
        invokePrivate("parseMuscles", new Class<?>[] {String.class}, "QUAD, ,GLUTE"));
    assertEquals(
        List.of(), invokePrivate("parseMuscles", new Class<?>[] {String.class}, (Object) null));

    List<ExecutionSet> sets =
        (List<ExecutionSet>)
            invokePrivate(
                "parseDetailedSets",
                new Class<?>[] {String.class},
                """
                [
                  {"metricType":"reps-lr","metricLeft":"4","metricRight":"3","weight":"25 lb","rpe":"8"},
                  {"metricType":"time","metricValue":"30","weight":"","rpe":""},
                  {"metricType":"distance","metricValue":"100","weight":"90 lb","rpe":"7.5"}
                ]
                """);
    assertEquals(3, sets.size());
    assertEquals(new SetMetric.RepsLr(4, 3), sets.get(0).metric());
    assertEquals(
        List.of(), invokePrivate("parseDetailedSets", new Class<?>[] {String.class}, "{}"));
    assertEquals(
        List.of(), invokePrivate("parseDetailedSets", new Class<?>[] {String.class}, "bad-json"));

    String exampleJson =
        Files.readString(Path.of("shared", "workouts", "examples", "conjugate-wave-v2.json"));
    PlannedWorkoutFile workout = PlannedWorkoutJson.readString(exampleJson);
    assertTrue(
        ((String)
                invokePrivate(
                    "downloadName",
                    new Class<?>[] {PlannedWorkoutFile.class, String.class},
                    workout,
                    ".json"))
            .endsWith(".json"));
  }
}
