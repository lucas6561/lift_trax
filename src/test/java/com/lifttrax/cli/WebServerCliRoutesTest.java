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
  void addLiftRouteCreatesTheCatalogEntry() throws Exception {
    try (RouteDbFixture fixture = routeDbFixture("lift-add")) {
      TestExchange exchange =
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

      invokeHandler("handleAddLift", exchange, fixture.db());

      assertEquals(303, exchange.status());
      assertEquals("upright", fixture.db().getLift("Safety Bar Squat").notes());
    }
  }

  @Test
  void updateLiftRouteRenamesTheEntryAndPreservesNavigation() throws Exception {
    try (RouteDbFixture fixture = routeDbFixture("lift-update")) {
      addSsbLift(fixture.db(), "Safety Bar Squat", "upright");
      TestExchange exchange =
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

      invokeHandler("handleUpdateLift", exchange, fixture.db());

      assertEquals(303, exchange.status());
      assertTrue(exchange.location().contains("Updated+lift"));
      assertEquals("updated", fixture.db().getLift("SSB Squat").notes());
    }
  }

  @Test
  void liftStatusRouteDisablesTheSelectedLift() throws Exception {
    try (RouteDbFixture fixture = routeDbFixture("lift-status")) {
      addSsbLift(fixture.db(), "SSB Squat", "updated");
      TestExchange exchange =
          TestExchange.post(
              "/set-lift-enabled", form("lift", "SSB Squat", "enabled", "0", "tab", "executions"));

      invokeHandler("handleSetLiftEnabled", exchange, fixture.db());

      assertFalse(fixture.db().isLiftEnabled("SSB Squat"));
      assertTrue(exchange.location().contains("Disabled+lift"));
    }
  }

  @Test
  void liftDetailRouteRendersExecutionHistory() throws Exception {
    try (RouteDbFixture fixture = routeDbFixture("lift-detail")) {
      addSsbExecution(fixture.db());
      TestExchange exchange = TestExchange.get("/lift?name=SSB+Squat");

      invokeHandler("handleLift", exchange, fixture.db());

      assertEquals(200, exchange.status());
      assertTrue(exchange.responseBody().contains("315 lb"));
      assertTrue(exchange.responseBody().contains("solid"));
    }
  }

  @Test
  void executionFragmentRendersHistoryForTheSelectedLift() throws Exception {
    try (RouteDbFixture fixture = routeDbFixture("execution-fragment")) {
      addSsbExecution(fixture.db());
      TestExchange exchange = TestExchange.get("/executions-fragment?lift=SSB+Squat");

      invokeHandler("handleExecutionsFragment", exchange, fixture.db());

      assertEquals(200, exchange.status());
      assertTrue(exchange.responseBody().contains("315 lb"));
    }
  }

  @Test
  void liftAndExecutionViewsRejectMissingLiftNames() throws Exception {
    try (RouteDbFixture fixture = routeDbFixture("missing-lift")) {
      TestExchange fragment = TestExchange.get("/executions-fragment");
      TestExchange detail = TestExchange.get("/lift");

      invokeHandler("handleExecutionsFragment", fragment, fixture.db());
      invokeHandler("handleLift", detail, fixture.db());

      assertEquals(400, fragment.status());
      assertTrue(detail.responseBody().contains("Missing lift name"));
    }
  }

  @Test
  void deleteLiftRouteRemovesTheCatalogEntry() throws Exception {
    try (RouteDbFixture fixture = routeDbFixture("lift-delete")) {
      addSsbLift(fixture.db(), "SSB Squat", "updated");
      TestExchange exchange =
          TestExchange.post("/delete-lift", form("lift", "SSB Squat", "tab", "executions"));

      invokeHandler("handleDeleteLift", exchange, fixture.db());

      assertEquals(303, exchange.status());
      assertTrue(exchange.location().contains("Deleted+catalog+lift"));
    }
  }

  @Test
  void addLiftRouteRejectsMissingNames() throws Exception {
    try (RouteDbFixture fixture = routeDbFixture("lift-missing-name")) {
      TestExchange exchange = TestExchange.post("/add-lift", form("name", "", "region", "UPPER"));

      invokeHandler("handleAddLift", exchange, fixture.db());

      assertTrue(exchange.location().contains("name%20is%20required"));
    }
  }

  @Test
  void addLiftRouteRejectsUnsupportedMethods() throws Exception {
    try (RouteDbFixture fixture = routeDbFixture("lift-method")) {
      TestExchange exchange = TestExchange.put("/add-lift", "");

      invokeHandler("handleAddLift", exchange, fixture.db());

      assertEquals(405, exchange.status());
    }
  }

  private static void addSsbLift(SqliteDb db, String name, String notes) throws Exception {
    db.addLift(name, LiftRegion.LOWER, LiftType.SQUAT, List.of(Muscle.QUAD), notes);
  }

  private static void addSsbExecution(SqliteDb db) throws Exception {
    addSsbLift(db, "SSB Squat", "updated");
    db.addLiftExecution(
        "SSB Squat",
        new LiftExecution(
            null,
            LocalDate.parse("2026-07-27"),
            List.of(new ExecutionSet(new SetMetric.Reps(3), "315 lb", 8.5f)),
            false,
            false,
            "solid"));
  }

  private static RouteDbFixture routeDbFixture(String name) throws Exception {
    Path path = Files.createTempFile("lifttrax-route-" + name, ".db");
    return new RouteDbFixture(path, new SqliteDb(path.toString()));
  }

  private record RouteDbFixture(Path path, SqliteDb db) implements AutoCloseable {
    @Override
    public void close() throws Exception {
      db.close();
      Files.deleteIfExists(path);
    }
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
  void plannedWorkoutPreviewRendersTheImportedWorkout() throws Exception {
    try (PlannedRouteFixture fixture = plannedRouteFixture()) {
      TestExchange exchange =
          TestExchange.post("/planned-workout-preview", form("plannedWorkoutJson", fixture.json()));

      invokeHandler("handlePlannedWorkoutPreview", exchange, fixture.db());

      assertEquals(200, exchange.status());
      assertTrue(exchange.responseBody().contains(fixture.workout().metadata().name()));
    }
  }

  @Test
  void plannedWorkoutWorkAlongRendersAStartAction() throws Exception {
    PlannedRouteFixture fixture = plannedRouteData();
    TestExchange exchange =
        TestExchange.post(
            "/planned-workout-work-along", form("plannedWorkoutJson", fixture.json()));

    invokeStaticHandler("handlePlannedWorkoutWorkAlong", exchange);

    assertTrue(exchange.responseBody().contains("Start Workout"));
  }

  @Test
  void plannedWorkoutPrintRendersPrintableContent() throws Exception {
    try (PlannedRouteFixture fixture = plannedRouteFixture()) {
      TestExchange exchange =
          TestExchange.post("/planned-workout-print", form("plannedWorkoutJson", fixture.json()));

      invokeHandler("handlePlannedWorkoutPrint", exchange, fixture.db());

      assertEquals(200, exchange.status());
      assertTrue(exchange.responseBody().contains("Print"));
    }
  }

  @Test
  void plannedWorkoutDownloadsUseTheExpectedExtensions() throws Exception {
    try (PlannedRouteFixture fixture = plannedRouteFixture()) {
      String workoutForm = form("plannedWorkoutJson", fixture.json());
      TestExchange markdown = TestExchange.post("/planned-workout-markdown", workoutForm);
      TestExchange json = TestExchange.post("/planned-workout-json", workoutForm);

      invokeHandler("handlePlannedWorkoutMarkdown", markdown, fixture.db());
      invokeStaticHandler("handlePlannedWorkoutJson", json);

      assertEquals(200, markdown.status());
      assertTrue(markdown.responseHeaders.getFirst("Content-Disposition").endsWith(".md\""));
      assertEquals(200, json.status());
      assertTrue(json.responseHeaders.getFirst("Content-Disposition").endsWith(".json\""));
    }
  }

  @Test
  void plannedWorkoutResumeRouteRendersTheDraftChooser() throws Exception {
    try (PlannedRouteFixture fixture = plannedRouteFixture()) {
      TestExchange exchange = TestExchange.get("/planned-workout-session?draft=draft-key");

      invokeHandler("handlePlannedWorkoutSession", exchange, fixture.db());

      assertTrue(exchange.responseBody().contains("Resume Workout"));
    }
  }

  @Test
  void plannedWorkoutSessionRouteStartsTheSelectedDay() throws Exception {
    try (PlannedRouteFixture fixture = plannedRouteFixture()) {
      TestExchange exchange =
          TestExchange.post("/planned-workout-session", selectedWorkoutForm(fixture));

      invokeHandler("handlePlannedWorkoutSession", exchange, fixture.db());

      assertEquals(200, exchange.status());
      assertTrue(exchange.responseBody().contains("Train " + fixture.dayOfWeek()));
    }
  }

  @Test
  void plannedWorkoutBlockSaveIsIdempotentForAnEmptyBlock() throws Exception {
    try (PlannedRouteFixture fixture = plannedRouteFixture()) {
      TestExchange exchange =
          TestExchange.post(
              "/save-planned-workout-block",
              form(
                  "plannedWorkoutJson",
                  fixture.json(),
                  "weekNumber",
                  String.valueOf(fixture.weekNumber()),
                  "dayOfWeek",
                  fixture.dayOfWeek(),
                  "sessionDate",
                  "2026-07-27",
                  "sessionResultsJson",
                  "[]",
                  "submissionId",
                  "coverage-block-1"));

      invokeHandler("handleSavePlannedWorkoutBlock", exchange, fixture.db());

      assertEquals(200, exchange.status());
      assertTrue(exchange.responseBody().contains("\"loggedExecutionCount\":0"));
    }
  }

  @Test
  void plannedWorkoutSessionSaveCombinesEarlierBlockTotals() throws Exception {
    try (PlannedRouteFixture fixture = plannedRouteFixture()) {
      TestExchange exchange =
          TestExchange.post(
              "/save-planned-workout-session",
              form(
                  "plannedWorkoutJson",
                  fixture.json(),
                  "weekNumber",
                  String.valueOf(fixture.weekNumber()),
                  "dayOfWeek",
                  fixture.dayOfWeek(),
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

      invokeHandler("handleSavePlannedWorkoutSession", exchange, fixture.db());

      assertEquals(200, exchange.status());
      assertTrue(exchange.responseBody().contains("Workout Saved"));
    }
  }

  @Test
  void plannedWorkoutRoutesRejectMissingPayloads() throws Exception {
    try (PlannedRouteFixture fixture = plannedRouteFixture()) {
      TestExchange preview = TestExchange.post("/planned-workout-preview", "");
      TestExchange block = TestExchange.post("/save-planned-workout-block", "");

      invokeHandler("handlePlannedWorkoutPreview", preview, fixture.db());
      invokeHandler("handleSavePlannedWorkoutBlock", block, fixture.db());

      assertTrue(preview.responseBody().contains("Import Error"));
      assertEquals(400, block.status());
      assertTrue(block.responseBody().contains("error"));
    }
  }

  @Test
  void boundedIntegerParsingUsesFallbacksAndClampsHighValues() {
    assertEquals(7, WebExecutionRequestParser.parseBoundedInt(null, 7, 1, 24));
    assertEquals(24, WebExecutionRequestParser.parseBoundedInt("99", 7, 1, 24));
    assertEquals(7, WebExecutionRequestParser.parseBoundedInt("bad", 7, 1, 24));
  }

  @Test
  void dateParsingUsesTheFallbackForInvalidInput() {
    assertEquals(
        LocalDate.parse("2026-07-27"),
        WebExecutionRequestParser.parseDateOrDefault("2026-07-27", LocalDate.MIN));
    assertEquals(LocalDate.MIN, WebExecutionRequestParser.parseDateOrDefault("bad", LocalDate.MIN));
  }

  @Test
  void muscleParsingIgnoresBlankTokensAndNullInput() {
    assertEquals(List.of(Muscle.QUAD, Muscle.GLUTE), WebCatalogRoutes.parseMuscles("QUAD, ,GLUTE"));
    assertEquals(List.of(), WebCatalogRoutes.parseMuscles(null));
  }

  @Test
  void detailedSetParsingPreservesMetricKinds() {
    List<ExecutionSet> sets =
        WebExecutionRequestParser.parseDetailedSets(
            """
            [
              {"metricType":"reps-lr","metricLeft":"4","metricRight":"3","weight":"25 lb","rpe":"8"},
              {"metricType":"time","metricValue":"30","weight":"","rpe":""},
              {"metricType":"distance","metricValue":"100","weight":"90 lb","rpe":"7.5"}
            ]
            """);

    assertEquals(3, sets.size());
    assertEquals(new SetMetric.RepsLr(4, 3), sets.get(0).metric());
  }

  @Test
  void detailedSetParsingRejectsObjectsAndMalformedJson() {
    assertEquals(List.of(), WebExecutionRequestParser.parseDetailedSets("{}"));
    assertEquals(List.of(), WebExecutionRequestParser.parseDetailedSets("bad-json"));
  }

  @Test
  void plannedWorkoutDownloadNamesPreserveTheRequestedExtension() throws Exception {
    PlannedRouteFixture fixture = plannedRouteData();

    assertTrue(WebWorkoutRoutes.downloadName(fixture.workout(), ".json").endsWith(".json"));
  }

  private static String selectedWorkoutForm(PlannedRouteFixture fixture) {
    return form(
        "plannedWorkoutJson",
        fixture.json(),
        "weekNumber",
        String.valueOf(fixture.weekNumber()),
        "dayOfWeek",
        fixture.dayOfWeek());
  }

  private static PlannedRouteFixture plannedRouteFixture() throws Exception {
    PlannedRouteFixture data = plannedRouteData();
    Path path = Files.createTempFile("lifttrax-route-planned-workout", ".db");
    return new PlannedRouteFixture(
        data.json(),
        data.workout(),
        data.weekNumber(),
        data.dayOfWeek(),
        path,
        new SqliteDb(path.toString()));
  }

  private static PlannedRouteFixture plannedRouteData() throws Exception {
    String json =
        Files.readString(Path.of("shared", "workouts", "examples", "conjugate-wave-v2.json"));
    PlannedWorkoutFile workout = PlannedWorkoutJson.readString(json);
    return new PlannedRouteFixture(
        json,
        workout,
        workout.weeks().get(0).weekNumber(),
        workout.weeks().get(0).days().get(0).dayOfWeek(),
        null,
        null);
  }

  private record PlannedRouteFixture(
      String json,
      PlannedWorkoutFile workout,
      int weekNumber,
      String dayOfWeek,
      Path path,
      SqliteDb db)
      implements AutoCloseable {
    @Override
    public void close() throws Exception {
      if (db != null) {
        db.close();
      }
      if (path != null) {
        Files.deleteIfExists(path);
      }
    }
  }
}
