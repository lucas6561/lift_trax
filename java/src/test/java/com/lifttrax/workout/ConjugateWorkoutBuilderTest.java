package com.lifttrax.workout;

import com.lifttrax.db.Database;
import com.lifttrax.models.Lift;
import com.lifttrax.models.LiftExecution;
import com.lifttrax.models.LiftRegion;
import com.lifttrax.models.LiftStats;
import com.lifttrax.models.LiftType;
import com.lifttrax.models.Muscle;
import com.lifttrax.models.SetMetric;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConjugateWorkoutBuilderTest {

    @Test
    void buildsExpectedDaysForWave() throws Exception {
        FakeDb db = FakeDb.withSeedData();
        ConjugateWorkoutBuilder builder = new ConjugateWorkoutBuilder();

        var wave = builder.getWave(2, db);

        assertEquals(2, wave.size());
        for (var week : wave) {
            assertTrue(week.containsKey(DayOfWeek.MONDAY));
            assertTrue(week.containsKey(DayOfWeek.TUESDAY));
            assertTrue(week.containsKey(DayOfWeek.THURSDAY));
            assertTrue(week.containsKey(DayOfWeek.FRIDAY));
        }
    }

    @Test
    void lowerDynamicDayUsesRustSetRepAndPercentScheme() throws Exception {
        FakeDb db = FakeDb.withSeedData();
        var week = new ConjugateWorkoutBuilder().getWave(1, db).get(0);

        Workout thu = week.get(DayOfWeek.THURSDAY);
        assertNotNull(thu);

        long deSquatSets = thu.lifts().stream()
                .filter(l -> l.kind() instanceof WorkoutLiftKind.SingleKind sk
                        && l.name().equals("Dynamic Effort")
                        && sk.singleLift().metric() instanceof SetMetric.Reps reps
                        && reps.reps() == 3)
                .count();
        long deDeadSets = thu.lifts().stream()
                .filter(l -> l.kind() instanceof WorkoutLiftKind.SingleKind sk
                        && l.name().equals("Dynamic Effort")
                        && sk.singleLift().metric() instanceof SetMetric.Reps reps
                        && reps.reps() == 2)
                .count();

        assertEquals(6, deSquatSets);
        assertEquals(6, deDeadSets);

        // week 1 in the 6-week DE cycle should be straight weight @60%
        assertTrue(thu.lifts().stream()
                .filter(l -> l.kind() instanceof WorkoutLiftKind.SingleKind && l.name().equals("Dynamic Effort"))
                .allMatch(l -> {
                    var s = ((WorkoutLiftKind.SingleKind) l.kind()).singleLift();
                    return Integer.valueOf(60).equals(s.percent());
                }));
    }

    @Test
    void markdownWriterIncludesWeekHeaders() throws Exception {
        FakeDb db = FakeDb.withSeedData();
        var wave = new ConjugateWorkoutBuilder().getWave(1, db);

        List<String> markdown = WaveMarkdownWriter.createMarkdown(wave);

        assertTrue(markdown.stream().anyMatch(line -> line.equals("# Week 1")));
        assertTrue(markdown.stream().anyMatch(line -> line.equals("## Monday")));
    }

    static class FakeDb implements Database {
        private final List<Lift> lifts = new ArrayList<>();

        static FakeDb withSeedData() {
            FakeDb db = new FakeDb();

            db.add("Back Squat", LiftRegion.LOWER, LiftType.SQUAT, List.of());
            db.add("Front Squat", LiftRegion.LOWER, LiftType.SQUAT, List.of());
            db.add("Conventional Deadlift", LiftRegion.LOWER, LiftType.DEADLIFT, List.of());
            db.add("Sumo Deadlift", LiftRegion.LOWER, LiftType.DEADLIFT, List.of());
            db.add("Bench Press", LiftRegion.UPPER, LiftType.BENCH_PRESS, List.of());
            db.add("Floor Press", LiftRegion.UPPER, LiftType.BENCH_PRESS, List.of());
            db.add("Overhead Press", LiftRegion.UPPER, LiftType.OVERHEAD_PRESS, List.of());
            db.add("Push Press", LiftRegion.UPPER, LiftType.OVERHEAD_PRESS, List.of());

            db.add("Sled Push", LiftRegion.LOWER, LiftType.CONDITIONING, List.of());
            db.add("Bike", LiftRegion.UPPER, LiftType.CONDITIONING, List.of());
            db.add("Leg Swings", LiftRegion.LOWER, LiftType.MOBILITY, List.of());
            db.add("Shoulder CARs", LiftRegion.UPPER, LiftType.MOBILITY, List.of());

            db.add("Lower Accessory 1", LiftRegion.LOWER, LiftType.ACCESSORY, List.of(Muscle.GLUTE));
            db.add("Lower Accessory 2", LiftRegion.LOWER, LiftType.ACCESSORY, List.of(Muscle.GLUTE));
            db.add("Upper Accessory 1", LiftRegion.UPPER, LiftType.ACCESSORY, List.of(Muscle.CHEST));
            db.add("Upper Accessory 2", LiftRegion.UPPER, LiftType.ACCESSORY, List.of(Muscle.CHEST));

            db.add("Hamstring Curl", LiftRegion.LOWER, LiftType.ACCESSORY, List.of(Muscle.HAMSTRING));
            db.add("Leg Extension", LiftRegion.LOWER, LiftType.ACCESSORY, List.of(Muscle.QUAD));
            db.add("Calf Raise", LiftRegion.LOWER, LiftType.ACCESSORY, List.of(Muscle.CALF));
            db.add("Lat Pulldown", LiftRegion.UPPER, LiftType.ACCESSORY, List.of(Muscle.LAT));
            db.add("Tricep Pushdown", LiftRegion.UPPER, LiftType.ACCESSORY, List.of(Muscle.TRICEP));
            db.add("Rear Delt Fly", LiftRegion.UPPER, LiftType.ACCESSORY, List.of(Muscle.REAR_DELT));
            db.add("Lateral Raise", LiftRegion.UPPER, LiftType.ACCESSORY, List.of(Muscle.SHOULDER));
            db.add("Front Raise", LiftRegion.UPPER, LiftType.ACCESSORY, List.of(Muscle.FRONT_DELT));
            db.add("DB Shrug", LiftRegion.UPPER, LiftType.ACCESSORY, List.of(Muscle.TRAP));
            db.add("Plank", LiftRegion.LOWER, LiftType.ACCESSORY, List.of(Muscle.CORE));
            db.add("Deadbug", LiftRegion.LOWER, LiftType.ACCESSORY, List.of(Muscle.CORE));
            db.add("Curl", LiftRegion.UPPER, LiftType.ACCESSORY, List.of(Muscle.BICEP));
            db.add("Wrist Curl", LiftRegion.UPPER, LiftType.ACCESSORY, List.of(Muscle.FOREARM));
            return db;
        }

        void add(String name, LiftRegion region, LiftType type, List<Muscle> muscles) {
            lifts.add(new Lift(name, region, type, muscles, ""));
        }

        @Override
        public List<Lift> liftsByType(LiftType liftType) {
            return lifts.stream().filter(l -> l.main() == liftType).toList();
        }

        @Override
        public List<Lift> getAccessoriesByMuscle(Muscle muscle) {
            return lifts.stream().filter(l -> l.main() == LiftType.ACCESSORY && l.muscles().contains(muscle)).toList();
        }

        @Override
        public List<Lift> liftsByRegionAndType(LiftRegion region, LiftType liftType) {
            return lifts.stream().filter(l -> l.region() == region && l.main() == liftType).toList();
        }

        @Override
        public Lift getLift(String name) {
            return lifts.stream().filter(l -> l.name().equals(name)).findFirst().orElseThrow();
        }

        @Override public void addLift(String name, LiftRegion region, LiftType main, List<Muscle> muscles, String notes) {}
        @Override public void addLiftExecution(String name, LiftExecution execution) {}
        @Override public void updateLift(String currentName, String newName, LiftRegion region, LiftType main, List<Muscle> muscles, String notes) {}
        @Override public void deleteLift(String name) {}
        @Override public List<LiftExecution> getExecutions(String liftName) { return List.of(); }
        @Override public void updateLiftExecution(int execId, LiftExecution execution) {}
        @Override public void deleteLiftExecution(int execId) {}
        @Override public LiftStats liftStats(String name) { throw new UnsupportedOperationException(); }
        @Override public List<Lift> listLifts() { return List.copyOf(lifts); }
    }
}
