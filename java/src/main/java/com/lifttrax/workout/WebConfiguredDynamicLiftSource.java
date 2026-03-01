package com.lifttrax.workout;

import com.lifttrax.db.Database;
import com.lifttrax.models.Lift;
import com.lifttrax.models.LiftType;

import java.util.List;
import java.util.Map;

public class WebConfiguredDynamicLiftSource implements DynamicLiftSource {
    private final Map<String, String> values;

    public WebConfiguredDynamicLiftSource(Map<String, String> values) {
        this.values = values;
    }

    @Override
    public DynamicLifts select(Database db) throws Exception {
        DynamicLifts defaults = DynamicLifts.fromDatabase(db, false);

        List<Lift> squats = db.liftsByType(LiftType.SQUAT);
        List<Lift> deadlifts = db.liftsByType(LiftType.DEADLIFT);
        List<Lift> benches = db.liftsByType(LiftType.BENCH_PRESS);
        List<Lift> overheads = db.liftsByType(LiftType.OVERHEAD_PRESS);

        Lift squat = findByName(squats, values.getOrDefault("deSquat", defaults.squat().lift().name()), defaults.squat().lift());
        Lift deadlift = findByName(deadlifts, values.getOrDefault("deDeadlift", defaults.deadlift().lift().name()), defaults.deadlift().lift());
        Lift bench = findByName(benches, values.getOrDefault("deBench", defaults.bench().lift().name()), defaults.bench().lift());
        Lift overhead = findByName(overheads, values.getOrDefault("deOverhead", defaults.overhead().lift().name()), defaults.overhead().lift());

        AccommodatingResistance squatAr = parseAr(values.get("deSquatAr"), defaults.squat().ar());
        AccommodatingResistance deadliftAr = parseAr(values.get("deDeadliftAr"), defaults.deadlift().ar());
        AccommodatingResistance benchAr = parseAr(values.get("deBenchAr"), defaults.bench().ar());
        AccommodatingResistance overheadAr = parseAr(values.get("deOverheadAr"), defaults.overhead().ar());

        return new DynamicLifts(
                new DynamicLift(squat, squatAr),
                new DynamicLift(deadlift, deadliftAr),
                new DynamicLift(bench, benchAr),
                new DynamicLift(overhead, overheadAr)
        );
    }

    private static Lift findByName(List<Lift> options, String name, Lift fallback) {
        for (Lift option : options) {
            if (option.name().equals(name)) {
                return option;
            }
        }
        return fallback;
    }

    private static AccommodatingResistance parseAr(String value, AccommodatingResistance fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return AccommodatingResistance.valueOf(value.trim().toUpperCase());
        } catch (Exception ignored) {
            return fallback;
        }
    }
}

