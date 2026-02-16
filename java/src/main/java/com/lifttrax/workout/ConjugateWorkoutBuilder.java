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
import java.util.Random;

public class ConjugateWorkoutBuilder implements WorkoutBuilder {
    private static WorkoutLift maxEffortSingle(Lift lift) {
        return new WorkoutLift("Max Effort Single", new WorkoutLiftKind.SingleKind(
                new SingleLift(lift, new SetMetric.Reps(1), null, null, null, false)
        ));
    }

    private static List<WorkoutLift> repeatedSingles(String name, Lift lift, int sets, SetMetric metric, Integer percent, Float rpe, boolean deload, AccommodatingResistance ar) {
        List<WorkoutLift> lifts = new ArrayList<>();
        for (int i = 0; i < sets; i++) {
            lifts.add(new WorkoutLift(name, new WorkoutLiftKind.SingleKind(
                    new SingleLift(lift, metric, percent, rpe, ar, deload)
            )));
        }
        return lifts;
    }

    private static boolean isDeloadWeek(int weekNumber) {
        return (weekNumber + 1) % 7 == 0;
    }

    private static WorkoutLift accessoryCircuit(AccessoryStacks accessories, Muscle m1, Muscle m2, Muscle m3) {
        List<SingleLift> lifts = List.of(accessories.single(m1), accessories.single(m2), accessories.single(m3));
        return new WorkoutLift("Accessory Circuit", new WorkoutLiftKind.CircuitKind(new CircuitLift(lifts, 3, false)));
    }

    private static WorkoutLift conditioning(RandomStack<Lift> stack, String name, int seconds, boolean deload) {
        Lift cond = stack.pop();
        if (cond == null) {
            throw new IllegalArgumentException("not enough conditioning lifts available");
        }
        return new WorkoutLift(name, new WorkoutLiftKind.SingleKind(
                new SingleLift(cond, new SetMetric.TimeSecs(seconds), null, null, null, deload)
        ));
    }

    private static Workout buildLowerMaxDay(
            int weekNumber,
            List<Lift> lowerPlan,
            RandomStack<Lift> conditioning,
            WarmupStacks warmups,
            AccessoryStacks accessories
    ) {
        Lift lower = lowerPlan.get(weekNumber);
        List<WorkoutLift> lifts = new ArrayList<>();
        lifts.add(warmups.warmup(LiftRegion.LOWER));

        if (isDeloadWeek(weekNumber)) {
            lifts.addAll(repeatedSingles("Deload Technique", lower, 3, new SetMetric.Reps(3), 70, 6.0f, true, null));
            lifts.add(new WorkoutLift("Deload Circuit", new WorkoutLiftKind.CircuitKind(new CircuitLift(
                    List.of(accessories.single(Muscle.HAMSTRING), accessories.single(Muscle.QUAD), accessories.single(Muscle.CORE)),
                    2,
                    false
            ))));
            lifts.add(conditioning(conditioning, "Light Conditioning", 300, true));
        } else {
            lifts.add(maxEffortSingle(lower));
            lifts.addAll(repeatedSingles("Backoff Sets", lower, 2, new SetMetric.Reps(5), 70, 7.0f, false, null));
            Lift next = lowerPlan.get((weekNumber + 1) % lowerPlan.size());
            lifts.addAll(repeatedSingles("Supplemental Sets", next, 3, new SetMetric.Reps(5), 80, null, false, null));
            lifts.add(accessoryCircuit(accessories, Muscle.HAMSTRING, Muscle.QUAD, Muscle.CALF));
            lifts.add(conditioning(conditioning, "Conditioning", 600, false));
            SingleLift forearm = accessories.forearm();
            if (forearm != null) {
                lifts.add(new WorkoutLift("Forearm Finisher", new WorkoutLiftKind.SingleKind(forearm)));
            }
        }
        return new Workout(lifts);
    }

    private static Workout buildUpperMaxDay(
            int weekNumber,
            List<Lift> upperPlan,
            RandomStack<Lift> conditioning,
            WarmupStacks warmups,
            AccessoryStacks accessories
    ) {
        Lift upper = upperPlan.get(weekNumber);
        List<WorkoutLift> lifts = new ArrayList<>();
        lifts.add(warmups.warmup(LiftRegion.UPPER));

        if (isDeloadWeek(weekNumber)) {
            lifts.addAll(repeatedSingles("Deload Technique", upper, 3, new SetMetric.Reps(3), 70, 6.0f, true, null));
            lifts.add(new WorkoutLift("Deload Circuit", new WorkoutLiftKind.CircuitKind(new CircuitLift(
                    List.of(accessories.single(Muscle.LAT), accessories.single(Muscle.TRICEP), accessories.single(Muscle.CORE)),
                    2,
                    false
            ))));
            lifts.add(conditioning(conditioning, "Light Conditioning", 300, true));
        } else {
            lifts.add(maxEffortSingle(upper));
            lifts.addAll(repeatedSingles("Backoff Sets", upper, 2, new SetMetric.Reps(5), 70, 7.0f, false, null));
            Lift next = upperPlan.get((weekNumber + 1) % upperPlan.size());
            lifts.addAll(repeatedSingles("Supplemental Sets", next, 3, new SetMetric.Reps(5), 80, null, false, null));

            Muscle[] upperOpts = {Muscle.REAR_DELT, Muscle.SHOULDER, Muscle.FRONT_DELT, Muscle.TRAP};
            Muscle third = upperOpts[new Random().nextInt(upperOpts.length)];
            lifts.add(accessoryCircuit(accessories, Muscle.LAT, Muscle.TRICEP, third));
            lifts.add(conditioning(conditioning, "Conditioning", 600, false));
            SingleLift forearm = accessories.forearm();
            if (forearm != null) {
                lifts.add(new WorkoutLift("Forearm Finisher", new WorkoutLiftKind.SingleKind(forearm)));
            }
        }
        return new Workout(lifts);
    }

    private static int[] dynamicPlan(int weekNumber, AccommodatingResistance resistedAr) {
        return switch (weekNumber % 6) {
            case 0 -> new int[]{60, AccommodatingResistance.STRAIGHT.ordinal()};
            case 1 -> new int[]{65, AccommodatingResistance.STRAIGHT.ordinal()};
            case 2 -> new int[]{70, AccommodatingResistance.STRAIGHT.ordinal()};
            case 3 -> new int[]{50, resistedAr.ordinal()};
            case 4 -> new int[]{55, resistedAr.ordinal()};
            default -> new int[]{60, resistedAr.ordinal()};
        };
    }

    private static Workout buildLowerDynamicDay(int weekNumber, DynamicLifts deLifts, RandomStack<Lift> conditioning, WarmupStacks warmups, AccessoryStacks accessories) {
        List<WorkoutLift> lifts = new ArrayList<>();
        lifts.add(warmups.warmup(LiftRegion.LOWER));

        if (isDeloadWeek(weekNumber)) {
            lifts.addAll(repeatedSingles("Deload Speed Work", deLifts.squat().lift(), 3, new SetMetric.Reps(3), 50, null, true, AccommodatingResistance.STRAIGHT));
            lifts.addAll(repeatedSingles("Deload Speed Work", deLifts.deadlift().lift(), 3, new SetMetric.Reps(2), 50, null, true, AccommodatingResistance.STRAIGHT));
            lifts.add(new WorkoutLift("Deload Circuit", new WorkoutLiftKind.CircuitKind(new CircuitLift(
                    List.of(accessories.single(Muscle.HAMSTRING), accessories.single(Muscle.QUAD), accessories.single(Muscle.CORE)),
                    2,
                    false
            ))));
            lifts.add(conditioning(conditioning, "Light Conditioning", 300, true));
            return new Workout(lifts);
        }

        int[] squatPlan = dynamicPlan(weekNumber, deLifts.squat().ar());
        lifts.addAll(repeatedSingles("Dynamic Effort", deLifts.squat().lift(), 12, new SetMetric.Reps(2), squatPlan[0], null, false, AccommodatingResistance.values()[squatPlan[1]]));
        int[] deadPlan = dynamicPlan(weekNumber, deLifts.deadlift().ar());
        lifts.addAll(repeatedSingles("Dynamic Effort", deLifts.deadlift().lift(), 8, new SetMetric.Reps(1), deadPlan[0], null, false, AccommodatingResistance.values()[deadPlan[1]]));
        lifts.add(accessoryCircuit(accessories, Muscle.HAMSTRING, Muscle.QUAD, Muscle.CALF));
        lifts.add(conditioning(conditioning, "Conditioning", 600, false));
        SingleLift forearm = accessories.forearm();
        if (forearm != null) {
            lifts.add(new WorkoutLift("Forearm Finisher", new WorkoutLiftKind.SingleKind(forearm)));
        }
        return new Workout(lifts);
    }

    private static Workout buildUpperDynamicDay(int weekNumber, DynamicLifts deLifts, RandomStack<Lift> conditioning, WarmupStacks warmups, AccessoryStacks accessories) {
        List<WorkoutLift> lifts = new ArrayList<>();
        lifts.add(warmups.warmup(LiftRegion.UPPER));

        if (isDeloadWeek(weekNumber)) {
            lifts.addAll(repeatedSingles("Deload Speed Work", deLifts.bench().lift(), 5, new SetMetric.Reps(3), 50, null, true, AccommodatingResistance.STRAIGHT));
            lifts.addAll(repeatedSingles("Deload Speed Work", deLifts.overhead().lift(), 3, new SetMetric.Reps(2), 50, null, true, AccommodatingResistance.STRAIGHT));
            lifts.add(new WorkoutLift("Deload Circuit", new WorkoutLiftKind.CircuitKind(new CircuitLift(
                    List.of(accessories.single(Muscle.LAT), accessories.single(Muscle.TRICEP), accessories.single(Muscle.CORE)),
                    2,
                    false
            ))));
            lifts.add(conditioning(conditioning, "Light Conditioning", 300, true));
            return new Workout(lifts);
        }

        int[] benchPlan = dynamicPlan(weekNumber, deLifts.bench().ar());
        lifts.addAll(repeatedSingles("Dynamic Effort", deLifts.bench().lift(), 9, new SetMetric.Reps(3), benchPlan[0], null, false, AccommodatingResistance.values()[benchPlan[1]]));
        int[] overheadPlan = dynamicPlan(weekNumber, deLifts.overhead().ar());
        lifts.addAll(repeatedSingles("Dynamic Effort", deLifts.overhead().lift(), 6, new SetMetric.Reps(2), overheadPlan[0], null, false, AccommodatingResistance.values()[overheadPlan[1]]));
        lifts.add(accessoryCircuit(accessories, Muscle.LAT, Muscle.TRICEP, Muscle.BICEP));
        lifts.add(conditioning(conditioning, "Conditioning", 600, false));
        SingleLift forearm = accessories.forearm();
        if (forearm != null) {
            lifts.add(new WorkoutLift("Forearm Finisher", new WorkoutLiftKind.SingleKind(forearm)));
        }
        return new Workout(lifts);
    }

    @Override
    public List<Map<DayOfWeek, Workout>> getWave(int numWeeks, Database db) throws Exception {
        MaxEffortLiftPools pools = new MaxEffortLiftPools(numWeeks, db);
        DynamicLifts dynamicLifts = DynamicLifts.fromDatabase(db);

        List<Lift> lowerCondLifts = db.liftsByRegionAndType(LiftRegion.LOWER, LiftType.CONDITIONING);
        List<Lift> upperCondLifts = db.liftsByRegionAndType(LiftRegion.UPPER, LiftType.CONDITIONING);
        if (lowerCondLifts.isEmpty() || upperCondLifts.isEmpty()) {
            throw new IllegalArgumentException("not enough conditioning lifts available");
        }

        RandomStack<Lift> lowerConditioning = new RandomStack<>(lowerCondLifts);
        RandomStack<Lift> upperConditioning = new RandomStack<>(upperCondLifts);
        WarmupStacks warmups = new WarmupStacks(db);
        AccessoryStacks accessories = new AccessoryStacks(db);

        List<Map<DayOfWeek, Workout>> weeks = new ArrayList<>();
        for (int week = 0; week < numWeeks; week++) {
            Map<DayOfWeek, Workout> workoutWeek = new EnumMap<>(DayOfWeek.class);
            workoutWeek.put(DayOfWeek.MONDAY, buildLowerMaxDay(week, pools.lowerWeeks(), lowerConditioning, warmups, accessories));
            workoutWeek.put(DayOfWeek.TUESDAY, buildUpperMaxDay(week, pools.upperWeeks(), upperConditioning, warmups, accessories));
            workoutWeek.put(DayOfWeek.THURSDAY, buildLowerDynamicDay(week, dynamicLifts, lowerConditioning, warmups, accessories));
            workoutWeek.put(DayOfWeek.FRIDAY, buildUpperDynamicDay(week, dynamicLifts, upperConditioning, warmups, accessories));
            weeks.add(workoutWeek);
        }
        return weeks;
    }
}
