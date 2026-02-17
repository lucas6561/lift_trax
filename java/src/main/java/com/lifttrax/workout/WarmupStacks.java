package com.lifttrax.workout;

import com.lifttrax.db.Database;
import com.lifttrax.models.Lift;
import com.lifttrax.models.LiftRegion;
import com.lifttrax.models.LiftType;
import com.lifttrax.models.Muscle;

import java.util.ArrayList;
import java.util.List;

public class WarmupStacks {
    private final RandomStack<Lift> core;
    private final RandomStack<Lift> lowerMobility;
    private final RandomStack<Lift> upperMobility;
    private final RandomStack<Lift> lowerAccessories;
    private final RandomStack<Lift> upperAccessories;

    public WarmupStacks(Database db) throws Exception {
        List<Lift> lowerAccessoryLifts = new ArrayList<>(db.liftsByRegionAndType(LiftRegion.LOWER, LiftType.ACCESSORY));
        lowerAccessoryLifts.removeIf(l -> l.muscles().contains(Muscle.FOREARM) || l.muscles().contains(Muscle.CORE));
        if (lowerAccessoryLifts.size() < 2) {
            throw new IllegalArgumentException("not enough accessory lifts available");
        }

        List<Lift> upperAccessoryLifts = new ArrayList<>(db.liftsByRegionAndType(LiftRegion.UPPER, LiftType.ACCESSORY));
        upperAccessoryLifts.removeIf(l -> l.muscles().contains(Muscle.FOREARM) || l.muscles().contains(Muscle.CORE));
        if (upperAccessoryLifts.size() < 2) {
            throw new IllegalArgumentException("not enough accessory lifts available");
        }

        core = new RandomStack<>(db.getAccessoriesByMuscle(Muscle.CORE));
        if (core.isEmpty()) {
            throw new IllegalArgumentException("not enough core lifts available");
        }

        lowerMobility = new RandomStack<>(db.liftsByRegionAndType(LiftRegion.LOWER, LiftType.MOBILITY));
        if (lowerMobility.isEmpty()) {
            throw new IllegalArgumentException("not enough mobility lifts available");
        }

        upperMobility = new RandomStack<>(db.liftsByRegionAndType(LiftRegion.UPPER, LiftType.MOBILITY));
        if (upperMobility.isEmpty()) {
            throw new IllegalArgumentException("not enough mobility lifts available");
        }

        lowerAccessories = new RandomStack<>(lowerAccessoryLifts);
        upperAccessories = new RandomStack<>(upperAccessoryLifts);
    }

    public WorkoutLift warmup(LiftRegion region) {
        Lift coreLift = core.pop();
        if (coreLift == null) {
            throw new IllegalArgumentException("not enough core lifts available");
        }

        RandomStack<Lift> mobilityStack = region == LiftRegion.LOWER ? lowerMobility : upperMobility;
        RandomStack<Lift> accessoryStack = region == LiftRegion.LOWER ? lowerAccessories : upperAccessories;

        Lift mobility = mobilityStack.pop();
        Lift accessoryOne = accessoryStack.pop();
        Lift accessoryTwo = accessoryStack.pop();

        if (mobility == null) {
            throw new IllegalArgumentException("not enough mobility lifts available");
        }
        if (accessoryOne == null || accessoryTwo == null) {
            throw new IllegalArgumentException("not enough accessory lifts available");
        }

        List<SingleLift> warmupLifts = List.of(
                new SingleLift(mobility, null, null, null, null, false),
                new SingleLift(accessoryOne, null, null, null, null, false),
                new SingleLift(accessoryTwo, null, null, null, null, false),
                new SingleLift(coreLift, null, null, null, null, false)
        );

        return new WorkoutLift("Warmup Circuit", new WorkoutLiftKind.CircuitKind(new CircuitLift(warmupLifts, 3, true)));
    }
}
