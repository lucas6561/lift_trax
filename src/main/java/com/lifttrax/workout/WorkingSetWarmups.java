package com.lifttrax.workout;

import java.util.ArrayList;
import java.util.List;

/** Builds practical warm-up ladders from the planned working weight, never 1RM. */
public final class WorkingSetWarmups {
  private WorkingSetWarmups() {}

  public record Stage(double percent, int reps) {}

  /** Percent is the template target, before practical load selection. */
  public record WarmupSet(double load, int reps, double percent) {}

  public record Result(List<WarmupSet> sets, boolean finalGapSatisfied) {
    public Result {
      sets = List.copyOf(sets);
    }
  }

  public static List<Stage> template(int workingReps) {
    if (workingReps < 1) {
      throw new IllegalArgumentException("Working repetitions must be positive.");
    }
    if (workingReps <= 2) {
      return List.of(
          new Stage(40, 5), new Stage(60, 3), new Stage(75, 2), new Stage(85, 1), new Stage(92, 1));
    }
    if (workingReps <= 4) {
      return List.of(new Stage(40, 5), new Stage(60, 3), new Stage(75, 2), new Stage(85, 1));
    }
    return List.of(new Stage(40, 5), new Stage(60, 3), new Stage(80, 2));
  }

  public static Result generate(double workingWeight, int workingReps) {
    return generate(workingWeight, workingReps, WarmupLoading.fixedIncrement(5, 0), false);
  }

  public static Result generate(
      double workingWeight,
      int workingReps,
      double loadIncrement,
      double minimumLoad,
      boolean alreadyWarm) {
    return generate(
        workingWeight,
        workingReps,
        WarmupLoading.fixedIncrement(loadIncrement, minimumLoad),
        alreadyWarm);
  }

  public static Result generate(
      double workingWeight, int workingReps, WarmupLoading loading, boolean alreadyWarm) {
    if (!Double.isFinite(workingWeight) || workingWeight <= 0) {
      throw new IllegalArgumentException("Working weight must be finite and positive.");
    }
    List<Stage> stages = template(workingReps);
    double minimumFinalLoad = workingWeight * minimumFinalFraction(workingReps);
    List<WarmupSet> sets = buildSets(workingWeight, loading, stages, minimumFinalLoad);
    pruneSmallSteps(sets, workingWeight);
    removeEarlyWarmupsWhenAlreadyWarm(sets, alreadyWarm);
    boolean jumpsSatisfied = repairLateJumps(sets, workingWeight, workingReps, loading);
    boolean satisfied =
        jumpsSatisfied
            && !sets.isEmpty()
            && sets.get(sets.size() - 1).load() + 1e-9 >= minimumFinalLoad;
    return new Result(sets, satisfied);
  }

  private static List<WarmupSet> buildSets(
      double workingWeight, WarmupLoading loading, List<Stage> stages, double minimumFinalLoad) {
    List<WarmupSet> sets = new ArrayList<>();
    for (int index = 0; index < stages.size(); index++) {
      Stage stage = stages.get(index);
      boolean finalStage = index == stages.size() - 1;
      WarmupLoading.Candidate chosen =
          loading.choose(workingWeight, stage.percent(), finalStage ? minimumFinalLoad : 0);
      if (chosen != null) {
        // Keep the later stage's reps, including multi-rep bridges when loads collapse.
        while (!sets.isEmpty() && sets.get(sets.size() - 1).load() >= chosen.load()) {
          sets.remove(sets.size() - 1);
        }
        sets.add(new WarmupSet(chosen.load(), stage.reps(), stage.percent()));
      }
    }
    return sets;
  }

  private static void removeEarlyWarmupsWhenAlreadyWarm(List<WarmupSet> sets, boolean alreadyWarm) {
    if (!alreadyWarm) {
      return;
    }
    int removed = 0;
    while (removed < 2 && sets.size() > 2 && sets.get(0).percent() <= 60) {
      sets.remove(0);
      removed++;
    }
  }

  private static void pruneSmallSteps(List<WarmupSet> sets, double workingWeight) {
    int index = sets.size() - 1;
    while (index > 0) {
      double jump = sets.get(index).load() - sets.get(index - 1).load();
      if (jump < 10 && jump < workingWeight * 0.05 && sets.size() > 2) {
        // The final bridge always wins; two remaining exposures are useful even if close.
        sets.remove(index - 1);
      }
      index--;
    }
  }

  private static boolean repairLateJumps(
      List<WarmupSet> sets, double workingWeight, int workingReps, WarmupLoading loading) {
    if (workingReps > 4 || sets.isEmpty()) {
      return true;
    }
    double threshold = workingWeight * .70;
    double maximumJump = Math.max(20, Math.min(50, Math.floor(workingWeight * .15 / 5) * 5));
    int index = 1;
    while (index <= sets.size()) {
      LateStep step = lateStepAt(sets, index, workingWeight, workingReps);
      if (!needsBridge(step, threshold, maximumJump)) {
        index++;
        continue;
      }
      WarmupLoading.Candidate bridge =
          chooseBridge(loading, workingWeight, threshold, maximumJump, step);
      if (bridge == null) {
        return false;
      }
      applyBridge(sets, index, step, bridge);
    }
    return true;
  }

  private static LateStep lateStepAt(
      List<WarmupSet> sets, int index, double workingWeight, int workingReps) {
    boolean workSet = index == sets.size();
    return new LateStep(
        sets.get(index - 1),
        workSet ? workingWeight : sets.get(index).load(),
        workSet ? workingReps : sets.get(index).reps());
  }

  private static boolean needsBridge(LateStep step, double threshold, double maximumJump) {
    return step.nextLoad() + 1e-9 >= threshold
        && step.nextLoad() - step.previous().load() > maximumJump + 1e-9;
  }

  private static WarmupLoading.Candidate chooseBridge(
      WarmupLoading loading,
      double workingWeight,
      double threshold,
      double maximumJump,
      LateStep step) {
    return loading.chooseBridge(
        workingWeight, step.previous().load(), step.nextLoad(), threshold, maximumJump);
  }

  private static void applyBridge(
      List<WarmupSet> sets, int index, LateStep step, WarmupLoading.Candidate bridge) {
    if (bridge.load() - step.previous().load() <= 10 + 1e-9) {
      sets.set(
          index - 1,
          new WarmupSet(bridge.load(), step.previous().reps(), step.previous().percent()));
    } else if (index < sets.size() && step.nextLoad() - bridge.load() <= 10 + 1e-9) {
      sets.set(index, new WarmupSet(bridge.load(), step.nextReps(), sets.get(index).percent()));
    } else {
      sets.add(index, new WarmupSet(bridge.load(), step.nextReps(), Double.NaN));
    }
  }

  private static double minimumFinalFraction(int reps) {
    return reps <= 2 ? .91 : reps <= 4 ? .83 : .77;
  }

  private record LateStep(WarmupSet previous, double nextLoad, int nextReps) {}
}
