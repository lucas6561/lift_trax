package com.lifttrax.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lifttrax.db.SqliteDb;
import com.lifttrax.models.ExecutionSet;
import com.lifttrax.models.LiftExecution;
import com.lifttrax.models.LiftRegion;
import com.lifttrax.models.LiftType;
import com.lifttrax.models.SetMetric;
import com.lifttrax.workout.PlannedWorkoutFile;
import com.lifttrax.workout.PlannedWorkoutHistory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PlannedWorkoutWarmupsTest {

  @Test
  void singleRampUsesGuideAndShowsNoRewarmRuleForBackoffs() {
    PlannedWorkoutFile.PlannedExercise deadlift =
        exercise(
            "Conventional Deadlift", "DEADLIFT", List.of(target(1, 90, 8.0f), target(5, 75, null)));
    PlannedWorkoutFile.PlannedWorkoutDay day = day(block(1, "Main Work", "main", false, deadlift));

    PlannedWorkoutWarmups.WarmupPlan plan = planFor(day);

    assertEquals("Conventional Deadlift", plan.reference());
    assertEquals(
        List.of(
            "Lightest practical load",
            "40% of working weight",
            "55% of working weight",
            "70% of working weight",
            "80% of working weight"),
        plan.sets().stream().map(PlannedWorkoutWarmups.WarmupSet::load).toList());
    assertEquals(
        List.of("8-10", "5", "3", "2", "1"),
        plan.sets().stream().map(PlannedWorkoutWarmups.WarmupSet::reps).toList());
    assertTrue(plan.backoffNote().contains("do not warm up again"));
    assertTrue(plan.backoffNote().contains("60-65% of the working weight"));
    assertFalse(plan.backoffNote().contains("about"));
  }

  @Test
  void twoAndFiveRepRampsAddTheGuideThresholdSingles() {
    PlannedWorkoutWarmups.WarmupPlan bench =
        planFor(
            day(
                block(
                    1,
                    "Bench Practice",
                    "main",
                    false,
                    exercise("Bench Press", "BENCH_PRESS", List.of(target(2, 80, null))))));
    PlannedWorkoutWarmups.WarmupPlan squat =
        planFor(
            day(
                block(
                    1,
                    "Strength Volume",
                    "main",
                    false,
                    exercise("Back Squat", "SQUAT", List.of(target(5, 75, null))))));

    assertEquals("Empty bar", bench.sets().get(0).load());
    assertTrue(bench.sets().stream().anyMatch(set -> set.load().startsWith("75%")));
    assertEquals("Lightest practical load", squat.sets().get(0).load());
    assertTrue(squat.sets().stream().anyMatch(set -> set.load().startsWith("70%")));
  }

  @Test
  void lowerPercentAndLowRpeWorkDoNotAddThresholdSingles() {
    PlannedWorkoutWarmups.WarmupPlan twoReps =
        planFor(
            day(
                block(
                    1,
                    "Practice",
                    "main",
                    false,
                    exercise("Bench Press", "BENCH_PRESS", List.of(target(2, 79, null))))));
    PlannedWorkoutWarmups.WarmupPlan rpeWork =
        planFor(
            day(
                block(
                    1,
                    "Volume",
                    "main",
                    false,
                    exercise("Overhead Press", "OVERHEAD_PRESS", List.of(target(6, null, 6.0f))))));

    assertFalse(twoReps.sets().stream().anyMatch(set -> set.load().startsWith("75%")));
    assertFalse(rpeWork.sets().stream().anyMatch(set -> set.load().startsWith("70%")));
    assertEquals("Empty bar / lightest practical load", rpeWork.sets().get(0).load());
  }

  @Test
  void rpeDerivedWorkPercentageAddsTheThresholdSingle() {
    PlannedWorkoutWarmups.WarmupPlan plan =
        planFor(
            day(
                block(
                    1,
                    "Volume",
                    "main",
                    false,
                    exercise("Overhead Press", "OVERHEAD_PRESS", List.of(target(5, null, 7.0f))))));

    assertTrue(plan.sets().stream().anyMatch(set -> set.load().startsWith("70%")));
  }

  @Test
  void highRepRampUsesFractionsOfTheExpectedWorkWeight() throws Exception {
    PlannedWorkoutFile.PlannedExercise exercise =
        exercise("Bench Press", "BENCH_PRESS", List.of(target(10, 70, null)));
    PlannedWorkoutFile.PlannedWorkoutDay day = day(block(1, "Volume", "main", false, exercise));
    Path dbPath = Files.createTempFile("lifttrax-warmup-high-reps", ".db");
    try (SqliteDb db = new SqliteDb(dbPath.toString())) {
      addOneRepMax(db, "Bench Press", LiftType.BENCH_PRESS, 365);

      Map<String, PlannedWorkoutWarmups.WarmupPlan> plans =
          PlannedWorkoutWarmups.forDay(day, PlannedWorkoutHistory.load(db, day));
      PlannedWorkoutWarmups.WarmupPlan plan = plans.get("1:0");

      assertEquals("Very light load", plan.sets().get(0).load());
      assertEquals("About 50% of working weight", plan.sets().get(1).load());
      assertEquals("130 lb", plan.sets().get(1).suggestedWeight());
      assertEquals("About 70% of working weight", plan.sets().get(2).load());
      assertEquals("185 lb", plan.sets().get(2).suggestedWeight());
    }
  }

  @Test
  void referencedLiftRampUsesFractionsOfTheRecommendedWorkingWeight() throws Exception {
    PlannedWorkoutFile.PlannedSetTarget target = referencedTarget(4, 65, "Conventional Deadlift");
    PlannedWorkoutFile.PlannedExercise exercise =
        exercise("Paused Deadlift", "DEADLIFT", List.of(target));
    PlannedWorkoutFile.PlannedWorkoutDay day =
        day(block(1, "Paused Pull Strength", "supplemental", false, exercise));
    Path dbPath = Files.createTempFile("lifttrax-warmup-referenced-work", ".db");
    try (SqliteDb db = new SqliteDb(dbPath.toString())) {
      addOneRepMax(db, "Conventional Deadlift", LiftType.DEADLIFT, 440);

      PlannedWorkoutHistory.Snapshot history = PlannedWorkoutHistory.load(db, day);
      PlannedWorkoutWarmups.WarmupPlan plan = PlannedWorkoutWarmups.forDay(day, history).get("1:0");

      assertEquals("40% of working weight", plan.sets().get(1).load());
      assertEquals("290 lb", history.suggestedWeight(exercise.name(), target));
      assertEquals("120 lb", plan.sets().get(1).suggestedWeight());
      assertEquals("160 lb", plan.sets().get(2).suggestedWeight());
      assertEquals("190 lb", plan.sets().get(3).suggestedWeight());
    }
  }

  @Test
  void continentalCleanAndPressUsesTheOverheadPressReference() throws Exception {
    PlannedWorkoutFile.PlannedExercise exercise =
        exercise("Continental Clean & Press", "OVERHEAD_PRESS", List.of(target(3, null, 7.0f)));
    PlannedWorkoutFile.PlannedWorkoutDay day =
        day(block(1, "Skill Strength", "main", false, exercise));
    Path dbPath = Files.createTempFile("lifttrax-warmup-continental", ".db");
    try (SqliteDb db = new SqliteDb(dbPath.toString())) {
      addOneRepMax(db, "Overhead Press", LiftType.OVERHEAD_PRESS, 300);

      PlannedWorkoutWarmups.WarmupPlan plan =
          PlannedWorkoutWarmups.forDay(day, PlannedWorkoutHistory.load(db, day)).get("1:0");

      assertTrue(plan.title().contains("if needed"));
      assertEquals("Overhead Press", plan.reference());
      assertEquals("105 lb", plan.sets().get(1).suggestedWeight());
      assertEquals("130 lb", plan.sets().get(2).suggestedWeight());
      assertTrue(plan.intent().contains("deliberate"));
    }
  }

  @Test
  void skipsPreparationCircuitsAccessoriesAndConditioning() {
    PlannedWorkoutFile.PlannedWorkoutDay day =
        day(
            block(
                1,
                "Preparation Circuit",
                "warmup",
                true,
                exercise("Bird Dog", "CORE", List.of(target(10, null, 6.0f)))),
            block(
                2,
                "Accessories",
                "accessory",
                false,
                exercise("Curl", "ACCESSORY", List.of(target(10, null, 7.0f)))),
            block(
                3,
                "Conditioning",
                "conditioning",
                false,
                exercise("Bike", "CONDITIONING", List.of(target(10, null, 7.0f)))),
            block(
                4,
                "Loaded Circuit",
                "circuit",
                false,
                exercise("Row", "ROW", List.of(target(10, null, 7.0f)))),
            block(
                5,
                "Main Work",
                "main",
                false,
                exercise("Back Squat", "SQUAT", List.of(target(5, 70, null))),
                exercise("Triceps Extension", "ACCESSORY", List.of(target(10, null, 7.0f)))));

    Map<String, PlannedWorkoutWarmups.WarmupPlan> plans =
        PlannedWorkoutWarmups.forDay(day, PlannedWorkoutHistory.load(null, day));

    assertEquals(1, plans.size());
    assertTrue(plans.containsKey("5:0"));
  }

  @Test
  void everyNonExcludedStrengthLiftGetsARampIncludingSevenRepWork() {
    PlannedWorkoutFile.PlannedWorkoutDay day =
        day(
            block(
                1,
                "Unsupported",
                "main",
                false,
                exercise("Press", "OVERHEAD_PRESS", List.of(target(7, 70, null)))),
            block(
                2,
                "Later Work",
                "main",
                false,
                exercise("Back Squat", "SQUAT", List.of(target(5, 70, null)))));

    Map<String, PlannedWorkoutWarmups.WarmupPlan> plans =
        PlannedWorkoutWarmups.forDay(day, PlannedWorkoutHistory.load(null, day));

    assertEquals(2, plans.size());
    assertEquals("Very light load", plans.get("1:0").sets().get(0).load());
    assertTrue(plans.containsKey("2:0"));
  }

  @Test
  void separateLaterExerciseTriggersTheNoRewarmBackoffNote() {
    PlannedWorkoutFile.PlannedExercise single =
        exercise("Bench Press", "BENCH_PRESS", List.of(target(1, 90, null)));
    PlannedWorkoutFile.PlannedExercise other =
        exercise("Lat Pulldown", "ACCESSORY", List.of(target(8, null, 7.0f)));
    PlannedWorkoutFile.PlannedExercise backoff =
        exercise("bench press", "BENCH_PRESS", List.of(target(5, 70, null)));
    PlannedWorkoutFile.PlannedWorkoutDay day =
        day(
            block(1, "Single", "main", false, single),
            block(2, "Other", "main", false, other),
            block(3, "Backoff", "main", false, backoff));

    Map<String, PlannedWorkoutWarmups.WarmupPlan> plans =
        PlannedWorkoutWarmups.forDay(day, PlannedWorkoutHistory.load(null, day));

    assertEquals(1, plans.size());
    assertTrue(plans.get("1:0").backoffNote().contains("do not warm up again"));
  }

  @Test
  void singleWithoutLaterBackoffDoesNotShowTheNoRewarmRule() {
    PlannedWorkoutFile.PlannedWorkoutDay day =
        day(
            block(
                1,
                "Single",
                "main",
                false,
                exercise("Bench Press", "BENCH_PRESS", List.of(target(1, 90, null)))),
            block(2, "Other", "main", false, exercise("Row", "ROW", List.of(target(5, 70, null)))));

    Map<String, PlannedWorkoutWarmups.WarmupPlan> plans =
        PlannedWorkoutWarmups.forDay(day, PlannedWorkoutHistory.load(null, day));

    assertEquals(2, plans.size());
    assertTrue(plans.get("1:0").backoffNote().isBlank());
    assertTrue(plans.containsKey("2:0"));
  }

  @Test
  void strengthLiftWithoutPercentOrRpeStillGetsARamp() {
    PlannedWorkoutFile.PlannedWorkoutDay day =
        day(
            block(
                1,
                "Main Work",
                "main",
                false,
                exercise("Back Squat", "SQUAT", List.of(target(5, null, null)))));

    PlannedWorkoutWarmups.WarmupPlan plan = planFor(day);

    assertEquals("40% of working weight", plan.sets().get(1).load());
  }

  private static PlannedWorkoutWarmups.WarmupPlan planFor(
      PlannedWorkoutFile.PlannedWorkoutDay day) {
    return PlannedWorkoutWarmups.forDay(day, PlannedWorkoutHistory.load(null, day))
        .values()
        .iterator()
        .next();
  }

  private static PlannedWorkoutFile.PlannedWorkoutDay day(
      PlannedWorkoutFile.PlannedWorkoutBlock... blocks) {
    return new PlannedWorkoutFile.PlannedWorkoutDay("MONDAY", "Monday", List.of(blocks), List.of());
  }

  private static PlannedWorkoutFile.PlannedWorkoutBlock block(
      int order,
      String title,
      String type,
      boolean warmup,
      PlannedWorkoutFile.PlannedExercise... exercises) {
    return new PlannedWorkoutFile.PlannedWorkoutBlock(
        order, title, type, null, warmup, List.of(exercises), List.of());
  }

  private static PlannedWorkoutFile.PlannedExercise exercise(
      String name, String type, List<PlannedWorkoutFile.PlannedSetTarget> targets) {
    return new PlannedWorkoutFile.PlannedExercise(
        name, "UPPER", type, List.of(), targets, "", List.of());
  }

  private static PlannedWorkoutFile.PlannedSetTarget target(int reps, Integer percent, Float rpe) {
    return new PlannedWorkoutFile.PlannedSetTarget(
        1, "reps", reps, null, null, null, null, null, null, percent, rpe, "STRAIGHT", false);
  }

  private static PlannedWorkoutFile.PlannedSetTarget referencedTarget(
      int reps, int percent, String reference) {
    return new PlannedWorkoutFile.PlannedSetTarget(
        1,
        "reps",
        reps,
        null,
        null,
        null,
        null,
        null,
        null,
        percent,
        reference,
        null,
        8.0f,
        "STRAIGHT",
        null,
        false);
  }

  private static void addOneRepMax(SqliteDb db, String name, LiftType type, int pounds)
      throws Exception {
    db.addLift(name, LiftRegion.UPPER, type, List.of(), "");
    db.addLiftExecution(
        name,
        new LiftExecution(
            null,
            LocalDate.parse("2026-08-28"),
            List.of(new ExecutionSet(new SetMetric.Reps(1), pounds + " lb", 9.0f)),
            false,
            false,
            ""));
  }
}
