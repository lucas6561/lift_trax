package com.lifttrax.workout;

/**
 * Simple data holder for WorkoutLift values used by LiftTrax.
 */

public record WorkoutLift(
        String name,
        WorkoutLiftKind kind
) {}
