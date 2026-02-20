package com.lifttrax.workout;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HypertrophyWorkoutBuilderTest {

    @Test
    void buildsExpectedFourDaysForWave() throws Exception {
        ConjugateWorkoutBuilderTest.FakeDb db = ConjugateWorkoutBuilderTest.FakeDb.withSeedData();
        HypertrophyWorkoutBuilder builder = new HypertrophyWorkoutBuilder();

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
    void mondayUpperDayContainsExpectedSetScheme() throws Exception {
        ConjugateWorkoutBuilderTest.FakeDb db = ConjugateWorkoutBuilderTest.FakeDb.withSeedData();
        var week = new HypertrophyWorkoutBuilder().getWave(1, db).get(0);

        Workout monday = week.get(DayOfWeek.MONDAY);
        assertNotNull(monday);

        long mainSets = monday.lifts().stream()
                .filter(l -> l.kind() instanceof WorkoutLiftKind.SingleKind sk
                        && l.name().equals("Main Hypertrophy")
                        && sk.singleLift().metric() instanceof com.lifttrax.models.SetMetric.RepsRange range
                        && range.low() == 8 && range.high() == 12)
                .count();

        long supplementalSets = monday.lifts().stream()
                .filter(l -> l.kind() instanceof WorkoutLiftKind.SingleKind sk
                        && l.name().equals("Supplemental Hypertrophy")
                        && sk.singleLift().metric() instanceof com.lifttrax.models.SetMetric.RepsRange range
                        && range.low() == 10 && range.high() == 15)
                .count();

        assertEquals(4, mainSets);
        assertEquals(3, supplementalSets);
        assertTrue(monday.lifts().stream().anyMatch(l -> l.name().equals("Accessory Circuit")));
    }
}
