package com.lifttrax.models;

import java.util.List;

public record Lift(
        String name,
        LiftRegion region,
        LiftType main,
        List<Muscle> muscles,
        String notes
) {}
