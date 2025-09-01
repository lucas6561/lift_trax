package org.lift.trax.workoutbuilder;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.lift.trax.Database;
import org.lift.trax.Lift;
import org.lift.trax.LiftRegion;
import org.lift.trax.LiftType;

public class ConjugateWorkoutBuilder implements WorkoutBuilder {

    private static class MaxEffortLiftPools {
        List<Lift> squats;
        List<Lift> deadlifts;
        List<Lift> benches;
        List<Lift> overheads;
        int squatIdx;
        int deadIdx;
        int benchIdx;
        int ohpIdx;

        static MaxEffortLiftPools create(int numWeeks, Database db) throws Exception {
            MaxEffortLiftPools p = new MaxEffortLiftPools();
            p.squats = db.liftsByType(LiftType.SQUAT);
            p.deadlifts = db.liftsByType(LiftType.DEADLIFT);
            p.benches = db.liftsByType(LiftType.BENCH_PRESS);
            p.overheads = db.liftsByType(LiftType.OVERHEAD_PRESS);

            int squatWeeks = (numWeeks + 1) / 2;
            int deadWeeks = numWeeks / 2;
            int benchWeeks = (numWeeks + 1) / 2;
            int ohpWeeks = numWeeks / 2;

            if (p.squats.size() < squatWeeks) throw new Exception("not enough squat lifts available");
            if (p.deadlifts.size() < deadWeeks) throw new Exception("not enough deadlift lifts available");
            if (p.benches.size() < benchWeeks) throw new Exception("not enough bench press lifts available");
            if (p.overheads.size() < ohpWeeks) throw new Exception("not enough overhead press lifts available");

            Collections.shuffle(p.squats);
            Collections.shuffle(p.deadlifts);
            Collections.shuffle(p.benches);
            Collections.shuffle(p.overheads);
            return p;
        }

        Lift nextLower(int weekIdx) {
            if (weekIdx % 2 == 0) {
                return squats.get(squatIdx++);
            } else {
                return deadlifts.get(deadIdx++);
            }
        }

        Lift nextUpper(int weekIdx) {
            if (weekIdx % 2 == 0) {
                return benches.get(benchIdx++);
            } else {
                return overheads.get(ohpIdx++);
            }
        }
    }

    private static class DynamicLift {
        Lift lift;
        AccommodatingResistance ar;

        DynamicLift(Lift lift, AccommodatingResistance ar) {
            this.lift = lift;
            this.ar = ar;
        }
    }

    private static class DynamicLifts {
        DynamicLift squat;
        DynamicLift deadlift;
        DynamicLift bench;
        DynamicLift overhead;

        private static DynamicLift pick(List<Lift> lifts, AccommodatingResistance[] arOpts, Random rng) throws Exception {
            if (lifts.isEmpty()) throw new Exception("not enough lifts available");
            Lift lift = lifts.get(rng.nextInt(lifts.size()));
            AccommodatingResistance ar = arOpts[rng.nextInt(arOpts.length)];
            return new DynamicLift(lift, ar);
        }

        static DynamicLifts create(Database db) throws Exception {
            Random rng = new Random();
            AccommodatingResistance[] arOpts = AccommodatingResistance.values();

            Lift squat = db.listLifts("Squat").stream().findFirst()
                    .orElseThrow(() -> new Exception("Squat lift not found"));
            Lift deadlift = db.listLifts("Deadlift").stream().findFirst()
                    .orElseThrow(() -> new Exception("Deadlift lift not found"));
            Lift bench = db.listLifts("Bench Press").stream().findFirst()
                    .orElseThrow(() -> new Exception("Bench press lift not found"));
            Lift overhead = db.listLifts("Overhead Press").stream().findFirst()
                    .orElseThrow(() -> new Exception("Overhead press lift not found"));

            DynamicLifts d = new DynamicLifts();
            d.squat = pick(List.of(squat), arOpts, rng);
            d.deadlift = pick(List.of(deadlift), arOpts, rng);
            d.bench = pick(List.of(bench), arOpts, rng);
            d.overhead = pick(List.of(overhead), arOpts, rng);
            return d;
        }
    }

    private static SingleLift mkWarmupLift(Lift lift) {
        return new SingleLift(lift, null, 40, null);
    }

    private static WorkoutLift single(Lift lift) {
        return new SingleLift(lift, null, null, null);
    }

    private static List<WorkoutLift> backoffSets(Lift lift) {
        Random rng = new Random();
        if (rng.nextBoolean()) {
            int reps = 3 + rng.nextInt(3);
            return List.of(new SingleLift(lift, SetMetric.reps(reps), 90, null));
        } else {
            return List.of(
                    new SingleLift(lift, SetMetric.reps(3), 80, null),
                    new SingleLift(lift, SetMetric.reps(3), 80, null),
                    new SingleLift(lift, SetMetric.reps(3), 80, null));
        }
    }

    private static WorkoutLift warmup(LiftRegion region, Database db) throws Exception {
        Random rng = new Random();

        List<Lift> conds = db.liftsByType(LiftType.CONDITIONING);
        if (conds.isEmpty()) throw new Exception("not enough conditioning lifts available");
        Lift cond = conds.get(rng.nextInt(conds.size()));

        List<Lift> mobs = db.liftsByRegionAndType(region, LiftType.MOBILITY);
        if (mobs.isEmpty()) throw new Exception("not enough mobility lifts available");
        Lift mob = mobs.get(rng.nextInt(mobs.size()));

        List<Lift> accessories = db.liftsByRegionAndType(region, LiftType.ACCESSORY);
        if (accessories.size() < 2) throw new Exception("not enough accessory lifts available");
        Collections.shuffle(accessories, rng);
        Lift acc1 = accessories.get(0);
        Lift acc2 = accessories.get(1);

        List<SingleLift> lifts = List.of(
                mkWarmupLift(cond),
                mkWarmupLift(mob),
                mkWarmupLift(acc1),
                mkWarmupLift(acc2));
        return new CircuitLift(lifts, 60);
    }

    private static WorkoutWeek buildWeek(int i, MaxEffortLiftPools meLifts, DynamicLifts deLifts, Database db) throws Exception {
        WorkoutWeek week = new WorkoutWeek();

        Lift lower = meLifts.nextLower(i);
        List<WorkoutLift> monLifts = new ArrayList<>();
        monLifts.add(warmup(LiftRegion.LOWER, db));
        monLifts.add(single(lower));
        monLifts.addAll(backoffSets(lower));
        week.put(DayOfWeek.MONDAY, new Workout(monLifts));

        Lift upper = meLifts.nextUpper(i);
        List<WorkoutLift> tueLifts = new ArrayList<>();
        tueLifts.add(warmup(LiftRegion.UPPER, db));
        tueLifts.add(single(upper));
        tueLifts.addAll(backoffSets(upper));
        week.put(DayOfWeek.TUESDAY, new Workout(tueLifts));

        int percent = 50 + i * 5;
        List<WorkoutLift> thuLifts = new ArrayList<>();
        thuLifts.add(warmup(LiftRegion.LOWER, db));
        thuLifts.add(new SingleLift(deLifts.squat.lift, null, percent, deLifts.squat.ar));
        thuLifts.add(new SingleLift(deLifts.deadlift.lift, null, percent, deLifts.deadlift.ar));
        week.put(DayOfWeek.THURSDAY, new Workout(thuLifts));

        List<WorkoutLift> friLifts = new ArrayList<>();
        friLifts.add(warmup(LiftRegion.UPPER, db));
        friLifts.add(new SingleLift(deLifts.bench.lift, null, percent, deLifts.bench.ar));
        friLifts.add(new SingleLift(deLifts.overhead.lift, null, percent, deLifts.overhead.ar));
        week.put(DayOfWeek.FRIDAY, new Workout(friLifts));

        return week;
    }

    @Override
    public List<WorkoutWeek> getWave(int numWeeks, Database db) throws Exception {
        MaxEffortLiftPools mePools = MaxEffortLiftPools.create(numWeeks, db);
        DynamicLifts dynamic = DynamicLifts.create(db);
        List<WorkoutWeek> weeks = new ArrayList<>();
        for (int i = 0; i < numWeeks; i++) {
            weeks.add(buildWeek(i, mePools, dynamic, db));
        }
        return weeks;
    }
}

