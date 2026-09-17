package com.lifttrax.workout;

import java.util.ArrayList;
import java.util.List;

/** Implements the v2.0 templates using percentages of the planned working weight, never 1RM. */
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
    if (workingReps == 1) {
      return List.of(
          new Stage(35, 5), new Stage(55, 3), new Stage(70, 2), new Stage(82, 1), new Stage(92, 1));
    }
    if (workingReps <= 3) {
      return List.of(
          new Stage(35, 5),
          new Stage(55, 3),
          new Stage(70, 2),
          new Stage(workingReps == 2 ? 87 : 85, workingReps == 2 ? 1 : 2));
    }
    if (workingReps <= 6) {
      return List.of(
          new Stage(35, 5),
          new Stage(55, 4),
          new Stage(70, 3),
          new Stage(80, (workingReps + 1) / 2));
    }
    if (workingReps <= 10) {
      return List.of(new Stage(35, 6), new Stage(55, 5), new Stage(75, (workingReps + 1) / 2));
    }
    if (workingReps <= 15) {
      return List.of(new Stage(35, 8), new Stage(55, 6), new Stage(70, 5));
    }
    if (workingReps <= 20) {
      return List.of(new Stage(30, 8), new Stage(50, 6), new Stage(65, 5));
    }
    return List.of(new Stage(27.5, 10), new Stage(47.5, 6), new Stage(60, 5));
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
    List<WarmupSet> sets = new ArrayList<>();
    WarmupLoading.Candidate previous = WarmupLoading.Candidate.empty();
    double minimumFinalLoad = workingWeight * minimumFinalFraction(workingReps);
    for (int index = 0; index < stages.size(); index++) {
      Stage stage = stages.get(index);
      boolean finalStage = index == stages.size() - 1;
      WarmupLoading.Candidate chosen =
          loading.choose(
              workingWeight, stage.percent(), finalStage ? minimumFinalLoad : 0, previous);
      if (chosen != null) {
        // Keep the later stage's reps, including multi-rep bridges when loads collapse.
        while (!sets.isEmpty() && sets.get(sets.size() - 1).load() >= chosen.load()) {
          sets.remove(sets.size() - 1);
        }
        sets.add(new WarmupSet(chosen.load(), stage.reps(), stage.percent()));
        previous = chosen;
      }
    }
    pruneSmallSteps(sets, workingWeight);
    if (alreadyWarm) {
      int removed = 0;
      while (removed < 2 && sets.size() > 2 && sets.get(0).percent() <= 60) {
        sets.remove(0);
        removed++;
      }
    }
    boolean satisfied =
        !sets.isEmpty() && sets.get(sets.size() - 1).load() + 1e-9 >= minimumFinalLoad;
    return new Result(sets, satisfied);
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

  private static double minimumFinalFraction(int reps) {
    if (reps == 1) {
      return 0.90;
    }
    if (reps == 2) {
      return 0.84;
    }
    if (reps == 3) {
      return 0.82;
    }
    if (reps <= 6) {
      return 0.77;
    }
    if (reps <= 10) {
      return 0.70;
    }
    if (reps <= 15) {
      return 0.65;
    }
    return reps <= 20 ? 0.58 : 0.52;
  }
}
