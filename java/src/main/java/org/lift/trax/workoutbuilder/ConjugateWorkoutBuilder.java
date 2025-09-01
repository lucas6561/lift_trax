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
import org.lift.trax.Muscle;

public class ConjugateWorkoutBuilder implements WorkoutBuilder {

    private static class MaxEffortLiftPools {
        List<Lift> lowerWeeks;
        List<Lift> upperWeeks;
        int numWeeks;

        static MaxEffortLiftPools create(int numWeeks, Database db) throws Exception {
            List<Lift> squats = db.liftsByType(LiftType.SQUAT);
            List<Lift> deadlifts = db.liftsByType(LiftType.DEADLIFT);
            List<Lift> benches = db.liftsByType(LiftType.BENCH_PRESS);
            List<Lift> overheads = db.liftsByType(LiftType.OVERHEAD_PRESS);

            int squatWeeks = (numWeeks + 1) / 2;
            int deadWeeks = numWeeks / 2;
            int benchWeeks = (numWeeks + 1) / 2;
            int ohpWeeks = numWeeks / 2;

            if (squats.size() < squatWeeks) throw new Exception("not enough squat lifts available");
            if (deadlifts.size() < deadWeeks) throw new Exception("not enough deadlift lifts available");
            if (benches.size() < benchWeeks) throw new Exception("not enough bench press lifts available");
            if (overheads.size() < ohpWeeks) throw new Exception("not enough overhead press lifts available");

            Collections.shuffle(squats);
            Collections.shuffle(deadlifts);
            Collections.shuffle(benches);
            Collections.shuffle(overheads);

            MaxEffortLiftPools p = new MaxEffortLiftPools();
            p.numWeeks = numWeeks;
            p.lowerWeeks = new ArrayList<>(numWeeks);
            p.upperWeeks = new ArrayList<>(numWeeks);
            int squatIdx = 0, deadIdx = 0, benchIdx = 0, ohpIdx = 0;
            for (int i = 0; i < numWeeks; i++) {
                if (i % 2 == 0) {
                    p.lowerWeeks.add(squats.get(squatIdx++));
                    p.upperWeeks.add(benches.get(benchIdx++));
                } else {
                    p.lowerWeeks.add(deadlifts.get(deadIdx++));
                    p.upperWeeks.add(overheads.get(ohpIdx++));
                }
            }
            return p;
        }

        Lift lowerForWeek(int weekIdx) {
            return lowerWeeks.get(weekIdx);
        }

        Lift upperForWeek(int weekIdx) {
            return upperWeeks.get(weekIdx);
        }

        int numWeeks() {
            return numWeeks;
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
        return new SingleLift(lift, null, null, null);
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

    private static List<WorkoutLift> supplementalSets(Lift lift) {
        return List.of(
                new SingleLift(lift, SetMetric.reps(5), 80, null),
                new SingleLift(lift, SetMetric.reps(5), 80, null),
                new SingleLift(lift, SetMetric.reps(5), 80, null));
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
        return new CircuitLift(lifts, 60, 3);
    }

    private static SingleLift accessoryLift(List<Lift> allLifts, Muscle muscle, Random rng) throws Exception {
        List<Lift> matches = new ArrayList<>();
        for (Lift lift : allLifts) {
            if (lift.main == LiftType.ACCESSORY && lift.muscles.contains(muscle)) {
                matches.add(lift);
            }
        }
        if (matches.isEmpty()) {
            throw new Exception("not enough accessory lifts available for " + muscle.toString());
        }
        Lift lift = matches.get(rng.nextInt(matches.size()));
        int reps = rng.nextInt(3) + 10;
        return new SingleLift(lift, SetMetric.reps(reps), null, null);
    }

    private static WorkoutLift accessoryCircuit(Muscle m1, Muscle m2, Muscle m3, Database db) throws Exception {
        List<Lift> all = db.listLifts(null);
        Random rng = new Random();
        List<SingleLift> lifts = List.of(
                accessoryLift(all, m1, rng),
                accessoryLift(all, m2, rng),
                accessoryLift(all, m3, rng));
        return new CircuitLift(lifts, 60, 3);
    }

    private static WorkoutWeek buildWeek(int i, MaxEffortLiftPools meLifts, DynamicLifts deLifts, Database db) throws Exception {
        WorkoutWeek week = new WorkoutWeek();

        Lift lower = meLifts.lowerForWeek(i);
        List<WorkoutLift> monLifts = new ArrayList<>();
        monLifts.add(warmup(LiftRegion.LOWER, db));
        monLifts.add(single(lower));
        monLifts.addAll(backoffSets(lower));
        Lift nextLower = meLifts.lowerForWeek((i + 1) % meLifts.numWeeks());
        monLifts.addAll(supplementalSets(nextLower));
        monLifts.add(accessoryCircuit(Muscle.HAMSTRING, Muscle.QUAD, Muscle.CALF, db));
        week.put(DayOfWeek.MONDAY, new Workout(monLifts));

        Lift upper = meLifts.upperForWeek(i);
        List<WorkoutLift> tueLifts = new ArrayList<>();
        tueLifts.add(warmup(LiftRegion.UPPER, db));
        tueLifts.add(single(upper));
        tueLifts.addAll(backoffSets(upper));
        Lift nextUpper = meLifts.upperForWeek((i + 1) % meLifts.numWeeks());
        tueLifts.addAll(supplementalSets(nextUpper));
        Muscle[] upperOpts = new Muscle[] {Muscle.REAR_DELT, Muscle.SHOULDER, Muscle.FRONT_DELT, Muscle.TRAP};
        Muscle third = upperOpts[new Random().nextInt(upperOpts.length)];
        tueLifts.add(accessoryCircuit(Muscle.LAT, Muscle.TRICEP, third, db));
        week.put(DayOfWeek.TUESDAY, new Workout(tueLifts));

        int percent = 50 + i * 5;
        List<WorkoutLift> thuLifts = new ArrayList<>();
        thuLifts.add(warmup(LiftRegion.LOWER, db));
        thuLifts.add(new SingleLift(deLifts.squat.lift, null, percent, deLifts.squat.ar));
        thuLifts.add(new SingleLift(deLifts.deadlift.lift, null, percent, deLifts.deadlift.ar));
        thuLifts.add(accessoryCircuit(Muscle.HAMSTRING, Muscle.QUAD, Muscle.CORE, db));
        week.put(DayOfWeek.THURSDAY, new Workout(thuLifts));

        List<WorkoutLift> friLifts = new ArrayList<>();
        friLifts.add(warmup(LiftRegion.UPPER, db));
        friLifts.add(new SingleLift(deLifts.bench.lift, null, percent, deLifts.bench.ar));
        friLifts.add(new SingleLift(deLifts.overhead.lift, null, percent, deLifts.overhead.ar));
        friLifts.add(accessoryCircuit(Muscle.LAT, Muscle.TRICEP, Muscle.BICEP, db));
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

