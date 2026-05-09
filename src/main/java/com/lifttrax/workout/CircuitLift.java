package com.lifttrax.workout;

import java.util.List;

/** Simple data holder for CircuitLift values used by LiftTrax. */
public record CircuitLift(List<SingleLift> circuitLifts, int rounds, boolean warmup) {}
