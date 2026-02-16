package com.lifttrax.models;

import java.util.Map;

public record LiftStats(
        LiftExecution last,
        Map<Integer, String> bestByReps
) {}
