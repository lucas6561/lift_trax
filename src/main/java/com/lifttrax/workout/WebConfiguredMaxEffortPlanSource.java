package com.lifttrax.workout;

import com.lifttrax.db.Database;
import com.lifttrax.models.Lift;
import com.lifttrax.models.LiftType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Core WebConfiguredMaxEffortPlanSource component used by LiftTrax. */
public class WebConfiguredMaxEffortPlanSource implements MaxEffortPlanSource {
  private final int weeks;
  private final Map<String, String> values;

  public WebConfiguredMaxEffortPlanSource(int weeks, Map<String, String> values) {
    this.weeks = weeks;
    this.values = values;
  }

  @Override
  public MaxEffortPlan selectPlan(Database db, MaxEffortLiftPools pools) throws Exception {
    List<Lift> squats = db.liftsByType(LiftType.SQUAT);
    List<Lift> deadlifts = db.liftsByType(LiftType.DEADLIFT);
    List<Lift> benches = db.liftsByType(LiftType.BENCH_PRESS);
    List<Lift> overheads = db.liftsByType(LiftType.OVERHEAD_PRESS);

    List<Lift> lowerDefaults = pools.lowerWeeks();
    List<Lift> upperDefaults = pools.upperWeeks();

    List<Lift> lower = new ArrayList<>(lowerDefaults);
    List<Lift> upper = new ArrayList<>(upperDefaults);

    for (int i = 0; i < weeks; i++) {
      int week = i + 1;
      if (week % 7 == 0) {
        continue;
      }
      String lowerName = values.getOrDefault("meLowerWeek" + week, lower.get(i).name());
      String upperName = values.getOrDefault("meUpperWeek" + week, upper.get(i).name());
      lower.set(i, findByName((i % 2 == 0) ? squats : deadlifts, lowerName, lower.get(i)));
      upper.set(i, findByName((i % 2 == 0) ? benches : overheads, upperName, upper.get(i)));
    }

    List<MaxEffortPlan.DeloadLowerLifts> lowerDeload =
        new ArrayList<>(MaxEffortPlan.deriveLowerDeloadFromPlan(lower));
    List<MaxEffortPlan.DeloadUpperLifts> upperDeload =
        new ArrayList<>(MaxEffortPlan.deriveUpperDeloadFromPlan(upper));

    for (int i = 0; i < lowerDeload.size(); i++) {
      int n = i + 1;
      MaxEffortPlan.DeloadLowerLifts def = lowerDeload.get(i);
      Lift squat =
          findByName(
              squats,
              values.getOrDefault("meLowerDeload" + n + "Squat", def.squat().name()),
              def.squat());
      Lift deadlift =
          findByName(
              deadlifts,
              values.getOrDefault("meLowerDeload" + n + "Deadlift", def.deadlift().name()),
              def.deadlift());
      lowerDeload.set(i, new MaxEffortPlan.DeloadLowerLifts(squat, deadlift));
    }

    for (int i = 0; i < upperDeload.size(); i++) {
      int n = i + 1;
      MaxEffortPlan.DeloadUpperLifts def = upperDeload.get(i);
      Lift bench =
          findByName(
              benches,
              values.getOrDefault("meUpperDeload" + n + "Bench", def.bench().name()),
              def.bench());
      Lift overhead =
          findByName(
              overheads,
              values.getOrDefault("meUpperDeload" + n + "Overhead", def.overhead().name()),
              def.overhead());
      upperDeload.set(i, new MaxEffortPlan.DeloadUpperLifts(bench, overhead));
    }

    return new MaxEffortPlan(lower, upper, lowerDeload, upperDeload);
  }

  private static Lift findByName(List<Lift> options, String name, Lift fallback) {
    for (Lift option : options) {
      if (option.name().equals(name)) {
        return option;
      }
    }
    return fallback;
  }
}
