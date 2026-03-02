package com.lifttrax.models;

import java.util.Map;

/**
 * Simple data holder for LiftStats values used by LiftTrax.
 */

public record LiftStats(
        LiftExecution last,
        Map<Integer, String> bestByReps
) {}
