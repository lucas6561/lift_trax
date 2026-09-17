package com.lifttrax.workout;

import static org.junit.jupiter.api.Assertions.*;

import com.lifttrax.workout.WorkingSetWarmups.Stage;
import com.lifttrax.workout.WorkingSetWarmups.WarmupSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WorkingSetWarmupsTest {
  @Test
  void templatesCoverEveryRepBandBoundary() {
    assertEquals(
        List.of(
            new Stage(35, 5),
            new Stage(55, 3),
            new Stage(70, 2),
            new Stage(82, 1),
            new Stage(92, 1)),
        WorkingSetWarmups.template(1));
    assertEquals(
        List.of(new Stage(35, 5), new Stage(55, 3), new Stage(70, 2), new Stage(87, 1)),
        WorkingSetWarmups.template(2));
    assertEquals(
        List.of(new Stage(35, 5), new Stage(55, 3), new Stage(70, 2), new Stage(85, 2)),
        WorkingSetWarmups.template(3));
    for (int reps : new int[] {4, 5, 6}) {
      assertEquals(
          List.of(
              new Stage(35, 5), new Stage(55, 4), new Stage(70, 3), new Stage(80, (reps + 1) / 2)),
          WorkingSetWarmups.template(reps));
    }
    for (int reps : new int[] {7, 8, 9, 10}) {
      assertEquals(
          List.of(new Stage(35, 6), new Stage(55, 5), new Stage(75, (reps + 1) / 2)),
          WorkingSetWarmups.template(reps));
    }
    for (int reps : new int[] {11, 15}) {
      assertEquals(
          List.of(new Stage(35, 8), new Stage(55, 6), new Stage(70, 5)),
          WorkingSetWarmups.template(reps));
    }
    for (int reps : new int[] {16, 20}) {
      assertEquals(
          List.of(new Stage(30, 8), new Stage(50, 6), new Stage(65, 5)),
          WorkingSetWarmups.template(reps));
    }
    for (int reps : new int[] {21, Integer.MAX_VALUE}) {
      assertEquals(
          List.of(new Stage(27.5, 10), new Stage(47.5, 6), new Stage(60, 5)),
          WorkingSetWarmups.template(reps));
    }
  }

  @Test
  void heavySinglesHaveCloseAccurateBridgesEvenWhenAlreadyWarm() {
    for (double weight : new double[] {395, 405}) {
      for (boolean warm : new boolean[] {false, true}) {
        var result = WorkingSetWarmups.generate(weight, 1, WarmupLoading.barbell(), warm);
        var bridge = last(result.sets());
        assertTrue(result.finalGapSatisfied());
        assertTrue(bridge.load() >= weight * .90);
        assertTrue(weight - bridge.load() <= weight * .10);
        assertTrue(Math.abs(bridge.load() - weight * .92) <= 2.5);
        assertEquals(1, bridge.reps());
        assertEquals(warm ? 3 : 5, result.sets().size());
      }
    }
  }

  @Test
  void workedExamplesRetainMeaningfulMultiRepBridges() {
    assertBridge(250, 5, 3, .77, .83);
    assertBridge(300, 10, 5, .70, .80);
    assertBridge(180, 12, 5, .65, .75);
    for (int reps = 4; reps <= 6; reps++) {
      assertBridge(405, reps, (reps + 1) / 2, .77, .83);
    }
    assertEquals(
        List.of(new WarmupSet(30, 8, 30), new WarmupSet(50, 6, 50), new WarmupSet(65, 5, 65)),
        WorkingSetWarmups.generate(100, 20).sets());
  }

  @Test
  void plateSelectionPrefersSimpleEarlyLoadsAndPreciseHighLoads() {
    var loading = WarmupLoading.barbell();
    var first = loading.choose(250, 35, 0, WarmupLoading.Candidate.empty());
    assertEquals(95, first.load());
    assertEquals(Map.of(25.0, 1), first.plates());
    assertFalse(first.precision());
    var previous = new WarmupLoading.Candidate(175, Map.of(45.0, 1, 10.0, 2), false);
    var bridge = loading.choose(250, 80, 192.5, previous);
    assertEquals(195, bridge.load());
    assertFalse(bridge.precision());
    assertEquals(325, loading.choose(395, 82, 0, previous).load());
    assertTrue(loading.choose(400, 92, 360, previous).precision());
    var added = new WarmupLoading.Candidate(195, Map.of(45.0, 1, 10.0, 3), false);
    assertEquals(1, added.changeCost(previous));
    assertEquals(1.5, previous.changeCost(added));
  }

  @Test
  void duplicateAndTinyStagesKeepLaterRepsAndFinalBridge() {
    assertEquals(
        List.of(new WarmupSet(5, 5, 35), new WarmupSet(10, 4, 55), new WarmupSet(15, 3, 80)),
        WorkingSetWarmups.generate(20, 5).sets());
    var tiny =
        WorkingSetWarmups.generate(
            100, 5, WarmupLoading.fixedLoads(List.of(35.0, 55.0, 76.0, 80.0)), false);
    assertEquals(List.of(35.0, 55.0, 80.0), tiny.sets().stream().map(WarmupSet::load).toList());
    assertEquals(3, last(tiny.sets()).reps());
    var light = WorkingSetWarmups.generate(50, 5, WarmupLoading.barbell(), false);
    assertEquals(List.of(new WarmupSet(45, 3, 80)), light.sets());
    assertTrue(light.finalGapSatisfied());
  }

  @Test
  void repairsRoundingAndReportsUnavailableBridges() {
    var repaired = WorkingSetWarmups.generate(116, 1, 10, 0, false);
    assertEquals(new WarmupSet(110, 1, 92), last(repaired.sets()));
    assertTrue(repaired.finalGapSatisfied());
    // The v2 ceiling is strictly below work; a bridge above the old 95% ceiling is allowed.
    assertTrue(WorkingSetWarmups.generate(51, 1).finalGapSatisfied());
    var impossible = WorkingSetWarmups.generate(17.1, 1);
    assertFalse(impossible.finalGapSatisfied());
    assertEquals(new WarmupSet(15, 1, 92), last(impossible.sets()));
    assertEquals(new WorkingSetWarmups.Result(List.of(), false), WorkingSetWarmups.generate(1, 1));
    assertEquals(
        List.of(), WorkingSetWarmups.generate(45, 5, WarmupLoading.barbell(), false).sets());
    assertEquals(List.of(), WorkingSetWarmups.generate(100, 5, 5, 100, false).sets());
  }

  @Test
  void supportsInventoryCustomBarFixedLoadsAndFreeform() {
    var limited =
        new WarmupLoading(
            WarmupLoading.LoadingSystem.BARBELL,
            35,
            5,
            List.of(25.0, 10.0),
            List.of(2.5),
            Map.of(25.0, 2, 10.0, 3, 2.5, 2),
            List.of());
    var result = WorkingSetWarmups.generate(120, 1, limited, false);
    assertEquals(110, last(result.sets()).load());
    assertTrue(result.finalGapSatisfied());
    assertTrue(
        result.sets().stream()
            .allMatch(
                set ->
                    List.of(35.0, 40.0, 55.0, 60.0, 85.0, 90.0, 105.0, 110.0)
                        .contains(set.load())));
    var fixed =
        WorkingSetWarmups.generate(
            100, 1, WarmupLoading.fixedLoads(List.of(20.0, 40.0, 60.0, 80.0, 95.0, 100.0)), false);
    assertEquals(95, last(fixed.sets()).load());
    assertTrue(fixed.finalGapSatisfied());
    assertFalse(
        WorkingSetWarmups.generate(
                100, 1, WarmupLoading.fixedLoads(List.of(20.0, 50.0, 80.0)), false)
            .finalGapSatisfied());
    assertEquals(
        List.of(),
        WorkingSetWarmups.generate(10, 5, WarmupLoading.fixedLoads(List.of(10.0, 20.0)), false)
            .sets());
    var free = WorkingSetWarmups.generate(100, 21, WarmupLoading.freeform(), false);
    assertEquals(
        List.of(
            new WarmupSet(27.5, 10, 27.5), new WarmupSet(47.5, 6, 47.5), new WarmupSet(60, 5, 60)),
        free.sets());
    assertTrue(free.finalGapSatisfied());
  }

  @Test
  void alreadyWarmPreservesIntermediateAndFinalBridge() {
    for (int reps : new int[] {1, 2, 3, 4, 5, 6, 7, 10, 12, 20, 21}) {
      var full = WorkingSetWarmups.generate(250, reps, WarmupLoading.barbell(), false);
      var warm = WorkingSetWarmups.generate(250, reps, WarmupLoading.barbell(), true);
      assertTrue(warm.sets().size() >= 2);
      assertTrue(warm.sets().size() < full.sets().size());
      assertEquals(last(full.sets()), last(warm.sets()));
      assertTrue(warm.finalGapSatisfied());
    }
  }

  @Test
  void generatedLoadsStaySelectableOrderedAndBelowWorkAcrossRepBandsAndIncrements() {
    int[] reps = {1, 2, 3, 4, 6, 7, 10, 11, 15, 16, 20, 21};
    double[] fractions = {.90, .84, .82, .77, .77, .70, .70, .65, .65, .58, .58, .52};
    for (double increment : new double[] {.5, 2.5, 5, 10}) {
      for (double weight : new double[] {1, 6, 12, 17.1, 25, 52, 116, 205, 405}) {
        for (double minimum : new double[] {0, 7, 45}) {
          for (boolean warm : new boolean[] {false, true}) {
            for (int band = 0; band < reps.length; band++) {
              var result = WorkingSetWarmups.generate(weight, reps[band], increment, minimum, warm);
              double previous = 0;
              for (var set : result.sets()) {
                assertTrue(set.load() > previous && set.load() >= minimum && set.load() < weight);
                assertEquals(Math.rint(set.load() / increment), set.load() / increment, 1e-9);
                previous = set.load();
              }
              assertEquals(
                  !result.sets().isEmpty() && previous + 1e-9 >= weight * fractions[band],
                  result.finalGapSatisfied());
              if (!result.sets().isEmpty()) {
                assertEquals(
                    last(WorkingSetWarmups.template(reps[band])).reps(),
                    last(result.sets()).reps());
              }
            }
          }
        }
      }
    }
  }

  @Test
  void rejectsInvalidInputsAndReturnsImmutableLists() {
    for (double invalid :
        new double[] {-1, 0, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}) {
      assertThrows(IllegalArgumentException.class, () -> WorkingSetWarmups.generate(invalid, 5));
      assertThrows(
          IllegalArgumentException.class,
          () -> WorkingSetWarmups.generate(100, 5, invalid, 0, false));
      assertThrows(
          IllegalArgumentException.class, () -> WarmupLoading.fixedLoads(List.of(invalid)));
    }
    for (double invalid :
        new double[] {-1, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}) {
      assertThrows(
          IllegalArgumentException.class,
          () -> WorkingSetWarmups.generate(100, 5, 5, invalid, false));
    }
    for (int invalid : new int[] {0, -1}) {
      assertThrows(IllegalArgumentException.class, () -> WorkingSetWarmups.template(invalid));
    }
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new WarmupLoading(
                WarmupLoading.LoadingSystem.BARBELL,
                0,
                5,
                List.of(),
                List.of(),
                Map.of(),
                List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new WarmupLoading(null, 0, 5, List.of(), List.of(), Map.of(), List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new WarmupLoading(
                WarmupLoading.LoadingSystem.BARBELL,
                45,
                5,
                List.of(5.0),
                List.of(),
                Map.of(5.0, -1),
                List.of()));
    assertThrows(UnsupportedOperationException.class, () -> WorkingSetWarmups.template(5).clear());
    assertThrows(
        UnsupportedOperationException.class,
        () -> WorkingSetWarmups.generate(100, 5).sets().clear());
  }

  private static <T> T last(List<T> values) {
    return values.get(values.size() - 1);
  }

  private static void assertBridge(
      double weight, int reps, int bridgeReps, double minimum, double maximum) {
    var result = WorkingSetWarmups.generate(weight, reps, WarmupLoading.barbell(), false);
    var bridge = last(result.sets());
    assertEquals(bridgeReps, bridge.reps());
    assertTrue(bridge.load() >= weight * minimum && bridge.load() <= weight * maximum);
    assertTrue(result.finalGapSatisfied());
  }
}
