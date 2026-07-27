package com.lifttrax.workout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.lifttrax.db.SqliteDb;
import com.lifttrax.models.Lift;
import com.lifttrax.models.LiftRegion;
import com.lifttrax.models.LiftType;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JOptionPane;
import org.junit.jupiter.api.Test;

class WorkoutConfigurationCoverageTest {

  @Test
  void webConfiguredSourcesSelectNamedLiftsResistanceAndDeloads() throws Exception {
    try (SqliteDb db =
        new SqliteDb(
            Files.createTempDirectory("lifttrax-web-configured")
                .resolve("training.db")
                .toString())) {
      seedMainLifts(db, 5);
      MaxEffortLiftPools pools =
          new MaxEffortLiftPools(
              7,
              db,
              new RandomSupport.Randomizer() {
                @Override
                public <T> void shuffle(List<T> values, java.util.Random random) {}

                @Override
                public int nextInt(java.util.Random random, int bound) {
                  return 0;
                }
              });

      Map<String, String> values = new HashMap<>();
      values.put("meLowerWeek1", "Squat 5");
      values.put("meUpperWeek1", "Bench 5");
      values.put("meLowerWeek2", "Deadlift 5");
      values.put("meUpperWeek2", "Overhead 5");
      values.put("meLowerWeek3", "does not exist");
      values.put("meLowerDeload1Squat", "Squat 4");
      values.put("meLowerDeload1Deadlift", "Deadlift 4");
      values.put("meUpperDeload1Bench", "Bench 4");
      values.put("meUpperDeload1Overhead", "Overhead 4");

      MaxEffortPlan plan = new WebConfiguredMaxEffortPlanSource(7, values).selectPlan(db, pools);
      assertEquals("Squat 5", plan.lower().get(0).name());
      assertEquals("Deadlift 5", plan.lower().get(1).name());
      assertEquals("Squat 1", plan.lower().get(2).name());
      assertEquals("Bench 5", plan.upper().get(0).name());
      assertEquals("Overhead 5", plan.upper().get(1).name());
      assertEquals("Squat 4", plan.lowerDeload().get(0).squat().name());
      assertEquals("Deadlift 4", plan.lowerDeload().get(0).deadlift().name());
      assertEquals("Bench 4", plan.upperDeload().get(0).bench().name());
      assertEquals("Overhead 4", plan.upperDeload().get(0).overhead().name());

      Map<String, String> dynamicValues = new HashMap<>();
      dynamicValues.put("deSquat", "Squat 4");
      dynamicValues.put("deDeadlift", "Deadlift 4");
      dynamicValues.put("deBench", "Bench 4");
      dynamicValues.put("deOverhead", "Overhead 4");
      dynamicValues.put("deSquatAr", "bands");
      dynamicValues.put("deDeadliftAr", "CHAINS");
      dynamicValues.put("deBenchAr", "not-valid");
      dynamicValues.put("deOverheadAr", " ");
      DynamicLifts dynamic = new WebConfiguredDynamicLiftSource(dynamicValues).select(db);
      assertEquals("Squat 4", dynamic.squat().lift().name());
      assertEquals(AccommodatingResistance.BANDS, dynamic.squat().ar());
      assertEquals(AccommodatingResistance.CHAINS, dynamic.deadlift().ar());
      assertEquals("Bench 4", dynamic.bench().lift().name());
      assertEquals("Overhead 4", dynamic.overhead().lift().name());
    }
  }

  @Test
  void maxEffortPlanDerivesEverySevenWeekDeloadWithBothParities() {
    List<Lift> plan = new ArrayList<>();
    for (int i = 1; i <= 14; i++) {
      plan.add(lift("Lift " + i, i % 2 == 0 ? LiftType.DEADLIFT : LiftType.SQUAT));
    }

    MaxEffortPlan complete = MaxEffortPlan.fromDefaults(plan, plan);
    assertEquals(2, complete.lowerDeload().size());
    assertEquals("Lift 7", complete.lowerDeload().get(0).squat().name());
    assertEquals("Lift 6", complete.lowerDeload().get(0).deadlift().name());
    assertEquals("Lift 13", complete.lowerDeload().get(1).squat().name());
    assertEquals("Lift 14", complete.lowerDeload().get(1).deadlift().name());
    assertEquals("Lift 7", complete.upperDeload().get(0).bench().name());
    assertEquals("Lift 14", complete.upperDeload().get(1).overhead().name());

    assertEquals(List.of(), MaxEffortPlan.deriveLowerDeloadFromPlan(plan.subList(0, 6)));
    assertEquals(List.of(), MaxEffortPlan.deriveUpperDeloadFromPlan(plan.subList(0, 6)));
  }

  @Test
  void headlessEditorsReturnDefaultsAndRejectMissingLiftPools() {
    Lift squat = lift("Squat", LiftType.SQUAT);
    Lift deadlift = lift("Deadlift", LiftType.DEADLIFT);
    Lift bench = lift("Bench Press", LiftType.BENCH_PRESS);
    Lift overhead = lift("Overhead Press", LiftType.OVERHEAD_PRESS);

    DynamicLiftSelector.DynamicLiftChoices choices =
        new DynamicLiftSelector.DynamicLiftChoices(squat, deadlift, bench, overhead);
    assertEquals(
        choices,
        DynamicLiftSelector.choose(
            List.of(squat), List.of(deadlift), List.of(bench), List.of(overhead), choices, false));

    MaxEffortPlan edited =
        MaxEffortEditor.editPlan(
            List.of(squat),
            List.of(deadlift),
            List.of(bench),
            List.of(overhead),
            List.of(squat, deadlift),
            List.of(bench, overhead));
    assertEquals(List.of(squat, deadlift), edited.lower());
    assertEquals(List.of(bench, overhead), edited.upper());

    MaxEffortPlan fallback =
        MaxEffortEditor.editPlan(
            List.of(),
            List.of(deadlift),
            List.of(bench),
            List.of(overhead),
            List.of(squat),
            List.of(bench));
    assertEquals(List.of(squat), fallback.lower());
  }

  @Test
  void interactiveEditorsBuildFormsAndReturnSelectedOrDefaultPlans() {
    Lift squat = lift("Squat", LiftType.SQUAT);
    Lift deadlift = lift("Deadlift", LiftType.DEADLIFT);
    Lift bench = lift("Bench Press", LiftType.BENCH_PRESS);
    Lift overhead = lift("Overhead Press", LiftType.OVERHEAD_PRESS);
    List<Lift> lower = List.of(squat, deadlift, squat, deadlift, squat, deadlift, squat);
    List<Lift> upper = List.of(bench, overhead, bench, overhead, bench, overhead, bench);

    MaxEffortPlan selected =
        MaxEffortEditor.editPlanInteractively(
            List.of(squat),
            List.of(deadlift),
            List.of(bench),
            List.of(overhead),
            lower,
            upper,
            (panel, title) -> {
              assertEquals("Max Effort Planner", title);
              assertEquals(7, panel.getComponentCount());
              return JOptionPane.OK_OPTION;
            });
    assertEquals(lower, selected.lower());
    assertEquals(upper, selected.upper());
    assertEquals(1, selected.lowerDeload().size());
    assertEquals(1, selected.upperDeload().size());

    MaxEffortPlan cancelled =
        MaxEffortEditor.editPlanInteractively(
            List.of(squat),
            List.of(deadlift),
            List.of(bench),
            List.of(overhead),
            lower,
            upper,
            (panel, title) -> JOptionPane.CANCEL_OPTION);
    assertEquals(MaxEffortPlan.fromDefaults(lower, upper), cancelled);

    DynamicLiftSelector.DynamicLiftChoices choices =
        new DynamicLiftSelector.DynamicLiftChoices(squat, deadlift, bench, overhead);
    DynamicLiftSelector.DynamicLiftChoices chosen =
        DynamicLiftSelector.chooseInteractively(
            List.of(squat),
            List.of(deadlift),
            List.of(bench),
            List.of(overhead),
            choices,
            (panel, title) -> {
              assertEquals("Dynamic Effort Lifts", title);
              assertEquals(8, panel.getComponentCount());
              return JOptionPane.OK_OPTION;
            });
    assertEquals(choices, chosen);
    assertEquals(
        choices,
        DynamicLiftSelector.chooseInteractively(
            List.of(squat),
            List.of(deadlift),
            List.of(bench),
            List.of(overhead),
            choices,
            (panel, title) -> JOptionPane.CANCEL_OPTION));
  }

  @Test
  void completedWorkoutRecordsNormalizeOptionalValuesAndValidateRequiredValues() {
    PlannedWorkoutFile.CompletedSetResult set =
        new PlannedWorkoutFile.CompletedSetResult(1, "5 reps", null, 8.0f, null);
    PlannedWorkoutFile.CompletedExerciseResult exercise =
        new PlannedWorkoutFile.CompletedExerciseResult(" Squat ", List.of(set), null);
    PlannedWorkoutFile.CompletedWorkout workout =
        new PlannedWorkoutFile.CompletedWorkout(
            " completion-1 ",
            1,
            " Monday ",
            Instant.parse("2026-07-27T12:00:00Z").toString(),
            List.of(exercise),
            null);

    assertEquals("completion-1", workout.completionId());
    assertEquals("Monday", workout.dayOfWeek());
    assertEquals("", workout.notes());
    assertEquals("Squat", workout.exercises().get(0).name());
    assertEquals("", set.weight());
    assertEquals("", set.notes());

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PlannedWorkoutFile.CompletedWorkout(
                "", 1, "Monday", Instant.now().toString(), List.of(), ""));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PlannedWorkoutFile.CompletedWorkout(
                "id", 0, "Monday", Instant.now().toString(), List.of(), ""));
    assertThrows(
        IllegalArgumentException.class,
        () -> new PlannedWorkoutFile.CompletedExerciseResult("", List.of(), ""));
    assertThrows(
        IllegalArgumentException.class,
        () -> new PlannedWorkoutFile.CompletedSetResult(0, "done", "", null, ""));
    assertThrows(
        IllegalArgumentException.class,
        () -> new PlannedWorkoutFile.CompletedSetResult(1, "", "", null, ""));
  }

  private static void seedMainLifts(SqliteDb db, int count) throws Exception {
    for (int i = 1; i <= count; i++) {
      db.addLift("Squat " + i, LiftRegion.LOWER, LiftType.SQUAT, List.of(), "");
      db.addLift("Deadlift " + i, LiftRegion.LOWER, LiftType.DEADLIFT, List.of(), "");
      db.addLift("Bench " + i, LiftRegion.UPPER, LiftType.BENCH_PRESS, List.of(), "");
      db.addLift("Overhead " + i, LiftRegion.UPPER, LiftType.OVERHEAD_PRESS, List.of(), "");
    }
    db.addLift("Squat", LiftRegion.LOWER, LiftType.SQUAT, List.of(), "");
    db.addLift("Deadlift", LiftRegion.LOWER, LiftType.DEADLIFT, List.of(), "");
    db.addLift("Bench Press", LiftRegion.UPPER, LiftType.BENCH_PRESS, List.of(), "");
    db.addLift("Overhead Press", LiftRegion.UPPER, LiftType.OVERHEAD_PRESS, List.of(), "");
  }

  private static Lift lift(String name, LiftType type) {
    return new Lift(
        name,
        type == LiftType.SQUAT || type == LiftType.DEADLIFT ? LiftRegion.LOWER : LiftRegion.UPPER,
        type,
        List.of(),
        "");
  }
}
