package com.lifttrax.workout;

/** Contract for WorkoutLiftKind behavior used by other LiftTrax components. */
public sealed interface WorkoutLiftKind
    permits WorkoutLiftKind.SingleKind, WorkoutLiftKind.CircuitKind {
  record SingleKind(SingleLift singleLift) implements WorkoutLiftKind {}

  record CircuitKind(CircuitLift circuitLift) implements WorkoutLiftKind {}
}
