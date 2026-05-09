package com.lifttrax.workout;

import com.lifttrax.models.Lift;
import com.lifttrax.models.SetMetric;

/** Simple data holder for SingleLift values used by LiftTrax. */
public record SingleLift(
    Lift lift,
    SetMetric metric,
    Integer percent,
    Float rpe,
    AccommodatingResistance accommodatingResistance,
    boolean deload) {}
