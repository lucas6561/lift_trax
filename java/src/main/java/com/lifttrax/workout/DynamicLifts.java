package com.lifttrax.workout;

import com.lifttrax.db.Database;
import com.lifttrax.models.Lift;
import com.lifttrax.models.LiftType;

import java.util.List;
import java.util.Random;

public record DynamicLifts(
        DynamicLift squat,
        DynamicLift deadlift,
        DynamicLift bench,
        DynamicLift overhead
) {
    public static DynamicLifts fromDatabase(Database db) throws Exception {
        Random random = new Random();
        AccommodatingResistance[] arOpts = {AccommodatingResistance.CHAINS, AccommodatingResistance.BANDS};

        Lift squat = firstLiftOrFallback(db, LiftType.SQUAT, "Squat");
        Lift deadlift = firstLiftOrFallback(db, LiftType.DEADLIFT, "Deadlift");
        Lift bench = firstLiftOrFallback(db, LiftType.BENCH_PRESS, "Bench Press");
        Lift overhead = firstLiftOrFallback(db, LiftType.OVERHEAD_PRESS, "Overhead Press");

        return new DynamicLifts(
                new DynamicLift(squat, arOpts[random.nextInt(arOpts.length)]),
                new DynamicLift(deadlift, arOpts[random.nextInt(arOpts.length)]),
                new DynamicLift(bench, arOpts[random.nextInt(arOpts.length)]),
                new DynamicLift(overhead, arOpts[random.nextInt(arOpts.length)])
        );
    }

    private static Lift firstLiftOrFallback(Database db, LiftType liftType, String fallbackName) throws Exception {
        List<Lift> options = db.liftsByType(liftType);
        if (!options.isEmpty()) {
            return options.get(0);
        }
        return db.getLift(fallbackName);
    }
}
