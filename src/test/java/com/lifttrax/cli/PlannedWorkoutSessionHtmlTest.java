package com.lifttrax.cli;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lifttrax.models.Lift;
import com.lifttrax.models.LiftRegion;
import com.lifttrax.models.LiftType;
import com.lifttrax.workout.PlannedWorkoutFile;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlannedWorkoutSessionHtmlTest {

  @Test
  void sessionPageSeedsPlannedSetsAndAllowsWorkoutFileSwaps() {
    String html =
        PlannedWorkoutSessionHtml.renderPage(
            workoutFile(),
            1,
            "MONDAY",
            List.of(
                lift("Back Squat", LiftType.SQUAT),
                lift("Front Squat", LiftType.SQUAT),
                lift("Farmer Carry", LiftType.CONDITIONING)),
            LocalDate.parse("2026-05-31"));

    assertTrue(html.contains("Train Monday"));
    assertTrue(html.contains("Current block"));
    assertTrue(html.contains("Upcoming work"));
    assertTrue(html.contains("action='/save-planned-workout-session'"));
    assertTrue(html.contains("name='date' value='2026-05-31'"));
    assertTrue(html.contains("data-planned-lift='Back Squat'"));
    assertTrue(html.contains("<option value='Front Squat'>Front Squat</option>"));
    assertTrue(
        html.contains(
            "<option value='Safety Bar Squat' disabled>Safety Bar Squat (not in local lifts)</option>"));
    assertTrue(html.contains("Target: 5 reps @ 80% RPE 8.0"));
    assertTrue(html.contains("class='js-session-metric-value' value='5'"));
    assertTrue(html.contains("<option value='distance' selected>Feet</option>"));
    assertTrue(html.contains("value='100' required"));
    assertTrue(html.contains("value='skipped'>Skipped</option>"));
    assertTrue(html.contains("class='js-session-weight'"));
    assertTrue(html.contains("class='js-session-rpe'"));
    assertTrue(html.contains("class='js-session-exercise-notes'"));
    assertTrue(html.contains("JSON.stringify(results)"));
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
            1, "reps", 5, null, null, null, null, null, null, 80, 8.0f, "STRAIGHT", false);
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

  private static Lift lift(String name, LiftType type) {
    return new Lift(name, LiftRegion.LOWER, type, List.of(), "");
  }
}
