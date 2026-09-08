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
  void singleRampEndsWithBridgeAndDoesNotRewarmForBackoffs() {
    PlannedWorkoutFile.PlannedWorkoutDay day =
        day(
            block(
                1,
                "Main Work",
                "main",
                false,
                exercise(
                    "Conventional Deadlift",
                    "DEADLIFT",
                    List.of(target(1, 90, 8.0f), target(5, 75, null)))));
    PlannedWorkoutWarmups.WarmupPlan plan = planFor(day);

    assertEquals("Conventional Deadlift", plan.reference());
    assertEquals(
        List.of(
            "35% of working weight",
            "50% of working weight",
            "65% of working weight",
            "80% of working weight",
            "90% of working weight"),
        plan.sets().stream().map(PlannedWorkoutWarmups.WarmupSet::load).toList());
    assertEquals(
        List.of("5", "3", "2", "1", "1"),
        plan.sets().stream().map(PlannedWorkoutWarmups.WarmupSet::reps).toList());
    assertTrue(plan.backoffNote().contains("do not warm up again"));
    assertEquals(1, plan.workingReps());
    assertTrue(plan.workingWeight().isBlank());
    assertTrue(plan.loadWarning().isBlank());
  }

  @Test
  void templatesDependOnRepsWithoutPercentOrRpeThresholds() {
    for (PlannedWorkoutFile.PlannedSetTarget target :
        List.of(
            target(5, 60, null),
            target(5, 90, null),
            target(5, null, 6.0f),
            target(5, null, 9.0f),
            target(5, null, null))) {
      PlannedWorkoutWarmups.WarmupPlan plan =
          planFor(
              day(
                  block(
                      1,
                      "Main",
                      "main",
                      false,
                      exercise("Bench Press", "BENCH_PRESS", List.of(target)))));
      assertEquals(
          List.of(
              "40% of working weight",
              "60% of working weight", "75% of working weight", "85% of working weight"),
          plan.sets().stream().map(PlannedWorkoutWarmups.WarmupSet::load).toList());
      assertEquals(
          List.of("5", "3", "2", "1"),
          plan.sets().stream().map(PlannedWorkoutWarmups.WarmupSet::reps).toList());
    }
  }

  @Test
  void highRepRampUsesNearestIncrementsOfDisplayedWorkingWeight() throws Exception {
    PlannedWorkoutFile.PlannedExercise exercise =
        exercise("Bench Press", "BENCH_PRESS", List.of(target(12, 100, null)));
    PlannedWorkoutFile.PlannedWorkoutDay day = day(block(1, "Volume", "main", false, exercise));
    Path dbPath = Files.createTempFile("lifttrax-warmup-high-reps", ".db");
    try (SqliteDb db = new SqliteDb(dbPath.toString())) {
      addOneRepMax(db, "Bench Press", LiftType.BENCH_PRESS, 205);
      PlannedWorkoutWarmups.WarmupPlan plan =
          PlannedWorkoutWarmups.forDay(day, PlannedWorkoutHistory.load(db, day)).get("1:0");

      assertEquals("205 lb", plan.workingWeight());
      assertEquals(
          List.of("80 lb", "125 lb"),
          plan.sets().stream().map(PlannedWorkoutWarmups.WarmupSet::suggestedWeight).toList());
      assertEquals(
          List.of("5", "2"),
          plan.sets().stream().map(PlannedWorkoutWarmups.WarmupSet::reps).toList());
    }
  }

  @Test
  void referencedLiftRampUsesDisplayedWorkWeightBeforeNearestRounding() throws Exception {
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

      assertEquals("Conventional Deadlift", plan.reference());
      assertEquals("290 lb", history.suggestedWeight(exercise.name(), target));
      assertEquals("290 lb", plan.workingWeight());
      assertEquals(
          List.of("115 lb", "175 lb", "220 lb", "245 lb"),
          plan.sets().stream().map(PlannedWorkoutWarmups.WarmupSet::suggestedWeight).toList());
    }
  }

  @Test
  void continentalCleanAndPressUsesItsOwnWorkingWeightAndNewRepTemplate() throws Exception {
    PlannedWorkoutFile.PlannedExercise exercise =
        exercise("Continental Clean & Press", "OVERHEAD_PRESS", List.of(target(3, 100, null)));
    PlannedWorkoutFile.PlannedWorkoutDay day =
        day(block(1, "Skill Strength", "main", false, exercise));
    Path dbPath = Files.createTempFile("lifttrax-warmup-continental", ".db");
    try (SqliteDb db = new SqliteDb(dbPath.toString())) {
      addOneRepMax(db, "Overhead Press", LiftType.OVERHEAD_PRESS, 300);
      addOneRepMax(db, exercise.name(), LiftType.OVERHEAD_PRESS, 200);
      PlannedWorkoutWarmups.WarmupPlan plan =
          PlannedWorkoutWarmups.forDay(day, PlannedWorkoutHistory.load(db, day)).get("1:0");

      assertEquals(exercise.name(), plan.reference());
      assertEquals("200 lb", plan.workingWeight());
      assertEquals(
          List.of("80 lb", "110 lb", "140 lb", "170 lb"),
          plan.sets().stream().map(PlannedWorkoutWarmups.WarmupSet::suggestedWeight).toList());
    }
  }

  @Test
  void bridgeAdjustmentIsLabeledWhenNearestRoundingWouldLeaveTooLargeAGap() throws Exception {
    PlannedWorkoutFile.PlannedWorkoutDay day =
        day(
            block(
                1,
                "Single",
                "main",
                false,
                exercise("Overhead Press", "OVERHEAD_PRESS", List.of(target(1, 100, null)))));
    Path dbPath = Files.createTempFile("lifttrax-warmup-bridge", ".db");
    try (SqliteDb db = new SqliteDb(dbPath.toString())) {
      addOneRepMax(db, "Overhead Press", LiftType.OVERHEAD_PRESS, 80);
      PlannedWorkoutWarmups.WarmupPlan plan =
          PlannedWorkoutWarmups.forDay(day, PlannedWorkoutHistory.load(db, day)).get("1:0");

      PlannedWorkoutWarmups.WarmupSet last = plan.sets().get(plan.sets().size() - 1);
      assertEquals("75 lb", last.suggestedWeight());
      assertEquals("1", last.reps());
      assertEquals("Final bridge (adjusted from 90% of working weight)", last.load());
      assertTrue(plan.loadWarning().isBlank());
    }
  }

  @Test
  void impossibleSingleBridgeShowsWarningAndNeverPrescribesWorkingLoad() throws Exception {
    PlannedWorkoutFile.PlannedWorkoutDay day =
        day(
            block(
                1,
                "Single",
                "main",
                false,
                exercise("Overhead Press", "OVERHEAD_PRESS", List.of(target(1, 100, null)))));
    Path dbPath = Files.createTempFile("lifttrax-warmup-small-load", ".db");
    try (SqliteDb db = new SqliteDb(dbPath.toString())) {
      addOneRepMax(db, "Overhead Press", LiftType.OVERHEAD_PRESS, 20);
      PlannedWorkoutWarmups.WarmupPlan plan =
          PlannedWorkoutWarmups.forDay(day, PlannedWorkoutHistory.load(db, day)).get("1:0");

      assertEquals("20 lb", plan.workingWeight());
      assertTrue(plan.loadWarning().contains("cannot meet the final warm-up gap"));
      assertFalse(plan.sets().stream().anyMatch(set -> "20 lb".equals(set.suggestedWeight())));
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
  void onlyFirstWorkOccurrenceGetsRampEvenWithoutAnEarlierSingle() {
    PlannedWorkoutFile.PlannedWorkoutDay day =
        day(
            block(
                1,
                "Main",
                "main",
                false,
                exercise("Bench Press", "BENCH_PRESS", List.of(target(5, 75, null))),
                exercise(" bench press ", "BENCH_PRESS", List.of(target(8, 65, null)))),
            block(
                2,
                "Later",
                "main",
                false,
                exercise("BENCH PRESS", "BENCH_PRESS", List.of(target(12, 60, null)))),
            block(
                3,
                "Other",
                "main",
                false,
                exercise("Back Squat", "SQUAT", List.of(target(7, 70, null)))));
    Map<String, PlannedWorkoutWarmups.WarmupPlan> plans =
        PlannedWorkoutWarmups.forDay(day, PlannedWorkoutHistory.load(null, day));

    assertEquals(2, plans.size());
    assertTrue(plans.containsKey("1:0"));
    assertTrue(plans.containsKey("3:0"));
    assertEquals(
        List.of("5", "3", "1"),
        plans.get("3:0").sets().stream().map(PlannedWorkoutWarmups.WarmupSet::reps).toList());
  }

  @Test
  void earlierPreparationOrExcludedCircuitDoesNotSuppressFirstWorkingRamp() {
    PlannedWorkoutFile.PlannedExercise single =
        exercise("Bench Press", "BENCH_PRESS", List.of(target(1, 40, null)));
    PlannedWorkoutFile.PlannedWorkoutDay day =
        day(
            block(1, "Prep", "warmup", true, single),
            block(2, "Circuit", "circuit", false, single),
            block(
                3,
                "Work",
                "main",
                false,
                exercise("Bench Press", "BENCH_PRESS", List.of(target(5, 75, null)))));
    Map<String, PlannedWorkoutWarmups.WarmupPlan> plans =
        PlannedWorkoutWarmups.forDay(day, PlannedWorkoutHistory.load(null, day));

    assertEquals(1, plans.size());
    assertTrue(plans.containsKey("3:0"));
  }

  @Test
  void laterBackoffGetsNoSecondRampAndShowsBackoffNote() {
    PlannedWorkoutFile.PlannedWorkoutDay day =
        day(
            block(
                1,
                "Single",
                "main",
                false,
                exercise("Bench Press", "BENCH_PRESS", List.of(target(1, 90, null)))),
            block(
                2,
                "Other",
                "main",
                false,
                exercise("Lat Pulldown", "ACCESSORY", List.of(target(8, null, 7.0f)))),
            block(
                3,
                "Backoff",
                "main",
                false,
                exercise("bench press", "BENCH_PRESS", List.of(target(5, 70, null)))));
    Map<String, PlannedWorkoutWarmups.WarmupPlan> plans =
        PlannedWorkoutWarmups.forDay(day, PlannedWorkoutHistory.load(null, day));

    assertEquals(1, plans.size());
    assertTrue(plans.get("1:0").backoffNote().contains("do not warm up again"));
  }

  @Test
  void singleWithoutLaterBackoffDoesNotShowBackoffNote() {
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
  }

  @Test
  void emptyOrNonRepExercisesDoNotConsumeWarmupEligibility() {
    PlannedWorkoutFile.PlannedSetTarget timed =
        new PlannedWorkoutFile.PlannedSetTarget(
            1, "time", null, null, null, null, null, 30, null, null, null, "STRAIGHT", false);
    PlannedWorkoutFile.PlannedWorkoutDay day =
        day(
            block(
                1,
                "Prep",
                "main",
                false,
                exercise("Press", null, List.of()),
                exercise("Press", null, List.of(timed))),
            block(
                2,
                "Work",
                "main",
                false,
                exercise("Press", null, List.of(target(16, null, null)))));
    Map<String, PlannedWorkoutWarmups.WarmupPlan> plans =
        PlannedWorkoutWarmups.forDay(day, PlannedWorkoutHistory.load(null, day));

    assertEquals(1, plans.size());
    assertEquals(
        List.of("35% of working weight", "50% of working weight"),
        plans.get("2:0").sets().stream().map(PlannedWorkoutWarmups.WarmupSet::load).toList());
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
