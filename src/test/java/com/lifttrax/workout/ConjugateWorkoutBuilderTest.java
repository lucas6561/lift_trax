package com.lifttrax.workout;

import com.lifttrax.db.Database;
import com.lifttrax.models.ExecutionSet;
import com.lifttrax.models.Lift;
import com.lifttrax.models.LiftExecution;
import com.lifttrax.models.LiftRegion;
import com.lifttrax.models.LiftStats;
import com.lifttrax.models.LiftType;
import com.lifttrax.models.Muscle;
import com.lifttrax.models.SetMetric;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

        assertTrue(thu.lifts().stream()
                .filter(l -> l.kind() instanceof WorkoutLiftKind.SingleKind && l.name().equals("Dynamic Effort"))
                .allMatch(l -> {
                    var s = ((WorkoutLiftKind.SingleKind) l.kind()).singleLift();
                    return Integer.valueOf(60).equals(s.percent());
                }));
    }


    @Test
    void hypertrophyBuilderBuildsWaveWithHypertrophySections() throws Exception {
        FakeDb db = FakeDb.withSeedData();
        var week = new HypertrophyWorkoutBuilder().getWave(1, db).get(0);

        Workout monday = week.get(DayOfWeek.MONDAY);
        assertNotNull(monday);
        assertTrue(monday.lifts().stream().anyMatch(l -> l.name().equals("Primary Hypertrophy")));
        assertTrue(monday.lifts().stream().anyMatch(l -> l.name().equals("Secondary Hypertrophy")));

        Workout tuesday = week.get(DayOfWeek.TUESDAY);
        assertNotNull(tuesday);
        assertTrue(tuesday.lifts().stream().anyMatch(l -> l.name().equals("Accessory")));
    }

    @Test
    void markdownWriterIncludesRichFormattingAndHistory() throws Exception {
        FakeDb db = FakeDb.withSeedData();
        var wave = new ConjugateWorkoutBuilder().getWave(1, db);

        List<String> markdown = WaveMarkdownWriter.createMarkdown(wave, db);

        assertTrue(markdown.stream().anyMatch(line -> line.equals("# Week 1")));
        assertTrue(markdown.stream().anyMatch(line -> line.equals("## Monday")));
        assertTrue(markdown.stream().anyMatch(line -> line.startsWith("- Circuit: 3 rounds")));
        assertTrue(markdown.stream().anyMatch(line -> line.contains("**") && line.contains("1 reps")));
        assertTrue(markdown.stream().anyMatch(line -> line.contains("- Last:")));
    }

    @Test
    void conjugateWaveMarkdownMatchesParityFixture() throws Exception {
        FakeDb db = FakeDb.withParitySeedData();
        var wave = new ConjugateWorkoutBuilder(
                new SwingMaxEffortPlanSource(),
                new SwingDynamicLiftSource(),
                RandomSupport.DETERMINISTIC
        ).getWave(1, db);
        String markdown = String.join("\n", WaveMarkdownWriter.createMarkdown(wave, db));
        String expected = Files.readString(Path.of("testdata", "conjugate_wave_parity.md"));
        assertEquals(expected.trim(), markdown.trim());
    }

    @Test
    void circuitsAvoidDuplicateCableAndDbExercises() throws Exception {
        FakeDb db = FakeDb.withCableAndDbCircuitData();
        var wave = new ConjugateWorkoutBuilder(
                new SwingMaxEffortPlanSource(),
                new SwingDynamicLiftSource(),
                RandomSupport.DETERMINISTIC
        ).getWave(1, db);

        for (Workout day : wave.get(0).values()) {
            for (WorkoutLift workoutLift : day.lifts()) {
                if (!(workoutLift.kind() instanceof WorkoutLiftKind.CircuitKind ck)) {
                    continue;
                }
                long cable = ck.circuitLift().circuitLifts().stream()
                        .map(sl -> sl.lift().name().toLowerCase())
                        .filter(name -> name.contains("cable"))
                        .count();
                long dbCount = ck.circuitLift().circuitLifts().stream()
                        .map(sl -> sl.lift().name().toLowerCase())
                        .filter(name -> name.contains("db"))
                        .count();
                assertTrue(cable <= 1);
                assertTrue(dbCount <= 1);
            }
        }
    }

    static class FakeDb implements Database {
        private final List<Lift> lifts = new ArrayList<>();
        private final Map<String, List<LiftExecution>> executions = new HashMap<>();

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

            db.exec("Back Squat", new LiftExecution(1, LocalDate.now().minusDays(1), List.of(new ExecutionSet(new SetMetric.Reps(1), "345 lb", 9f)), false, false, ""));
            db.exec("Back Squat", new LiftExecution(2, LocalDate.now().minusDays(2), List.of(new ExecutionSet(new SetMetric.Reps(5), "245 lb", null)), false, false, ""));
            db.exec("Leg Swings", new LiftExecution(3, LocalDate.now().minusDays(1), List.of(new ExecutionSet(new SetMetric.Reps(3), "none", null)), true, false, ""));
            return db;
        }

        static FakeDb withParitySeedData() {
            FakeDb db = new FakeDb();

            db.add("Squat", LiftRegion.LOWER, LiftType.SQUAT, List.of());
            db.add("Deadlift", LiftRegion.LOWER, LiftType.DEADLIFT, List.of());
            db.add("Conventional Deadlift", LiftRegion.LOWER, LiftType.DEADLIFT, List.of());
            db.add("Bench Press", LiftRegion.UPPER, LiftType.BENCH_PRESS, List.of());
            db.add("Overhead Press", LiftRegion.UPPER, LiftType.OVERHEAD_PRESS, List.of());
            db.add("Sled Push", LiftRegion.LOWER, LiftType.CONDITIONING, List.of());
            db.add("Battle Rope", LiftRegion.UPPER, LiftType.CONDITIONING, List.of());
            db.add("Hip Airplane", LiftRegion.LOWER, LiftType.MOBILITY, List.of());
            db.add("Shoulder CARs", LiftRegion.UPPER, LiftType.MOBILITY, List.of());
            db.add("Hamstring Curl", LiftRegion.LOWER, LiftType.ACCESSORY, List.of(Muscle.HAMSTRING));
            db.add("Leg Extension", LiftRegion.LOWER, LiftType.ACCESSORY, List.of(Muscle.QUAD, Muscle.CALF));
            db.add("Plank", LiftRegion.LOWER, LiftType.ACCESSORY, List.of(Muscle.CORE));
            db.add("Delt Giant Set", LiftRegion.UPPER, LiftType.ACCESSORY, List.of(Muscle.REAR_DELT, Muscle.SHOULDER, Muscle.FRONT_DELT, Muscle.TRAP));
            db.add("Upper Compound Accessory", LiftRegion.UPPER, LiftType.ACCESSORY, List.of(Muscle.LAT, Muscle.TRICEP, Muscle.BICEP));

            return db;
        }

        static FakeDb withCableAndDbCircuitData() {
            FakeDb db = withSeedData();

            db.add("Cable Hamstring Curl", LiftRegion.LOWER, LiftType.ACCESSORY, List.of(Muscle.HAMSTRING));
            db.add("Machine Hamstring Curl", LiftRegion.LOWER, LiftType.ACCESSORY, List.of(Muscle.HAMSTRING));
            db.add("Cable Leg Extension", LiftRegion.LOWER, LiftType.ACCESSORY, List.of(Muscle.QUAD));
            db.add("Belt Squat", LiftRegion.LOWER, LiftType.ACCESSORY, List.of(Muscle.QUAD));
            db.add("Cable Lat Pulldown", LiftRegion.UPPER, LiftType.ACCESSORY, List.of(Muscle.LAT));
            db.add("Chest Supported Row", LiftRegion.UPPER, LiftType.ACCESSORY, List.of(Muscle.LAT));
            db.add("Cable Tricep Pushdown", LiftRegion.UPPER, LiftType.ACCESSORY, List.of(Muscle.TRICEP));
            db.add("Dip", LiftRegion.UPPER, LiftType.ACCESSORY, List.of(Muscle.TRICEP));
            db.add("DB Rear Delt Fly", LiftRegion.UPPER, LiftType.ACCESSORY, List.of(Muscle.REAR_DELT));
            db.add("Face Pull", LiftRegion.UPPER, LiftType.ACCESSORY, List.of(Muscle.REAR_DELT));
            db.add("DB Lateral Raise", LiftRegion.UPPER, LiftType.ACCESSORY, List.of(Muscle.SHOULDER));
            db.add("Machine Lateral Raise", LiftRegion.UPPER, LiftType.ACCESSORY, List.of(Muscle.SHOULDER));
            db.add("DB Front Raise", LiftRegion.UPPER, LiftType.ACCESSORY, List.of(Muscle.FRONT_DELT));
            db.add("Plate Front Raise", LiftRegion.UPPER, LiftType.ACCESSORY, List.of(Muscle.FRONT_DELT));
            db.add("DB Shrug 2", LiftRegion.UPPER, LiftType.ACCESSORY, List.of(Muscle.TRAP));
            db.add("Barbell Shrug", LiftRegion.UPPER, LiftType.ACCESSORY, List.of(Muscle.TRAP));

            return db;
        }

        void add(String name, LiftRegion region, LiftType type, List<Muscle> muscles) {
            lifts.add(new Lift(name, region, type, muscles, ""));
        }

        void exec(String name, LiftExecution execution) {
            executions.computeIfAbsent(name, k -> new ArrayList<>()).add(execution);
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
        @Override public void setLiftEnabled(String name, boolean enabled) {}
        @Override public boolean isLiftEnabled(String name) { return true; }
        @Override public List<LiftExecution> getExecutions(String liftName) { return executions.getOrDefault(liftName, List.of()); }
        @Override public void updateLiftExecution(int execId, LiftExecution execution) {}
        @Override public void deleteLiftExecution(int execId) {}
        @Override public LiftStats liftStats(String name) { throw new UnsupportedOperationException(); }
        @Override public List<Lift> listLifts() { return List.copyOf(lifts); }
    }
}
