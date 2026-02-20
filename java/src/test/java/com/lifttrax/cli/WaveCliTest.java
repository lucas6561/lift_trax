package com.lifttrax.cli;

import com.lifttrax.workout.ConjugateWorkoutBuilder;
import com.lifttrax.workout.HypertrophyWorkoutBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WaveCliTest {
    @Test
    void selectsConjugateByDefault() {
        assertInstanceOf(ConjugateWorkoutBuilder.class, WaveCli.builderForProgram(null));
        assertInstanceOf(ConjugateWorkoutBuilder.class, WaveCli.builderForProgram(""));
        assertInstanceOf(ConjugateWorkoutBuilder.class, WaveCli.builderForProgram("conjugate"));
    }

    @Test
    void selectsHypertrophyWhenRequested() {
        assertInstanceOf(HypertrophyWorkoutBuilder.class, WaveCli.builderForProgram("hypertrophy"));
    }

    @Test
    void rejectsUnknownProgram() {
        assertThrows(IllegalArgumentException.class, () -> WaveCli.builderForProgram("unknown"));
    }
}
