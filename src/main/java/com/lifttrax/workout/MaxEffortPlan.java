package com.lifttrax.workout;

import com.lifttrax.models.Lift;
import java.util.ArrayList;
import java.util.List;

/** Simple data holder for MaxEffortPlan values used by LiftTrax. */
public record MaxEffortPlan(
    List<Lift> lower,
    List<Lift> upper,
    List<DeloadLowerLifts> lowerDeload,
    List<DeloadUpperLifts> upperDeload) {
  public static MaxEffortPlan fromDefaults(List<Lift> defaultLower, List<Lift> defaultUpper) {
    return new MaxEffortPlan(
        defaultLower,
        defaultUpper,
        deriveLowerDeloadFromPlan(defaultLower),
        deriveUpperDeloadFromPlan(defaultUpper));
  }

  public static List<DeloadLowerLifts> deriveLowerDeloadFromPlan(List<Lift> plan) {
    if (plan.size() < 7) {
      return List.of();
    }
    List<DeloadLowerLifts> deload = new ArrayList<>();
    int deloadWeeks = plan.size() / 7;
    for (int deloadIdx = 0; deloadIdx < deloadWeeks; deloadIdx++) {
      int week = (deloadIdx + 1) * 7 - 1;
      Lift current = plan.get(week);
      Lift prior = plan.get(week - 1);
      if (week % 2 == 0) {
        deload.add(new DeloadLowerLifts(current, prior));
      } else {
        deload.add(new DeloadLowerLifts(prior, current));
      }
    }
    return deload;
  }

  public static List<DeloadUpperLifts> deriveUpperDeloadFromPlan(List<Lift> plan) {
    if (plan.size() < 7) {
      return List.of();
    }
    List<DeloadUpperLifts> deload = new ArrayList<>();
    int deloadWeeks = plan.size() / 7;
    for (int deloadIdx = 0; deloadIdx < deloadWeeks; deloadIdx++) {
      int week = (deloadIdx + 1) * 7 - 1;
      Lift current = plan.get(week);
      Lift prior = plan.get(week - 1);
      if (week % 2 == 0) {
        deload.add(new DeloadUpperLifts(current, prior));
      } else {
        deload.add(new DeloadUpperLifts(prior, current));
      }
    }
    return deload;
  }

  public record DeloadLowerLifts(Lift squat, Lift deadlift) {}

  public record DeloadUpperLifts(Lift bench, Lift overhead) {}
}
