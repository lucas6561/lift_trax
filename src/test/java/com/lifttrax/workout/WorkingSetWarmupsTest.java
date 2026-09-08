package com.lifttrax.workout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lifttrax.workout.WorkingSetWarmups.Stage;
import com.lifttrax.workout.WorkingSetWarmups.WarmupSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkingSetWarmupsTest {
  @Test
  void templatesCoverEveryRepBandBoundary() {
    assertEquals(
        List.of(
            new Stage(35, 5),
            new Stage(50, 3),
            new Stage(65, 2),
            new Stage(80, 1),
            new Stage(90, 1)),
        WorkingSetWarmups.template(1));
    for (int reps : new int[] {2, 3}) {
      assertEquals(
          List.of(new Stage(40, 5), new Stage(55, 3), new Stage(70, 2), new Stage(85, 1)),
          WorkingSetWarmups.template(reps));
    }
    for (int reps : new int[] {4, 6}) {
      assertEquals(
          List.of(new Stage(40, 5), new Stage(60, 3), new Stage(75, 2), new Stage(85, 1)),
          WorkingSetWarmups.template(reps));
    }
    for (int reps : new int[] {7, 10}) {
      assertEquals(
          List.of(new Stage(40, 5), new Stage(60, 3), new Stage(75, 1)),
          WorkingSetWarmups.template(reps));
    }
    for (int reps : new int[] {11, 15}) {
      assertEquals(List.of(new Stage(40, 5), new Stage(60, 2)), WorkingSetWarmups.template(reps));
    }
    for (int reps : new int[] {16, 30, Integer.MAX_VALUE}) {
      assertEquals(List.of(new Stage(35, 5), new Stage(50, 2)), WorkingSetWarmups.template(reps));
    }
  }

  @Test
  void generatesTheFourWorkedExamplesFromWorkingWeight() {
    assertRamp(
        405,
        1,
        new WarmupSet(140, 5, 35),
        new WarmupSet(205, 3, 50),
        new WarmupSet(265, 2, 65),
        new WarmupSet(325, 1, 80),
        new WarmupSet(365, 1, 90));
    assertRamp(
        315,
        3,
        new WarmupSet(125, 5, 40),
        new WarmupSet(175, 3, 55),
        new WarmupSet(220, 2, 70),
        new WarmupSet(270, 1, 85));
    assertRamp(
        220,
        5,
        new WarmupSet(90, 5, 40),
        new WarmupSet(130, 3, 60),
        new WarmupSet(165, 2, 75),
        new WarmupSet(185, 1, 85));
    assertRamp(205, 12, new WarmupSet(80, 5, 40), new WarmupSet(125, 2, 60));
  }

  @Test
  void roundsHalfUpAndMergesCollapsedLoadsUsingFewerReps() {
    assertEquals(
        List.of(new WarmupSet(10, 3, 60), new WarmupSet(15, 1, 85)),
        WorkingSetWarmups.generate(20, 5).sets());
    assertEquals(205, WorkingSetWarmups.generate(405, 1).sets().get(1).load());
    assertEquals(List.of(new WarmupSet(5, 2, 60)), WorkingSetWarmups.generate(10, 12).sets());
  }

  @Test
  void prunesSmallIntermediateStepsButKeepsTheFinalSingle() {
    assertEquals(
        List.of(new WarmupSet(40, 5, 35), new WarmupSet(70, 2, 65), new WarmupSet(90, 1, 90)),
        WorkingSetWarmups.generate(100, 1, 10, 0, false).sets());
  }

  @Test
  void repairsTheFinalGapAfterRounding() {
    WorkingSetWarmups.Result result = WorkingSetWarmups.generate(116, 1, 10, 0, false);
    assertTrue(result.finalGapSatisfied());
    assertEquals(new WarmupSet(110, 1, 90), result.sets().get(result.sets().size() - 1));
  }

  @Test
  void preservesFinalSingleWhenNominalBridgeRoundsAboveTheCeiling() {
    for (boolean alreadyWarm : new boolean[] {false, true}) {
      WorkingSetWarmups.Result result = WorkingSetWarmups.generate(20.75, 5, 5, 0, alreadyWarm);
      assertEquals(List.of(new WarmupSet(10, 3, 60), new WarmupSet(15, 1, 75)), result.sets());
      assertTrue(result.finalGapSatisfied());
    }
  }

  @Test
  void reportsImpossibleFinalGapsWithoutAddingUnsafeLoads() {
    WorkingSetWarmups.Result result = WorkingSetWarmups.generate(17.1, 1);
    assertFalse(result.finalGapSatisfied());
    assertEquals(15, result.sets().get(result.sets().size() - 1).load());
    assertTrue(result.sets().stream().allMatch(set -> set.load() <= 17.1 * 0.95));
    assertEquals(new WorkingSetWarmups.Result(List.of(), false), WorkingSetWarmups.generate(1, 1));
    assertEquals(
        new WorkingSetWarmups.Result(List.of(), false),
        WorkingSetWarmups.generate(100, 5, 5, 100, false));
  }

  @Test
  void roundsMinimumUpAndAddsEntryOnlyWhenSufficientlyLight() {
    WorkingSetWarmups.Result result = WorkingSetWarmups.generate(220, 5, 5, 43, false);
    assertEquals(new WarmupSet(45, 8, 0), result.sets().get(0));
    assertEquals(new WarmupSet(90, 5, 40), result.sets().get(1));
    assertEquals(
        List.of(new WarmupSet(45, 3, 60), new WarmupSet(55, 2, 75), new WarmupSet(60, 1, 85)),
        WorkingSetWarmups.generate(70, 5, 5, 43, false).sets());
    assertTrue(
        WorkingSetWarmups.generate(220, 5, 5, 46, false).sets().stream()
            .noneMatch(set -> set.percent() == 0));
  }

  @Test
  void alreadyWarmRemovesUpToTwoLightStagesAndPreservesIntermediateAndBridge() {
    WorkingSetWarmups.Result single = WorkingSetWarmups.generate(405, 1, 5, 45, true);
    assertEquals(
        List.of(new WarmupSet(265, 2, 65), new WarmupSet(325, 1, 80), new WarmupSet(365, 1, 90)),
        single.sets());
    assertTrue(single.finalGapSatisfied());
    assertEquals(
        List.of(new WarmupSet(60, 3, 60), new WarmupSet(75, 2, 75), new WarmupSet(85, 1, 85)),
        WorkingSetWarmups.generate(100, 5, 5, 0, true).sets());
    assertEquals(2, WorkingSetWarmups.generate(100, 20, 5, 0, true).sets().size());
    assertEquals(2, WorkingSetWarmups.generate(20, 5, 5, 0, true).sets().size());
  }

  @Test
  void rejectsInvalidInputs() {
    for (double invalid :
        new double[] {-1, 0, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}) {
      assertThrows(IllegalArgumentException.class, () -> WorkingSetWarmups.generate(invalid, 5));
      assertThrows(
          IllegalArgumentException.class,
          () -> WorkingSetWarmups.generate(100, 5, invalid, 0, false));
    }
    for (double invalid :
        new double[] {-1, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}) {
      assertThrows(
          IllegalArgumentException.class,
          () -> WorkingSetWarmups.generate(100, 5, 5, invalid, false));
    }
    for (int invalid : new int[] {0, -1}) {
      assertThrows(IllegalArgumentException.class, () -> WorkingSetWarmups.template(invalid));
      assertThrows(IllegalArgumentException.class, () -> WorkingSetWarmups.generate(100, invalid));
    }
  }

  @Test
  void returnedListsAreImmutable() {
    assertThrows(UnsupportedOperationException.class, () -> WorkingSetWarmups.template(5).clear());
    assertThrows(
        UnsupportedOperationException.class,
        () -> WorkingSetWarmups.generate(100, 5).sets().clear());
  }

  @Test
  void generatedLoadsStaySelectableOrderedAndBelowWorkAcrossRepBandsAndIncrements() {
    int[] repBands = {1, 2, 3, 4, 6, 7, 10, 11, 15, 16, 25};
    double[] minimumFractions = {0.88, 0.82, 0.82, 0.72, 0.72, 0.65, 0.65, 0.50, 0.50, 0.40, 0.40};
    for (double increment : new double[] {0.5, 2.5, 5, 10}) {
      for (double weight : new double[] {1, 6, 12, 17.1, 25, 52, 116, 205, 405}) {
        for (double minimum : new double[] {0, 7, 45}) {
          for (boolean alreadyWarm : new boolean[] {false, true}) {
            for (int band = 0; band < repBands.length; band++) {
              WorkingSetWarmups.Result result =
                  WorkingSetWarmups.generate(
                      weight, repBands[band], increment, minimum, alreadyWarm);
              double previous = 0;
              for (WarmupSet set : result.sets()) {
                assertTrue(Double.isFinite(set.load()));
                assertTrue(set.load() > previous);
                assertTrue(set.load() >= minimum);
                assertTrue(set.load() < weight);
                assertTrue(set.load() <= weight * 0.95);
                assertEquals(Math.rint(set.load() / increment), set.load() / increment, 1e-9);
                assertTrue(set.reps() > 0);
                previous = set.load();
              }
              assertEquals(
                  !result.sets().isEmpty() && previous >= weight * minimumFractions[band],
                  result.finalGapSatisfied());
            }
          }
        }
      }
    }
  }

  private static void assertRamp(double weight, int reps, WarmupSet... expected) {
    WorkingSetWarmups.Result result = WorkingSetWarmups.generate(weight, reps);
    assertEquals(List.of(expected), result.sets());
    assertTrue(result.finalGapSatisfied());
  }
}
