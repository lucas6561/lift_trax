package com.lifttrax.workout;

import java.util.ArrayList;
import java.util.List;

/** Builds warm-ups from the actual working weight and repetitions, without a max estimate. */
public final class WorkingSetWarmups {
  private WorkingSetWarmups() {}

  public record Stage(int percent, int reps) {}

  /** A zero percent identifies the optional light entry set. */
  public record WarmupSet(double load, int reps, int percent) {}

  public record Result(List<WarmupSet> sets, boolean finalGapSatisfied) {
    public Result {
      sets = List.copyOf(sets);
    }
  }

  public static List<Stage> template(int workingReps) {
    if (workingReps < 1) {
      throw new IllegalArgumentException("Working repetitions must be positive.");
    }
    if (workingReps == 1) {
      return List.of(
          new Stage(35, 5), new Stage(50, 3), new Stage(65, 2), new Stage(80, 1), new Stage(90, 1));
    }
    if (workingReps <= 3) {
      return List.of(new Stage(40, 5), new Stage(55, 3), new Stage(70, 2), new Stage(85, 1));
    }
    if (workingReps <= 6) {
      return List.of(new Stage(40, 5), new Stage(60, 3), new Stage(75, 2), new Stage(85, 1));
    }
    if (workingReps <= 10) {
      return List.of(new Stage(40, 5), new Stage(60, 3), new Stage(75, 1));
    }
    if (workingReps <= 15) {
      return List.of(new Stage(40, 5), new Stage(60, 2));
    }
    return List.of(new Stage(35, 5), new Stage(50, 2));
  }

  public static Result generate(double workingWeight, int workingReps) {
    return generate(workingWeight, workingReps, 5, 0, false);
  }

  public static Result generate(
      double workingWeight,
      int workingReps,
      double loadIncrement,
      double minimumLoad,
      boolean alreadyWarm) {
    validateLoads(workingWeight, loadIncrement, minimumLoad);
    List<Stage> stages = template(workingReps);
    double practicalMinimum = Math.ceil(minimumLoad / loadIncrement) * loadIncrement;
    List<WarmupSet> sets = new ArrayList<>();
    for (Stage stage : stages) {
      double load =
          Math.max(
              practicalMinimum,
              roundToIncrement(workingWeight * (stage.percent() / 100.0), loadIncrement));
      if (isSafeWarmup(load, workingWeight)) {
        appendOrMerge(sets, new WarmupSet(load, stage.reps(), stage.percent()));
      }
    }

    double minimumFinalLoad = workingWeight * minimumFinalFraction(workingReps);
    repairFinalBridge(
        sets,
        stages.get(stages.size() - 1),
        workingWeight,
        loadIncrement,
        practicalMinimum,
        minimumFinalLoad);
    pruneSmallSteps(sets, loadIncrement);
    if (alreadyWarm) {
      int removed = 0;
      while (removed < 2 && sets.size() > 2 && sets.get(0).load() < workingWeight * 0.60) {
        sets.remove(0);
        removed++;
      }
    }
    // Recheck the actual final load after rounding, merging, repair, and shortening.
    boolean finalGapSatisfied =
        !sets.isEmpty() && sets.get(sets.size() - 1).load() >= minimumFinalLoad;
    if (!alreadyWarm
        && practicalMinimum > 0
        && !sets.isEmpty()
        && practicalMinimum <= sets.get(0).load() / 2) {
      sets.add(0, new WarmupSet(practicalMinimum, 8, 0));
    }
    return new Result(sets, finalGapSatisfied);
  }

  private static void validateLoads(
      double workingWeight, double loadIncrement, double minimumLoad) {
    if (!Double.isFinite(workingWeight) || workingWeight <= 0) {
      throw new IllegalArgumentException("Working weight must be finite and positive.");
    }
    if (!Double.isFinite(loadIncrement) || loadIncrement <= 0) {
      throw new IllegalArgumentException("Load increment must be finite and positive.");
    }
    if (!Double.isFinite(minimumLoad) || minimumLoad < 0) {
      throw new IllegalArgumentException("Minimum load must be finite and nonnegative.");
    }
  }

  private static double roundToIncrement(double load, double increment) {
    return Math.round(load / increment) * increment;
  }

  private static boolean isSafeWarmup(double load, double workingWeight) {
    return Double.isFinite(load)
        && load > 0
        && load < workingWeight
        && load <= workingWeight * 0.95;
  }

  private static void appendOrMerge(List<WarmupSet> sets, WarmupSet next) {
    if (!sets.isEmpty() && sets.get(sets.size() - 1).load() == next.load()) {
      WarmupSet previous = sets.get(sets.size() - 1);
      sets.set(
          sets.size() - 1,
          new WarmupSet(next.load(), Math.min(previous.reps(), next.reps()), next.percent()));
    } else {
      sets.add(next);
    }
  }

  private static void repairFinalBridge(
      List<WarmupSet> sets,
      Stage finalStage,
      double workingWeight,
      double increment,
      double practicalMinimum,
      double minimumFinalLoad) {
    if (!sets.isEmpty() && sets.get(sets.size() - 1).load() >= minimumFinalLoad) {
      int finalIndex = sets.size() - 1;
      WarmupSet retained = sets.get(finalIndex);
      sets.set(
          finalIndex,
          new WarmupSet(
              retained.load(), Math.min(retained.reps(), finalStage.reps()), retained.percent()));
      return;
    }
    double maximumLoad = Math.floor(workingWeight * 0.95 / increment) * increment;
    double nominalLoad =
        roundToIncrement(workingWeight * (finalStage.percent() / 100.0), increment);
    double requiredLoad = Math.ceil(minimumFinalLoad / increment) * increment;
    double bridgeLoad =
        Math.max(practicalMinimum, Math.max(requiredLoad, Math.min(nominalLoad, maximumLoad)));
    if (isSafeWarmup(bridgeLoad, workingWeight) && bridgeLoad >= minimumFinalLoad) {
      appendOrMerge(sets, new WarmupSet(bridgeLoad, finalStage.reps(), finalStage.percent()));
    }
  }

  private static void pruneSmallSteps(List<WarmupSet> sets, double increment) {
    int index = 1;
    while (sets.size() > 2 && index < sets.size() - 1) {
      if (sets.get(index).load() - sets.get(index - 1).load() < 2 * increment) {
        sets.remove(index);
      } else {
        index++;
      }
    }
  }

  private static double minimumFinalFraction(int workingReps) {
    if (workingReps == 1) {
      return 0.88;
    }
    if (workingReps <= 3) {
      return 0.82;
    }
    if (workingReps <= 6) {
      return 0.72;
    }
    if (workingReps <= 10) {
      return 0.65;
    }
    if (workingReps <= 15) {
      return 0.50;
    }
    return 0.40;
  }
}
