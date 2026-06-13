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
import org.junit.jupiter.api.Test;

class PlannedWorkoutSessionHtmlTest {

  @Test
  void sessionPageStartsWithEmptyCompletedSetsAndAllowsWorkoutFileSwaps() {
    String html =
        PlannedWorkoutSessionHtml.renderPage(
            workoutFile(),
            1,
            "MONDAY",
            List.of(
                lift("Back Squat", LiftType.SQUAT, "Brace hard and sit between the hips."),
                lift("Front Squat", LiftType.SQUAT, "Keep elbows high."),
                lift("Farmer Carry", LiftType.CONDITIONING)),
            LocalDate.parse("2026-05-31"));

    assertTrue(html.contains("Train Monday"));
    assertTrue(html.contains("aria-label='Workout block navigation'"));
    assertTrue(html.contains("Block 1 of 2"));
    assertTrue(html.contains("data-session-block-index='0'"));
    assertTrue(html.contains("data-session-block-index='1'"));
    assertTrue(html.contains("class='session-block is-hidden' data-session-block-index='1'"));
    assertTrue(html.contains("js-session-next-block"));
    assertTrue(html.contains("showBlock(currentBlockIndex + 1)"));
    assertTrue(html.contains("class='save-workout-session-btn is-hidden'"));
    assertTrue(html.contains("action='/save-planned-workout-session'"));
    assertTrue(html.contains("name='sessionDate' value='2026-05-31'"));
    assertTrue(html.contains("data-planned-lift='Back Squat'"));
    assertTrue(html.contains(">Change lift</button>"));
    assertTrue(html.contains("<optgroup label='Planned and recommended'>"));
    assertTrue(html.contains("<optgroup label='Other lifts in your library'>"));
    assertTrue(
        html.contains(
            "Choose any lift from your library. Workout-approved alternatives are listed first."));
    assertTrue(
        html.contains(
            "<option value='Front Squat' data-lift-note='Keep elbows high.'>Front Squat</option>"));
    assertTrue(html.contains("data-lift-note='Brace hard and sit between the hips.'"));
    assertTrue(
        html.contains(
            "<strong>Lift note:</strong> <span>Brace hard and sit between the hips.</span>"));
    assertTrue(html.contains("selected.dataset.liftNote"));
    assertTrue(
        html.contains(
            "<option value='Safety Bar Squat' data-lift-note='' disabled>Safety Bar Squat (not in local lifts)</option>"));
    assertTrue(html.contains("Changed to: ${performedLift.value}"));
    assertTrue(html.contains("Target: 5 reps @ 80%"));
    assertTrue(html.contains("session-execution-widget js-session-execution-input"));
    assertTrue(html.contains("name='metricValue' value='5'"));
    assertTrue(html.contains("class='js-weight-hidden' value=''"));
    assertTrue(html.contains("name='rpe' value=''"));
    assertTrue(html.contains("class='js-detailed-sets' value='[]'"));
    assertTrue(html.contains("data-draft-key='lifttrax:planned-session:"));
    assertTrue(
        html.contains(
            "name='setEntryMode-1-0' value='individual' checked data-control-name='setEntryMode'"));
    assertTrue(html.contains("Multiple matching sets"));
    assertTrue(html.contains("Individual set log"));
    assertTrue(html.contains("Add Set to Log"));
    assertTrue(html.contains("class='individual-sets-details' open"));
    assertTrue(
        html.contains(
            "name='metricType-2-0' value='distance' checked data-control-name='metricType'"));
    assertTrue(html.contains("name='metricValue' value='100'"));
    assertTrue(html.contains("value='skipped'>Skipped</option>"));
    assertTrue(html.contains("class='js-weight-hidden'"));
    assertTrue(html.contains("name='rpe'"));
    assertTrue(html.contains("name='notes'"));
    assertTrue(html.contains("addSetBtn.click();"));
    assertTrue(html.contains("Array.from({length: setCount}"));
    assertTrue(html.contains("const widgetRadioState = new WeakMap();"));
    assertTrue(html.contains("if (state[name])"));
    assertTrue(html.contains("rememberRadioValue(widget, 'setEntryMode', mode);"));
    assertTrue(html.contains("localStorage.setItem(draftKey, JSON.stringify(draft));"));
    assertTrue(html.contains("restoreDraft();"));
    assertTrue(html.contains("currentBlockIndex"));
    assertTrue(html.contains("detailedSets.length > 0 || setEntryMode(widget) === 'individual'"));
    assertTrue(html.contains("event.preventDefault();"));
    assertTrue(html.contains("JSON.stringify(results)"));
    assertFalse(html.contains("name='notes' value='Stay fast.'"));
    assertFalse(html.contains("name='rpe' value='8.0'"));
    assertFalse(html.contains("class='session-history'"));
  }

  @Test
  void sessionPageUsesIndependentRadioGroupsForEachExerciseInABlock() {
    String html =
        PlannedWorkoutSessionHtml.renderPage(
            circuitWorkoutFile(),
            1,
            "MONDAY",
            List.of(
                lift("Back Squat", LiftType.SQUAT),
                lift("Overhead Press", LiftType.OVERHEAD_PRESS)),
            LocalDate.parse("2026-05-31"));

    assertTrue(
        html.contains(
            "name='setEntryMode-1-0' value='individual' checked data-control-name='setEntryMode'"));
    assertTrue(
        html.contains(
            "name='setEntryMode-1-1' value='individual' checked data-control-name='setEntryMode'"));
    assertTrue(
        html.contains("name='metricType-1-0' value='reps' checked data-control-name='metricType'"));
    assertTrue(
        html.contains("name='metricType-1-1' value='reps' checked data-control-name='metricType'"));
    assertTrue(html.contains("input[data-control-name='setEntryMode'][value='${mode}']"));
    assertFalse(html.contains("input[name='setEntryMode']:checked"));
  }

  @Test
  void sessionPageSeedsPercentWeightFromBestOneRepMax() throws Exception {
    Path dbPath = Files.createTempFile("lifttrax-follow-session-seed", ".db");
    try (SqliteDb db = new SqliteDb(dbPath.toString())) {
      db.addLift("Back Squat", LiftRegion.LOWER, LiftType.SQUAT, List.of(), "");
      db.addLiftExecution(
          "Back Squat",
          new LiftExecution(
              null,
              LocalDate.parse("2026-05-29"),
              List.of(new ExecutionSet(new SetMetric.Reps(5), "275 lb", 8.0f)),
              false,
              false,
              "smooth"));
      db.addLiftExecution(
          "Back Squat",
          new LiftExecution(
              null,
              LocalDate.parse("2026-05-30"),
              List.of(new ExecutionSet(new SetMetric.Reps(1), "365 lb", null)),
              false,
              false,
              ""));

      String html =
          PlannedWorkoutSessionHtml.renderPage(
              workoutFile(),
              1,
              "MONDAY",
              List.of(
                  lift("Back Squat", LiftType.SQUAT),
                  lift("Front Squat", LiftType.SQUAT),
                  lift("Farmer Carry", LiftType.CONDITIONING)),
              LocalDate.parse("2026-05-31"),
              db);

      assertTrue(html.contains("Target: 5 reps @ 80%"));
      assertTrue(html.contains("Suggested: 295 lb"));
      assertTrue(html.contains("class='js-weight-hidden' value='295 lb'"));
      assertTrue(html.contains("name='rpe' value=''"));
      assertTrue(html.contains("class='session-history' aria-label='Exercise history'"));
      assertTrue(html.contains("<strong>Last:</strong> 1 sets x 5 reps @ 275 lb RPE 8.0 - smooth"));
      assertTrue(html.contains("<strong>Best 1RM:</strong> 365 lb"));
    }
  }

  @Test
  void sessionPageUsesRpeTableWithHistoricalFallbackWhenPercentIsMissing() throws Exception {
    Path dbPath = Files.createTempFile("lifttrax-follow-session-rpe-seed", ".db");
    try (SqliteDb db = new SqliteDb(dbPath.toString())) {
      db.addLift("Back Squat", LiftRegion.LOWER, LiftType.SQUAT, List.of(), "");
      db.addLiftExecution(
          "Back Squat",
          new LiftExecution(
              null,
              LocalDate.parse("2026-05-30"),
              List.of(new ExecutionSet(new SetMetric.Reps(5), "275 lb", 8.0f)),
              false,
              false,
              ""));

      String html =
          PlannedWorkoutSessionHtml.renderPage(
              rpeOnlyWorkoutFile(),
              1,
              "MONDAY",
              List.of(lift("Back Squat", LiftType.SQUAT)),
              LocalDate.parse("2026-05-31"),
              db);

      assertTrue(html.contains("Target: 5 reps RPE 6.5"));
      assertTrue(html.contains("Suggested: 265 lb"));
      assertTrue(html.contains("class='js-weight-hidden' value='265 lb'"));
      assertTrue(html.contains("name='rpe' value=''"));
    }
  }

  @Test
  void savedPageSummarizesLoggedSwappedAndSkippedWork() {
    String html =
        PlannedWorkoutSessionHtml.renderSavedPage(
            workoutFile(),
            1,
            "MONDAY",
            LocalDate.parse("2026-05-31"),
            new PlannedWorkoutSessionService.SaveSummary(
                List.of(new PlannedWorkoutSessionService.LoggedExercise("Front Squat", 2, true)),
                1,
                2));

    assertTrue(html.contains("Workout Saved"));
    assertTrue(html.contains("1 execution logged for Monday on 2026-05-31"));
    assertTrue(html.contains("Front Squat"));
    assertTrue(html.contains("(swapped exercise)"));
    assertTrue(html.contains("Skipped 1 exercise and 2 sets"));
    assertTrue(html.contains("Back to Dashboard"));
  }

  private static PlannedWorkoutFile workoutFile() {
    PlannedWorkoutFile.PlannedSetTarget squat =
        new PlannedWorkoutFile.PlannedSetTarget(
            1, "reps", 5, null, null, null, null, null, null, 80, null, "STRAIGHT", false);
    PlannedWorkoutFile.PlannedSetTarget carry =
        new PlannedWorkoutFile.PlannedSetTarget(
            1, "distance_feet", null, null, null, null, null, null, 100, null, null, null, false);
    PlannedWorkoutFile.PlannedExercise backSquat =
        new PlannedWorkoutFile.PlannedExercise(
            "Back Squat",
            "LOWER",
            "SQUAT",
            List.of("QUAD"),
            List.of(squat),
            "Stay fast.",
            List.of("Front Squat", "Safety Bar Squat"));
    PlannedWorkoutFile.PlannedExercise farmerCarry =
        new PlannedWorkoutFile.PlannedExercise(
            "Farmer Carry",
            "LOWER",
            "CONDITIONING",
            List.of("CORE"),
            List.of(carry),
            "",
            List.of());
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

  private static PlannedWorkoutFile rpeOnlyWorkoutFile() {
    PlannedWorkoutFile.PlannedSetTarget squat =
        new PlannedWorkoutFile.PlannedSetTarget(
            1, "reps", 5, null, null, null, null, null, null, null, 6.5f, "STRAIGHT", false);
    PlannedWorkoutFile.PlannedExercise backSquat =
        new PlannedWorkoutFile.PlannedExercise(
            "Back Squat", "LOWER", "SQUAT", List.of("QUAD"), List.of(squat), "", List.of());
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
                                List.of())),
                        List.of())))),
        List.of());
  }

  private static PlannedWorkoutFile circuitWorkoutFile() {
    PlannedWorkoutFile.PlannedSetTarget squat =
        new PlannedWorkoutFile.PlannedSetTarget(
            1, "reps", 5, null, null, null, null, null, null, 80, null, "STRAIGHT", false);
    PlannedWorkoutFile.PlannedSetTarget press =
        new PlannedWorkoutFile.PlannedSetTarget(
            1, "reps", 10, null, null, null, null, null, null, null, null, "STRAIGHT", false);
    PlannedWorkoutFile.PlannedExercise backSquat =
        new PlannedWorkoutFile.PlannedExercise(
            "Back Squat", "LOWER", "SQUAT", List.of("QUAD"), List.of(squat), "", List.of());
    PlannedWorkoutFile.PlannedExercise overheadPress =
        new PlannedWorkoutFile.PlannedExercise(
            "Overhead Press",
            "UPPER",
            "OVERHEAD_PRESS",
            List.of("SHOULDER"),
            List.of(press),
            "",
            List.of());
    return new PlannedWorkoutFile(
        2,
        new PlannedWorkoutFile.PlannedWorkoutMetadata(
            "Circuit Wave", "Ready to train.", 1, List.of()),
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
                                "Circuit",
                                "circuit",
                                2,
                                false,
                                List.of(backSquat, overheadPress),
                                List.of())),
                        List.of())))),
        List.of());
  }

  private static Lift lift(String name, LiftType type) {
    return new Lift(name, LiftRegion.LOWER, type, List.of(), "");
  }

  private static Lift lift(String name, LiftType type, String notes) {
    return new Lift(name, LiftRegion.LOWER, type, List.of(), notes);
  }
}
