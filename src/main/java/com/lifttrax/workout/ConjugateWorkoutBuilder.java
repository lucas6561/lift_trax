package com.lifttrax.workout;

import com.lifttrax.db.Database;
import com.lifttrax.models.Lift;
import com.lifttrax.models.LiftRegion;
import com.lifttrax.models.LiftType;
import com.lifttrax.models.Muscle;
import com.lifttrax.models.SetMetric;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Core ConjugateWorkoutBuilder component used by LiftTrax.
 */

public class ConjugateWorkoutBuilder implements WorkoutBuilder {
    private final MaxEffortPlanSource maxEffortPlanSource;
    private final DynamicLiftSource dynamicLiftSource;
    private final RandomSupport.Randomizer randomizer;

    public ConjugateWorkoutBuilder() {
        this(new SwingMaxEffortPlanSource(), new SwingDynamicLiftSource(), RandomSupport.DEFAULT);
    }

    public ConjugateWorkoutBuilder(MaxEffortPlanSource maxEffortPlanSource) {
        this(maxEffortPlanSource, new DefaultDynamicLiftSource(), RandomSupport.DEFAULT);
    }

    public ConjugateWorkoutBuilder(MaxEffortPlanSource maxEffortPlanSource, DynamicLiftSource dynamicLiftSource) {
        this(maxEffortPlanSource, dynamicLiftSource, RandomSupport.DEFAULT);
    }

    ConjugateWorkoutBuilder(MaxEffortPlanSource maxEffortPlanSource, DynamicLiftSource dynamicLiftSource, RandomSupport.Randomizer randomizer) {
        this.maxEffortPlanSource = maxEffortPlanSource;
        this.dynamicLiftSource = dynamicLiftSource;
        this.randomizer = randomizer;
    }

    private static WorkoutLift maxEffortSingle(Lift lift) {
        return new WorkoutLift("Max Effort Single (target 95% of max; go higher only if it feels great)", new WorkoutLiftKind.SingleKind(
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
        List<SingleLift> lifts = constrainedCircuit(accessories, List.of(m1, m2, m3), false);
        return new WorkoutLift("Accessory Circuit", new WorkoutLiftKind.CircuitKind(new CircuitLift(lifts, 3, false)));
    }

    private static WorkoutLift deloadCircuit(AccessoryStacks accessories, Muscle m1, Muscle m2, Muscle m3) {
        List<SingleLift> lifts = constrainedCircuit(accessories, List.of(m1, m2, m3), true);
        return new WorkoutLift("Deload Circuit", new WorkoutLiftKind.CircuitKind(new CircuitLift(lifts, 2, false)));
    }

    private static List<SingleLift> constrainedCircuit(AccessoryStacks accessories, List<Muscle> muscles, boolean deload) {
        List<Lift> selected = new ArrayList<>();
        int cableCount = 0;
        int dbCount = 0;

        for (Muscle muscle : muscles) {
            List<Lift> rejected = new ArrayList<>();
            Lift pick;
            while (true) {
                pick = accessories.pop(muscle);
                String name = pick.name().toLowerCase();
                boolean isCable = name.contains("cable");
                boolean isDb = isDbLike(name);
                if ((isCable && cableCount > 0) || (isDb && dbCount > 0)) {
                    rejected.add(pick);
                    if (rejected.size() > 25) {
                        throw new IllegalArgumentException("unable to build circuit without duplicate cable/db exercises");
                    }
                    continue;
                }
                if (isCable) cableCount++;
                if (isDb) dbCount++;
                break;
            }
            for (Lift lift : rejected) {
                accessories.putBack(muscle, lift);
            }
            selected.add(pick);
        }

        List<SingleLift> lifts = new ArrayList<>();
        for (Lift lift : selected) {
            SingleLift single = new SingleLift(lift, new SetMetric.RepsRange(8, 12), null, null, null, false);
            lifts.add(deload ? markDeload(single) : single);
        }
        return Collections.unmodifiableList(lifts);
    }

    private static boolean isDbLike(String name) {
        return name.contains("db") || name.contains("dumbbell") || name.contains("dumb bell");
    }

    private static SingleLift markDeload(SingleLift lift) {
        return new SingleLift(lift.lift(), lift.metric(), lift.percent(), lift.rpe(), lift.accommodatingResistance(), true);
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
            List<MaxEffortPlan.DeloadLowerLifts> lowerDeload,
            RandomStack<Lift> conditioning,
            WarmupStacks warmups,
            AccessoryStacks accessories
    ) {
        Lift lower = lowerPlan.get(weekNumber);
        List<WorkoutLift> lifts = new ArrayList<>();
        lifts.add(warmups.warmup(LiftRegion.LOWER));

        if (isDeloadWeek(weekNumber)) {
            int deloadIdx = weekNumber / 7;
            if (deloadIdx < lowerDeload.size()) {
                lifts.addAll(repeatedSingles("Deload Technique", lowerDeload.get(deloadIdx).squat(), 3, new SetMetric.Reps(3), 70, 6.0f, true, null));
                lifts.addAll(repeatedSingles("Deload Technique", lowerDeload.get(deloadIdx).deadlift(), 3, new SetMetric.Reps(3), 70, 6.0f, true, null));
            } else {
                lifts.addAll(repeatedSingles("Deload Technique", lower, 3, new SetMetric.Reps(3), 70, 6.0f, true, null));
            }
            lifts.add(deloadCircuit(accessories, Muscle.HAMSTRING, Muscle.QUAD, Muscle.CORE));
            lifts.add(conditioning(conditioning, "Light Conditioning", 300, true));
        } else {
            lifts.add(maxEffortSingle(lower));
            lifts.addAll(repeatedSingles("Backoff Sets (log top single; use 80-85% of today's 1RM)", lower, 2, new SetMetric.Reps(5), 80, null, false, null));
            Lift next = lowerPlan.get((weekNumber + 1) % lowerPlan.size());
            lifts.addAll(repeatedSingles("Supplemental Sets", next, 2, new SetMetric.Reps(5), 85, null, false, null));
            lifts.add(accessoryCircuit(accessories, Muscle.HAMSTRING, Muscle.QUAD, Muscle.CALF));
            lifts.add(conditioning(conditioning, "Conditioning", 600, false));
            SingleLift forearm = accessories.forearm();
            if (forearm != null) {
                lifts.add(new WorkoutLift("Forearm Finisher", new WorkoutLiftKind.SingleKind(forearm)));
            }
        }
        return new Workout(lifts);
    }

    private Workout buildUpperMaxDay(
            int weekNumber,
            List<Lift> upperPlan,
            List<MaxEffortPlan.DeloadUpperLifts> upperDeload,
            RandomStack<Lift> conditioning,
            WarmupStacks warmups,
            AccessoryStacks accessories
    ) {
        Lift upper = upperPlan.get(weekNumber);
        List<WorkoutLift> lifts = new ArrayList<>();
        lifts.add(warmups.warmup(LiftRegion.UPPER));

        if (isDeloadWeek(weekNumber)) {
            int deloadIdx = weekNumber / 7;
            if (deloadIdx < upperDeload.size()) {
                lifts.addAll(repeatedSingles("Deload Technique", upperDeload.get(deloadIdx).bench(), 3, new SetMetric.Reps(3), 70, 6.0f, true, null));
                lifts.addAll(repeatedSingles("Deload Technique", upperDeload.get(deloadIdx).overhead(), 3, new SetMetric.Reps(3), 70, 6.0f, true, null));
            } else {
                lifts.addAll(repeatedSingles("Deload Technique", upper, 3, new SetMetric.Reps(3), 70, 6.0f, true, null));
            }
            lifts.add(deloadCircuit(accessories, Muscle.LAT, Muscle.TRICEP, Muscle.CORE));
            lifts.add(conditioning(conditioning, "Light Conditioning", 300, true));
        } else {
            lifts.add(maxEffortSingle(upper));
            lifts.addAll(repeatedSingles("Backoff Sets (log top single; use 80-85% of today's 1RM)", upper, 2, new SetMetric.Reps(5), 80, null, false, null));
            Lift next = upperPlan.get((weekNumber + 1) % upperPlan.size());
            lifts.addAll(repeatedSingles("Supplemental Sets", next, 2, new SetMetric.Reps(5), 85, null, false, null));

            Muscle[] upperOpts = {Muscle.REAR_DELT, Muscle.SHOULDER, Muscle.FRONT_DELT, Muscle.TRAP};
            Muscle third = upperOpts[randomizer.nextInt(new Random(), upperOpts.length)];
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
            lifts.add(deloadCircuit(accessories, Muscle.HAMSTRING, Muscle.QUAD, Muscle.CORE));
            lifts.add(conditioning(conditioning, "Light Conditioning", 300, true));
            return new Workout(lifts);
        }

        int[] squatPlan = dynamicPlan(weekNumber, deLifts.squat().ar());
        lifts.addAll(repeatedSingles("Dynamic Effort", deLifts.squat().lift(), 6, new SetMetric.Reps(3), squatPlan[0], null, false, AccommodatingResistance.values()[squatPlan[1]]));
        int[] deadPlan = dynamicPlan(weekNumber, deLifts.deadlift().ar());
        lifts.addAll(repeatedSingles("Dynamic Effort", deLifts.deadlift().lift(), 6, new SetMetric.Reps(2), deadPlan[0], null, false, AccommodatingResistance.values()[deadPlan[1]]));
        lifts.add(accessoryCircuit(accessories, Muscle.HAMSTRING, Muscle.QUAD, Muscle.CORE));
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
            lifts.add(deloadCircuit(accessories, Muscle.LAT, Muscle.TRICEP, Muscle.CORE));
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
        MaxEffortLiftPools pools = new MaxEffortLiftPools(numWeeks, db, randomizer);
        MaxEffortPlan maxEffortPlan = maxEffortPlanSource.selectPlan(db, pools);

        DynamicLifts dynamicLifts = dynamicLiftSource.select(db);

        List<Lift> lowerCondLifts = db.liftsByRegionAndType(LiftRegion.LOWER, LiftType.CONDITIONING);
        List<Lift> upperCondLifts = db.liftsByRegionAndType(LiftRegion.UPPER, LiftType.CONDITIONING);
        if (lowerCondLifts.isEmpty() || upperCondLifts.isEmpty()) {
            throw new IllegalArgumentException("not enough conditioning lifts available");
        }

        RandomStack<Lift> lowerConditioning = new RandomStack<>(lowerCondLifts, randomizer);
        RandomStack<Lift> upperConditioning = new RandomStack<>(upperCondLifts, randomizer);
        WarmupStacks warmups = new WarmupStacks(db, randomizer);
        AccessoryStacks accessories = new AccessoryStacks(db, randomizer);

        List<Map<DayOfWeek, Workout>> weeks = new ArrayList<>();
        for (int week = 0; week < numWeeks; week++) {
            Map<DayOfWeek, Workout> workoutWeek = new EnumMap<>(DayOfWeek.class);
            workoutWeek.put(DayOfWeek.MONDAY, buildLowerMaxDay(week, maxEffortPlan.lower(), maxEffortPlan.lowerDeload(), lowerConditioning, warmups, accessories));
            workoutWeek.put(DayOfWeek.TUESDAY, buildUpperMaxDay(week, maxEffortPlan.upper(), maxEffortPlan.upperDeload(), upperConditioning, warmups, accessories));
            workoutWeek.put(DayOfWeek.THURSDAY, buildLowerDynamicDay(week, dynamicLifts, lowerConditioning, warmups, accessories));
            workoutWeek.put(DayOfWeek.FRIDAY, buildUpperDynamicDay(week, dynamicLifts, upperConditioning, warmups, accessories));
            weeks.add(workoutWeek);
        }
        return weeks;
    }
}
