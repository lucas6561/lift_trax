package com.lifttrax.workout;

import com.lifttrax.db.Database;
import com.lifttrax.models.Lift;
import com.lifttrax.models.LiftType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Core MaxEffortLiftPools component used by LiftTrax.
 */

public class MaxEffortLiftPools {
        private final List<Lift> lowerWeekLifts;
    private final List<Lift> upperWeekLifts;

    public MaxEffortLiftPools(int numWeeks, Database db) throws Exception {
        this(numWeeks, db, RandomSupport.DEFAULT);
    }

    public MaxEffortLiftPools(int numWeeks, Database db, RandomSupport.Randomizer randomizer) throws Exception {
        List<Lift> squats = new ArrayList<>(db.liftsByType(LiftType.SQUAT));
        List<Lift> deadlifts = new ArrayList<>(db.liftsByType(LiftType.DEADLIFT));
        List<Lift> benches = new ArrayList<>(db.liftsByType(LiftType.BENCH_PRESS));
        List<Lift> overheads = new ArrayList<>(db.liftsByType(LiftType.OVERHEAD_PRESS));

        int squatWeeks = (numWeeks + 1) / 2;
        int deadliftWeeks = numWeeks / 2;
        int benchWeeks = (numWeeks + 1) / 2;
        int overheadWeeks = numWeeks / 2;

        if (squats.size() < squatWeeks) throw new IllegalArgumentException("not enough squat lifts available");
        if (deadlifts.size() < deadliftWeeks) throw new IllegalArgumentException("not enough deadlift lifts available");
        if (benches.size() < benchWeeks) throw new IllegalArgumentException("not enough bench press lifts available");
        if (overheads.size() < overheadWeeks) throw new IllegalArgumentException("not enough overhead press lifts available");

        Random random = new Random();
        randomizer.shuffle(squats, random);
        randomizer.shuffle(deadlifts, random);
        randomizer.shuffle(benches, random);
        randomizer.shuffle(overheads, random);

        lowerWeekLifts = new ArrayList<>(numWeeks);
        upperWeekLifts = new ArrayList<>(numWeeks);

        int squatIdx = 0;
        int deadliftIdx = 0;
        int benchIdx = 0;
        int overheadIdx = 0;

        for (int i = 0; i < numWeeks; i++) {
            if (i % 2 == 0) {
                lowerWeekLifts.add(squats.get(squatIdx++));
                upperWeekLifts.add(benches.get(benchIdx++));
            } else {
                lowerWeekLifts.add(deadlifts.get(deadliftIdx++));
                upperWeekLifts.add(overheads.get(overheadIdx++));
            }
        }
    }

    public List<Lift> lowerWeeks() {
        return List.copyOf(lowerWeekLifts);
    }

    public List<Lift> upperWeeks() {
        return List.copyOf(upperWeekLifts);
    }
}
