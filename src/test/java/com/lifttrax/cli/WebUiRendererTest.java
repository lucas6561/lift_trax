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
import com.lifttrax.workout.PlannedWorkoutFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WebUiRendererTest {

  @Test
  void indexBodyDefaultsToDailyDashboard() throws Exception {
    Path dbPath = Files.createTempFile("lifttrax-dashboard-default", ".db");
    try (SqliteDb db = new SqliteDb(dbPath.toString())) {
      String html =
          WebUiRenderer.renderIndexBody(
              db,
              List.of(),
              "",
              "",
              "",
              "",
              "",
              WebUiRenderer.AddExecutionPrefill.empty(),
              LocalDate.parse("2026-01-01"),
              LocalDate.parse("2026-01-07"),
              1,
              Map.of());

      assertTrue(html.contains("data-initial-tab='dashboard'"));
      assertTrue(html.contains("data-panel='dashboard' data-loaded='true'"));
      assertTrue(html.contains("Today's Training"));
    }
  }

  @Test
  void indexBodyDefersDashboardWhenAnotherTabIsActive() throws Exception {
    Path dbPath = Files.createTempFile("lifttrax-dashboard-deferred", ".db");
    try (SqliteDb db = new SqliteDb(dbPath.toString())) {
      String html =
          WebUiRenderer.renderIndexBody(
              db,
              List.of(),
              "",
              "",
              "add-execution",
              "",
              "",
              WebUiRenderer.AddExecutionPrefill.empty(),
              LocalDate.parse("2026-01-01"),
              LocalDate.parse("2026-01-07"),
              1,
              Map.of());

      assertTrue(html.contains("data-panel='dashboard' data-loaded='false'"));
      assertTrue(html.contains("Open this tab to load Dashboard."));
    }
  }

  @Test
  void dailyDashboardShowsSuggestionsRecentHistoryAndLoggingLinks() throws Exception {
    LocalDate today = LocalDate.of(2026, 5, 30);
    Path dbPath = Files.createTempFile("lifttrax-dashboard", ".db");
    try (SqliteDb db = new SqliteDb(dbPath.toString())) {
      db.addLift("Back Squat", LiftRegion.LOWER, LiftType.SQUAT, List.of(), "");
      db.addLift("Bench Press", LiftRegion.UPPER, LiftType.BENCH_PRESS, List.of(), "");
      db.addLiftExecution("Back Squat", execution(today.minusDays(5), false));
      db.addLiftExecution("Bench Press", execution(today.minusDays(1), false));

      String html = WebUiRenderer.renderDailyDashboard(db, db.listLifts(), today);

      assertTrue(html.contains("Today's Training"));
      assertTrue(html.contains("Suggested Work"));
      assertTrue(html.contains("Recent History"));
      assertTrue(html.contains("Back Squat"));
      assertTrue(html.contains("Bench Press"));
      assertTrue(html.contains("Last: 2026-05-25 - 1 sets x 5 reps @ 225 lb RPE 8.0"));
      assertTrue(
          html.contains(
              "href='/?tab=add-execution&amp;prefillDate=2026-05-30&amp;prefillLift=Back+Squat'"));
      assertTrue(html.contains("href='/lift?name=Back+Squat'"));
      assertTrue(html.contains("Log Again"));
      assertTrue(html.contains("2026-05-29"));
    }
  }

  @Test
  void dailyDashboardUsesLatestExecutionOutsideRecentWindowForSuggestions() throws Exception {
    LocalDate today = LocalDate.of(2026, 5, 30);
    Path dbPath = Files.createTempFile("lifttrax-dashboard-latest", ".db");
    try (SqliteDb db = new SqliteDb(dbPath.toString())) {
      db.addLift("Back Squat", LiftRegion.LOWER, LiftType.SQUAT, List.of(), "");
      db.addLiftExecution("Back Squat", execution(today.minusDays(30), false));

      String html = WebUiRenderer.renderDailyDashboard(db, db.listLifts(), today);

      assertTrue(html.contains("Last: 2026-04-30 - 1 sets x 5 reps @ 225 lb RPE 8.0"));
      assertTrue(html.contains("No executions in the last 14 days."));
    }
  }

  @Test
  void dailyDashboardShowsEmptyStatesWithoutLiftsOrExecutions() throws Exception {
    LocalDate today = LocalDate.of(2026, 5, 30);
    Path dbPath = Files.createTempFile("lifttrax-dashboard-empty", ".db");
    try (SqliteDb db = new SqliteDb(dbPath.toString())) {
      String html = WebUiRenderer.renderDailyDashboard(db, db.listLifts(), today);

      assertTrue(html.contains("No lifts yet"));
      assertTrue(html.contains("Add First Lift"));
      assertTrue(html.contains("No executions in the last 14 days."));
      assertTrue(html.contains("href='/?tab=add-execution&amp;prefillDate=2026-05-30'"));
    }
  }

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
      assertTrue(html.contains("class='execution-row-actions'"));

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
      assertTrue(html.contains("href='/lift?name=Back+Squat'"));
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
      assertTrue(html.contains("class='execution-text'"));
      assertTrue(html.contains("class='execution-edit-meta'"));
      assertTrue(html.contains("class='execution-row-actions'"));
      assertTrue(
          html.contains(
              "<button type='button' class='secondary compact-btn js-exec-edit'>Edit</button>"));
      assertTrue(
          html.contains(
              "<button type='button' class='secondary danger compact-btn js-exec-delete'>Delete</button>"));
      assertTrue(
          html.contains(
              "<button type='button' class='secondary compact-btn js-add-set'>Add Set</button>"));
      assertTrue(
          html.contains(
              "<button type='button' class='secondary compact-btn js-exec-cancel'>Cancel</button>"));
    }
  }

  @Test
  void executionRowsUseSharedMetricFormValuesForEditorRowsAndInitialPayload() throws Exception {
    Path dbPath = Files.createTempFile("lifttrax-exec-rows-metrics", ".db");
    try (SqliteDb db = new SqliteDb(dbPath.toString())) {
      db.addLift("Back Squat", LiftRegion.LOWER, LiftType.SQUAT, List.of(), "");
      db.addLiftExecution(
          "Back Squat",
          new LiftExecution(
              null,
              LocalDate.of(2026, 4, 20),
              List.of(
                  new ExecutionSet(new SetMetric.RepsLr(7, 8), "20 lb", 7.5f),
                  new ExecutionSet(new SetMetric.TimeSecs(30), "none", null),
                  new ExecutionSet(new SetMetric.DistanceFeet(100), "", null)),
              false,
              false,
              ""));

      String html = WebUiRenderer.renderExecutionRows(db, "Back Squat");

      assertTrue(html.contains("<option value='reps-lr' selected>reps-lr</option>"));
      assertTrue(
          html.contains(
              "class='js-set-left' disabled placeholder='left' style='width:80px;' value='7'"));
      assertTrue(
          html.contains(
              "class='js-set-right' disabled placeholder='right' style='width:80px;' value='8'"));
      assertTrue(html.contains("<option value='time' selected>time</option>"));
      assertTrue(html.contains("<option value='distance' selected>distance</option>"));
      assertTrue(html.contains("&quot;metricType&quot;:&quot;reps-lr&quot;"));
      assertTrue(html.contains("&quot;metricLeft&quot;:&quot;7&quot;"));
      assertTrue(html.contains("&quot;metricRight&quot;:&quot;8&quot;"));
      assertTrue(html.contains("&quot;metricType&quot;:&quot;time&quot;"));
      assertTrue(html.contains("&quot;metricValue&quot;:&quot;30&quot;"));
      assertTrue(html.contains("&quot;metricType&quot;:&quot;distance&quot;"));
      assertTrue(html.contains("&quot;metricValue&quot;:&quot;100&quot;"));
      assertTrue(html.contains("&quot;weight&quot;:&quot;20 lb&quot;"));
      assertTrue(html.contains("&quot;rpe&quot;:&quot;7.5&quot;"));
    }
  }

  @Test
  void liftTrendSummaryShowsRecentPerformanceCards() {
    String html =
        WebUiRenderer.renderLiftTrendSummary(
            List.of(
                new LiftExecution(
                    null,
                    LocalDate.of(2026, 5, 20),
                    List.of(new ExecutionSet(new SetMetric.Reps(5), "225 lb", 8.0f)),
                    false,
                    false,
                    "smooth"),
                new LiftExecution(
                    null,
                    LocalDate.of(2026, 5, 25),
                    List.of(new ExecutionSet(new SetMetric.Reps(1), "315 lb", 8.0f)),
                    false,
                    false,
                    "")),
            LocalDate.of(2026, 5, 30));

    assertTrue(html.contains("Recent Trends"));
    assertTrue(html.contains("Last 90 days"));
    assertTrue(html.contains("Last trained"));
    assertTrue(html.contains("Recent best set"));
    assertTrue(html.contains("2026-05-25 - 1 rep @ 315 lb"));
    assertTrue(html.contains("315 lb comparison RPE 8.0"));
    assertTrue(html.contains("2 sessions"));
    assertTrue(html.contains("2 training days"));
    assertTrue(html.contains("2 work sets"));
    assertTrue(html.contains("6 reps / 1,440 lb-reps"));
  }

  @Test
  void liftTrendSummaryShowsHelpfulEmptyAndSparseStates() {
    String empty = WebUiRenderer.renderLiftTrendSummary(List.of(), LocalDate.of(2026, 5, 30));
    String sparse =
        WebUiRenderer.renderLiftTrendSummary(
            List.of(execution(LocalDate.of(2026, 5, 20), false)), LocalDate.of(2026, 5, 30));
    String stale =
        WebUiRenderer.renderLiftTrendSummary(
            List.of(execution(LocalDate.of(2026, 1, 15), false)), LocalDate.of(2026, 5, 30));

    assertTrue(empty.contains("No trend data yet"));
    assertTrue(sparse.contains("Sparse recent history: 1 session so far"));
    assertTrue(sparse.contains("Trends get clearer after three or more sessions."));
    assertTrue(stale.contains("No executions in the last 90 days. Last trained 2026-01-15."));
  }

  @Test
  void plannedWorkoutPageRendersWeeksBlocksAndGroupedTargets() {
    PlannedWorkoutFile.PlannedSetTarget firstSet =
        new PlannedWorkoutFile.PlannedSetTarget(
            1, "reps", 5, null, null, null, null, null, null, 80, null, "CHAINS", false);
    PlannedWorkoutFile.PlannedSetTarget secondSet =
        new PlannedWorkoutFile.PlannedSetTarget(
            2, "reps", 5, null, null, null, null, null, null, 80, null, "CHAINS", false);
    PlannedWorkoutFile workoutFile =
        new PlannedWorkoutFile(
            1,
            new PlannedWorkoutFile.PlannedWorkoutMetadata(
                "Imported Wave", "Ready to train.", 1, List.of("test")),
            new PlannedWorkoutFile.PlannedWorkoutSource(
                "wave-generation", "conjugate", "Conjugate Wave", null, "2026-05-29T00:00:00Z"),
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
                                    "Backoff Sets",
                                    "supplemental",
                                    null,
                                    false,
                                    List.of(
                                        new PlannedWorkoutFile.PlannedExercise(
                                            "Back Squat",
                                            "LOWER",
                                            "SQUAT",
                                            List.of("QUAD"),
                                            List.of(firstSet, secondSet),
                                            "Stay fast.",
                                            List.of("Front Squat"))),
                                    List.of("Use today's top single."))),
                            List.of())))),
            List.of());

    String html = PlannedWorkoutHtml.renderPage(workoutFile);

    assertTrue(html.contains("Imported Wave"));
    assertTrue(html.contains("Back to Import Workout"));
    assertTrue(html.contains("Week 1"));
    assertTrue(html.contains("Monday"));
    assertTrue(html.contains("Backoff Sets"));
    assertTrue(html.contains("2x 5 reps @ 80% CHAINS"));
    assertTrue(html.contains("Swap options: Front Squat"));
    assertFalse(html.contains("action='/planned-workout-session'"));
    assertFalse(html.contains("Start This Day"));
    assertTrue(html.contains("Work Along"));
    assertTrue(html.contains("formaction='/planned-workout-work-along'"));
    assertTrue(html.contains("App Preview"));
    assertTrue(html.contains("Print View"));
    assertTrue(html.contains("Save As Markdown"));
    assertTrue(html.contains("Save As Workout JSON"));

    String workAlongHtml = PlannedWorkoutHtml.renderWorkAlongPage(workoutFile);
    assertTrue(workAlongHtml.contains("<h1>Work Along</h1>"));
    assertTrue(workAlongHtml.contains("Choose the week and training day you want to follow."));
    assertTrue(workAlongHtml.contains("action='/planned-workout-session'"));
    assertTrue(workAlongHtml.contains("name='weekNumber'"));
    assertTrue(workAlongHtml.contains("class='js-workalong-day-choice'"));
    assertTrue(workAlongHtml.contains("name='dayOfWeek' class='js-workalong-day'"));
    assertTrue(workAlongHtml.contains("data-week='1' data-day='MONDAY'"));
    assertTrue(workAlongHtml.contains("Start Workout"));
  }

  @Test
  void plannedWorkoutPageRendersCompactAiGeneratedRepRangesWithoutNullReps() throws Exception {
    PlannedWorkoutFile workoutFile =
        com.lifttrax.workout.PlannedWorkoutJson.readString(
            """
            {
              "schemaVersion": 2,
              "metadata": {"name": "AI Hypertrophy", "description": "", "totalWeeks": 1, "tags": []},
              "source": {
                "kind": "ai",
                "generator": "ChatGPT",
                "programName": "AI Hypertrophy",
                "programSchemaVersion": 2,
                "generatedAt": "2026-06-01T00:00:00Z"
              },
              "weeks": [
                {
                  "weekNumber": 1,
                  "days": [
                    {
                      "dayOfWeek": "MONDAY",
                      "title": "Lower Hypertrophy",
                      "blocks": [
                        {
                          "order": 1,
                          "title": "Main Lift",
                          "blockType": "single",
                          "warmup": false,
                          "exercises": [
                            {
                              "name": "SSB Squat",
                              "region": "LOWER",
                              "type": "SQUAT",
                              "muscles": ["QUAD"],
                              "plannedSets": [
                                {
                                  "setNumber": 1,
                                  "metricType": "reps",
                                  "repsMin": 8,
                                  "repsMax": 10,
                                  "percent": 65,
                                  "deload": false
                                },
                                {
                                  "setNumber": 2,
                                  "metricType": "reps",
                                  "repsMin": 8,
                                  "repsMax": 10,
                                  "percent": 65,
                                  "deload": false
                                }
                              ],
                              "notes": "",
                              "substitutionOptions": []
                            }
                          ],
                          "notes": []
                        }
                      ],
                      "notes": []
                    }
                  ]
                }
              ],
              "completedWorkouts": []
            }
            """);

    String html = PlannedWorkoutHtml.renderPage(workoutFile);

    assertTrue(html.contains("2x 8-10 reps @ 65%"));
    assertFalse(html.contains("null reps"));
  }

  @Test
  void plannedWorkoutPageIncludesMatchingLiftHistory() throws Exception {
    Path dbPath = Files.createTempFile("lifttrax-planned-history", ".db");
    try (SqliteDb db = new SqliteDb(dbPath.toString())) {
      db.addLift("Back Squat", LiftRegion.LOWER, LiftType.SQUAT, List.of(), "");
      db.addLiftExecution(
          "Back Squat",
          new LiftExecution(
              null,
              LocalDate.of(2026, 5, 24),
              List.of(new ExecutionSet(new SetMetric.Reps(5), "275 lb", 8.0f)),
              false,
              false,
              "smooth"));
      db.addLiftExecution(
          "Back Squat",
          new LiftExecution(
              null,
              LocalDate.of(2026, 5, 25),
              List.of(new ExecutionSet(new SetMetric.Reps(1), "365 lb", null)),
              false,
              false,
              ""));

      PlannedWorkoutFile.PlannedSetTarget setTarget =
          new PlannedWorkoutFile.PlannedSetTarget(
              1, "reps", 5, null, null, null, null, null, null, 80, null, "STRAIGHT", false);
      PlannedWorkoutFile.PlannedExercise exercise =
          new PlannedWorkoutFile.PlannedExercise(
              "Back Squat", "LOWER", "SQUAT", List.of("QUAD"), List.of(setTarget), "", List.of());
      PlannedWorkoutFile.PlannedWorkoutBlock block =
          new PlannedWorkoutFile.PlannedWorkoutBlock(
              1, "Backoff Sets", "supplemental", null, false, List.of(exercise), List.of());
      PlannedWorkoutFile.PlannedWorkoutDay day =
          new PlannedWorkoutFile.PlannedWorkoutDay("MONDAY", "Monday", List.of(block), List.of());
      PlannedWorkoutFile workoutFile =
          new PlannedWorkoutFile(
              1,
              new PlannedWorkoutFile.PlannedWorkoutMetadata(
                  "Imported Wave", "Ready to train.", 1, List.of("test")),
              new PlannedWorkoutFile.PlannedWorkoutSource(
                  "wave-generation", "conjugate", "Conjugate Wave", null, "2026-05-29T00:00:00Z"),
              List.of(new PlannedWorkoutFile.PlannedWorkoutWeek(1, List.of(day))),
              List.of());

      String html = PlannedWorkoutHtml.renderPage(workoutFile, db);

      assertTrue(html.contains("<strong>Last:</strong> 1 sets x 5 reps @ 275 lb RPE 8.0 - smooth"));
      assertTrue(html.contains("<strong>Best 1RM:</strong> 365 lb"));
      assertTrue(html.contains("<strong>Suggested:</strong> 295 lb"));
    }
  }

  @Test
  void plannedWorkoutPrintViewIsCompactAndPrintFriendly() throws Exception {
    PlannedWorkoutFile workoutFile =
        com.lifttrax.workout.PlannedWorkoutJson.readPath(
            Path.of("shared", "workouts", "examples", "conjugate-wave-v2.json"));

    String html = PlannedWorkoutPrintHtml.renderPage(workoutFile, null);

    assertTrue(html.contains("onclick='window.print()'"));
    assertTrue(html.contains("@media print"));
    assertTrue(html.contains("@page { size: auto; margin: 0.45in; }"));
    assertTrue(html.contains("background: #fff"));
    assertTrue(html.contains("break-inside: avoid"));
    assertTrue(html.contains("h2 { break-after: avoid-page"));
    assertTrue(html.contains("h3 { break-after: avoid-page"));
    assertFalse(html.contains(".print-day { break-inside: avoid-page"));
    assertTrue(html.contains("class='print-week'"));
    assertTrue(html.contains("class='print-target'"));
    assertFalse(html.contains("data-theme='dark'"));
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
