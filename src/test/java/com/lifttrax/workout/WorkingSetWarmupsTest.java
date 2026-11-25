package com.lifttrax.workout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lifttrax.workout.WorkingSetWarmups.Stage;
import com.lifttrax.workout.WorkingSetWarmups.WarmupSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WorkingSetWarmupsTest {
  @Test
  void templatesUseTheAgreedOneThroughFivePlusTables() {
    List<Stage> singleOrDouble =
        List.of(
            new Stage(40, 5),
            new Stage(60, 3),
            new Stage(75, 2),
            new Stage(85, 1),
            new Stage(92, 1));
    List<Stage> tripleOrFour =
        List.of(new Stage(40, 5), new Stage(60, 3), new Stage(75, 2), new Stage(85, 2));
    List<Stage> fivePlus = List.of(new Stage(40, 5), new Stage(60, 3), new Stage(80, 3));

    assertEquals(singleOrDouble, WorkingSetWarmups.template(1));
    assertEquals(singleOrDouble, WorkingSetWarmups.template(2));
    assertEquals(tripleOrFour, WorkingSetWarmups.template(3));
    assertEquals(tripleOrFour, WorkingSetWarmups.template(4));
    for (int reps : new int[] {5, 6, 10, 20, 100}) {
      assertEquals(fivePlus, WorkingSetWarmups.template(reps));
    }
  }

  @Test
  void agreedFortyFivePoundBarExamplesIncludeRepsAndLateJumpRepairs() {
    assertLadder(95, 3, 45, "45x5", "55x3", "70x2", "80x2");
    assertLadder(135, 4, 45, "45x5", "85x3", "105x2", "115x2");
    assertLadder(180, 4, 45, "65x5", "115x3", "135x2", "155x2");
    assertLadder(225, 4, 45, "95x5", "135x3", "165x2", "195x2");
    assertLadder(250, 3, 45, "95x5", "145x3", "165x2", "185x2", "215x2");
    assertLadder(277, 2, 45, "95x5", "155x3", "185x2", "205x2", "235x1", "255x1");
    assertLadder(280, 2, 45, "95x5", "155x3", "185x2", "205x2", "235x1", "255x1");
    assertLadder(315, 1, 45, "135x5", "185x3", "225x2", "265x1", "290x1");
    assertLadder(365, 1, 45, "135x5", "225x3", "275x2", "315x1", "335x1");
    assertLadder(405, 1, 45, "135x5", "225x3", "275x2", "315x2", "345x1", "375x1");
  }

  @Test
  void fourRep305PoundSquatStartsWithOnePlateInsteadOfPrecisionPlates() {
    assertLadder(305, 4, 45, "135x5", "185x3", "225x2", "255x2", "275x2");
    assertFalse(WarmupLoading.barbell(45).choose(305, 40, 0).precision());
  }

  @Test
  void barWeightChangesTheEasyLoadsWithoutChangingTheWorkTarget() {
    assertLadder(280, 2, 35, "125x5", "175x3", "215x2", "235x1", "255x1");
    var arbitraryWork = WorkingSetWarmups.generate(277, 2, WarmupLoading.barbell(35), false);
    assertTrue(arbitraryWork.sets().stream().allMatch(set -> set.load() % 5 == 0));
    assertTrue(arbitraryWork.sets().stream().allMatch(set -> set.load() < 277));
  }

  @Test
  void toleranceFavorsEasyPlatesAtLowPercentagesAndPrecisionNearWork() {
    WarmupLoading loading = WarmupLoading.barbell(45);

    assertEquals(95, loading.choose(280, 40, 0).load());
    assertEquals(155, loading.choose(280, 60, 0).load());
    assertEquals(205, loading.choose(280, 75, 0).load());
    assertEquals(235, loading.choose(280, 85, 0).load());
    assertEquals(255, loading.choose(280, 92, 0).load());
  }

  @Test
  void lateJumpsAreCappedOnlyForOneThroughFourRepWork() {
    for (double weight : new double[] {95, 135, 180, 225, 250, 277, 280, 315, 365, 405}) {
      for (int reps = 1; reps <= 4; reps++) {
        var result = WorkingSetWarmups.generate(weight, reps, 45, false);
        double threshold = weight * .70;
        double maximum = Math.max(20, Math.min(50, Math.floor(weight * .15 / 5) * 5));
        double previous = result.sets().get(0).load();
        for (int index = 1; index <= result.sets().size(); index++) {
          double next = index == result.sets().size() ? weight : result.sets().get(index).load();
          if (next >= threshold) {
            assertTrue(next - previous <= maximum + 1e-9, weight + "x" + reps + ": " + result);
          }
          previous = next;
        }
        assertTrue(result.finalGapSatisfied(), weight + "x" + reps + ": " + result);
      }
    }

    assertLadder(225, 5, 45, "95x5", "135x3", "185x3");
  }

  @Test
  void alreadyWarmRemovesEarlyStagesButPreservesAValidFinish() {
    var full = WorkingSetWarmups.generate(280, 2, 45, false);
    var shortened = WorkingSetWarmups.generate(280, 2, 45, true);

    assertTrue(shortened.sets().size() < full.sets().size());
    assertEquals(last(full.sets()), last(shortened.sets()));
    assertTrue(shortened.finalGapSatisfied());
  }

  @Test
  void customEquipmentAndFixedIncrementsRemainSupported() {
    var limited =
        new WarmupLoading(
            WarmupLoading.LoadingSystem.BARBELL,
            35,
            5,
            List.of(25.0, 10.0),
            List.of(2.5),
            Map.of(25.0, 2, 10.0, 4, 2.5, 2),
            List.of());
    var limitedResult = WorkingSetWarmups.generate(120, 1, limited, false);
    assertTrue(limitedResult.sets().stream().allMatch(set -> set.load() % 5 == 0));
    assertTrue(limitedResult.sets().stream().allMatch(set -> set.load() < 120));

    var fixed = WorkingSetWarmups.generate(116, 1, 10, 0, false);
    assertTrue(fixed.sets().stream().allMatch(set -> set.load() % 10 == 0));
    assertTrue(fixed.sets().stream().allMatch(set -> set.load() < 116));

    var explicit =
        WorkingSetWarmups.generate(
            100, 5, WarmupLoading.fixedLoads(List.of(20.0, 40.0, 60.0, 80.0)), false);
    assertEquals(80, last(explicit.sets()).load());
  }

  @Test
  void impossibleLoadsRemainBelowWorkAndReportAnUnsatisfiedFinish() {
    var result = WorkingSetWarmups.generate(20, 1, 45, false);
    assertTrue(result.sets().isEmpty());
    assertFalse(result.finalGapSatisfied());

    var fixed = WorkingSetWarmups.generate(17.1, 1, 45, false);
    assertTrue(fixed.sets().stream().allMatch(set -> set.load() < 17.1));
    assertFalse(fixed.finalGapSatisfied());
  }

  @Test
  void rejectsInvalidInputsAndReturnsImmutableResults() {
    for (double invalid :
        new double[] {-1, 0, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}) {
      assertThrows(
          IllegalArgumentException.class, () -> WorkingSetWarmups.generate(invalid, 5, 45, false));
    }
    assertThrows(IllegalArgumentException.class, () -> WorkingSetWarmups.template(0));
    assertThrows(UnsupportedOperationException.class, () -> WorkingSetWarmups.template(5).clear());
    assertThrows(
        UnsupportedOperationException.class,
        () -> WorkingSetWarmups.generate(100, 5, 45, false).sets().clear());
  }

  private static void assertLadder(
      double workingWeight, int workingReps, double barWeight, String... expected) {
    var result =
        WorkingSetWarmups.generate(
            workingWeight, workingReps, WarmupLoading.barbell(barWeight), false);
    assertEquals(
        List.of(expected),
        result.sets().stream().map(WorkingSetWarmupsTest::display).toList(),
        workingWeight + "x" + workingReps + " with " + barWeight + " lb bar");
    assertTrue(result.finalGapSatisfied());
  }

  private static String display(WarmupSet set) {
    return (int) set.load() + "x" + set.reps();
  }

  private static <T> T last(List<T> values) {
    return values.get(values.size() - 1);
  }
}
