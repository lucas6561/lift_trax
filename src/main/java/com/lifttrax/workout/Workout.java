package com.lifttrax.workout;

import java.util.List;

/** Simple data holder for Workout values used by LiftTrax. */
public record Workout(List<WorkoutLift> lifts) {}
