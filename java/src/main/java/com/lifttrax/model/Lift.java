package com.lifttrax.model;

import java.util.List;

public record Lift(
    String name,
    LiftRegion region,
    LiftType mainType,
    List<Muscle> muscles,
    String notes,
    List<LiftExecution> recentExecutions
) {
}
