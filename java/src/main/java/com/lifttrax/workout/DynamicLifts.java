package com.lifttrax.workout;

import com.lifttrax.db.Database;
import com.lifttrax.models.Lift;
import com.lifttrax.models.LiftType;

import java.util.ArrayList;
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

        List<Lift> squatOptions = liftsForType(db, LiftType.SQUAT, "Squat");
        List<Lift> deadliftOptions = liftsForType(db, LiftType.DEADLIFT, "Deadlift");
        List<Lift> benchOptions = liftsForType(db, LiftType.BENCH_PRESS, "Bench Press");
        List<Lift> overheadOptions = liftsForType(db, LiftType.OVERHEAD_PRESS, "Overhead Press");

        var defaults = new DynamicLiftSelector.DynamicLiftChoices(
                squatOptions.get(0),
                deadliftOptions.get(0),
                benchOptions.get(0),
                overheadOptions.get(0)
        );
        var chosen = DynamicLiftSelector.choose(
                squatOptions,
                deadliftOptions,
                benchOptions,
                overheadOptions,
                defaults
        );

        return new DynamicLifts(
                new DynamicLift(chosen.squat(), arOpts[random.nextInt(arOpts.length)]),
                new DynamicLift(chosen.deadlift(), arOpts[random.nextInt(arOpts.length)]),
                new DynamicLift(chosen.bench(), arOpts[random.nextInt(arOpts.length)]),
                new DynamicLift(chosen.overhead(), arOpts[random.nextInt(arOpts.length)])
        );
    }

    private static List<Lift> liftsForType(Database db, LiftType liftType, String fallbackName) throws Exception {
        List<Lift> options = new ArrayList<>(db.liftsByType(liftType));
        int idx = -1;
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).name().equals(fallbackName)) {
                idx = i;
                break;
            }
        }
        if (idx > 0) {
            Lift fallback = options.remove(idx);
            options.add(0, fallback);
        } else if (idx < 0) {
            options.add(0, db.getLift(fallbackName));
        }
        return options;
    }
}
