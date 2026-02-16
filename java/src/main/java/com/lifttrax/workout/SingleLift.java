package com.lifttrax.workout;

import com.lifttrax.models.Lift;
import com.lifttrax.models.SetMetric;

public record SingleLift(
        Lift lift,
        SetMetric metric,
        Integer percent,
        Float rpe,
        AccommodatingResistance accommodatingResistance,
        boolean deload
) {}
