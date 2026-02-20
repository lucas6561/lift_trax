package com.lifttrax.workout;

import com.lifttrax.db.Database;
import com.lifttrax.models.Lift;
import com.lifttrax.models.LiftRegion;
import com.lifttrax.models.LiftType;
import com.lifttrax.models.Muscle;
import com.lifttrax.models.SetMetric;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class HypertrophyWorkoutBuilder implements WorkoutBuilder {
    private static List<WorkoutLift> repeatedSets(String name, Lift lift, int sets, SetMetric metric, Float rpe) {
        List<WorkoutLift> lifts = new ArrayList<>();
        for (int i = 0; i < sets; i++) {
            lifts.add(new WorkoutLift(name, new WorkoutLiftKind.SingleKind(
                    new SingleLift(lift, metric, null, rpe, null, false)
            )));
        }
        return lifts;
    }

    private static Lift popOrThrow(RandomStack<Lift> stack, String errorMessage) {
        Lift next = stack.pop();
        if (next == null) {
            throw new IllegalArgumentException(errorMessage);
        }
        return next;
    }

    private static Workout accessoryCircuit(AccessoryStacks accessories, Muscle m1, Muscle m2, Muscle m3) {
        List<SingleLift> lifts = List.of(accessories.single(m1), accessories.single(m2), accessories.single(m3));
        return new Workout(List.of(new WorkoutLift("Accessory Circuit", new WorkoutLiftKind.CircuitKind(new CircuitLift(lifts, 3, false)))));
    }

    private static Workout buildUpperDay(
            Lift primary,
            Lift secondary,
            WarmupStacks warmups,
            AccessoryStacks accessories,
            boolean shoulderFocus
    ) {
        List<WorkoutLift> lifts = new ArrayList<>();
        lifts.add(warmups.warmup(LiftRegion.UPPER));
        lifts.addAll(repeatedSets("Main Hypertrophy", primary, 4, new SetMetric.RepsRange(8, 12), 8.0f));
        lifts.addAll(repeatedSets("Supplemental Hypertrophy", secondary, 3, new SetMetric.RepsRange(10, 15), 8.0f));

        if (shoulderFocus) {
            lifts.addAll(accessoryCircuit(accessories, Muscle.SHOULDER, Muscle.TRICEP, Muscle.BICEP).lifts());
        } else {
            lifts.addAll(accessoryCircuit(accessories, Muscle.LAT, Muscle.TRICEP, Muscle.REAR_DELT).lifts());
        }

        return new Workout(lifts);
    }

    private static Workout buildLowerDay(
            Lift primary,
            Lift secondary,
            WarmupStacks warmups,
            AccessoryStacks accessories,
            boolean posteriorFocus
    ) {
        List<WorkoutLift> lifts = new ArrayList<>();
        lifts.add(warmups.warmup(LiftRegion.LOWER));
        lifts.addAll(repeatedSets("Main Hypertrophy", primary, 4, new SetMetric.RepsRange(6, 10), 8.0f));
        lifts.addAll(repeatedSets("Supplemental Hypertrophy", secondary, 3, new SetMetric.RepsRange(8, 12), 8.0f));

        if (posteriorFocus) {
            lifts.addAll(accessoryCircuit(accessories, Muscle.HAMSTRING, Muscle.TRAP, Muscle.CORE).lifts());
        } else {
            lifts.addAll(accessoryCircuit(accessories, Muscle.QUAD, Muscle.CALF, Muscle.CORE).lifts());
        }

        return new Workout(lifts);
    }

    @Override
    public List<Map<DayOfWeek, Workout>> getWave(int numWeeks, Database db) throws Exception {
        RandomStack<Lift> bench = new RandomStack<>(db.liftsByType(LiftType.BENCH_PRESS));
        RandomStack<Lift> overhead = new RandomStack<>(db.liftsByType(LiftType.OVERHEAD_PRESS));
        RandomStack<Lift> squat = new RandomStack<>(db.liftsByType(LiftType.SQUAT));
        RandomStack<Lift> deadlift = new RandomStack<>(db.liftsByType(LiftType.DEADLIFT));

        if (bench.isEmpty() || overhead.isEmpty() || squat.isEmpty() || deadlift.isEmpty()) {
            throw new IllegalArgumentException("not enough primary lifts available for hypertrophy wave");
        }

        WarmupStacks warmups = new WarmupStacks(db);
        AccessoryStacks accessories = new AccessoryStacks(db);

        List<Map<DayOfWeek, Workout>> weeks = new ArrayList<>();
        for (int week = 0; week < numWeeks; week++) {
            Map<DayOfWeek, Workout> workoutWeek = new EnumMap<>(DayOfWeek.class);

            Lift mondayMain = popOrThrow(bench, "not enough bench lifts available");
            Lift mondaySupplemental = popOrThrow(overhead, "not enough overhead lifts available");
            workoutWeek.put(DayOfWeek.MONDAY, buildUpperDay(mondayMain, mondaySupplemental, warmups, accessories, false));

            Lift tuesdayMain = popOrThrow(squat, "not enough squat lifts available");
            Lift tuesdaySupplemental = popOrThrow(deadlift, "not enough deadlift lifts available");
            workoutWeek.put(DayOfWeek.TUESDAY, buildLowerDay(tuesdayMain, tuesdaySupplemental, warmups, accessories, false));

            Lift thursdayMain = popOrThrow(overhead, "not enough overhead lifts available");
            Lift thursdaySupplemental = popOrThrow(bench, "not enough bench lifts available");
            workoutWeek.put(DayOfWeek.THURSDAY, buildUpperDay(thursdayMain, thursdaySupplemental, warmups, accessories, true));

            Lift fridayMain = popOrThrow(deadlift, "not enough deadlift lifts available");
            Lift fridaySupplemental = popOrThrow(squat, "not enough squat lifts available");
            workoutWeek.put(DayOfWeek.FRIDAY, buildLowerDay(fridayMain, fridaySupplemental, warmups, accessories, true));

            weeks.add(workoutWeek);
        }

        return weeks;
    }
}
