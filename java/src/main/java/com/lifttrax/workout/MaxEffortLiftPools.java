package com.lifttrax.workout;

import com.lifttrax.db.Database;
import com.lifttrax.models.Lift;
import com.lifttrax.models.LiftType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MaxEffortLiftPools {
    private final List<Lift> lowerWeeks;
    private final List<Lift> upperWeeks;

    public MaxEffortLiftPools(int numWeeks, Database db) throws Exception {
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

        Collections.shuffle(squats);
        Collections.shuffle(deadlifts);
        Collections.shuffle(benches);
        Collections.shuffle(overheads);

        lowerWeeks = new ArrayList<>(numWeeks);
        upperWeeks = new ArrayList<>(numWeeks);

        int squatIdx = 0;
        int deadliftIdx = 0;
        int benchIdx = 0;
        int overheadIdx = 0;

        for (int i = 0; i < numWeeks; i++) {
            if (i % 2 == 0) {
                lowerWeeks.add(squats.get(squatIdx++));
                upperWeeks.add(benches.get(benchIdx++));
            } else {
                lowerWeeks.add(deadlifts.get(deadliftIdx++));
                upperWeeks.add(overheads.get(overheadIdx++));
            }
        }
    }

    public List<Lift> lowerWeeks() {
        return List.copyOf(lowerWeeks);
    }

    public List<Lift> upperWeeks() {
        return List.copyOf(upperWeeks);
    }
}
