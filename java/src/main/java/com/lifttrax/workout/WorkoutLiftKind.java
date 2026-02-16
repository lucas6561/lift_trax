package com.lifttrax.workout;

public sealed interface WorkoutLiftKind permits WorkoutLiftKind.SingleKind, WorkoutLiftKind.CircuitKind {
    record SingleKind(SingleLift singleLift) implements WorkoutLiftKind {}
    record CircuitKind(CircuitLift circuitLift) implements WorkoutLiftKind {}
}
