package com.lifttrax.workout;

import java.util.List;

public record CircuitLift(
        List<SingleLift> circuitLifts,
        int rounds,
        boolean warmup
) {}
