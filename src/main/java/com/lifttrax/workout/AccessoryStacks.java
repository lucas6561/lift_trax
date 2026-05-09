package com.lifttrax.workout;

import com.lifttrax.db.Database;
import com.lifttrax.models.Lift;
import com.lifttrax.models.Muscle;
import com.lifttrax.models.SetMetric;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Core AccessoryStacks component used by LiftTrax. */
public class AccessoryStacks {
  private final Map<Muscle, RandomStack<Lift>> stacks = new EnumMap<>(Muscle.class);
  private RandomStack<Lift> forearms;

  public AccessoryStacks(Database db) throws Exception {
    this(db, RandomSupport.DEFAULT);
  }

  public AccessoryStacks(Database db, RandomSupport.Randomizer randomizer) throws Exception {
    Muscle[] required = {
      Muscle.HAMSTRING,
      Muscle.QUAD,
      Muscle.CALF,
      Muscle.LAT,
      Muscle.TRICEP,
      Muscle.REAR_DELT,
      Muscle.SHOULDER,
      Muscle.FRONT_DELT,
      Muscle.TRAP,
      Muscle.CORE,
      Muscle.BICEP
    };

    for (Muscle muscle : required) {
      List<Lift> lifts = db.getAccessoriesByMuscle(muscle);
      if (lifts.isEmpty()) {
        throw new IllegalArgumentException("not enough accessory lifts available for " + muscle);
      }
      stacks.put(muscle, new RandomStack<>(lifts, randomizer));
    }

    List<Lift> forearmLifts = db.getAccessoriesByMuscle(Muscle.FOREARM);
    if (!forearmLifts.isEmpty()) {
      forearms = new RandomStack<>(forearmLifts, randomizer);
    }
  }

  public SingleLift single(Muscle muscle) {
    Lift lift = pop(muscle);
    return asSingleLift(lift);
  }

  Lift pop(Muscle muscle) {
    RandomStack<Lift> stack = stacks.get(muscle);
    if (stack == null) {
      throw new IllegalArgumentException("not enough accessory lifts available for " + muscle);
    }
    Lift lift = stack.pop();
    if (lift == null) {
      throw new IllegalArgumentException("not enough accessory lifts available for " + muscle);
    }
    return lift;
  }

  void putBack(Muscle muscle, Lift lift) {
    RandomStack<Lift> stack = stacks.get(muscle);
    if (stack == null) {
      throw new IllegalArgumentException("not enough accessory lifts available for " + muscle);
    }
    stack.putBack(lift);
  }

  private static SingleLift asSingleLift(Lift lift) {
    return new SingleLift(lift, new SetMetric.RepsRange(8, 12), null, null, null, false);
  }

  public SingleLift forearm() {
    if (forearms == null) {
      return null;
    }
    Lift lift = forearms.pop();
    if (lift == null) {
      return null;
    }
    return new SingleLift(lift, new SetMetric.Reps(5), 80, null, null, false);
  }
}
