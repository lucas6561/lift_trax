package com.lifttrax.workout;

import com.lifttrax.models.Lift;

/**
 * Simple data holder for DynamicLift values used by LiftTrax.
 */

public record DynamicLift(Lift lift, AccommodatingResistance ar) {}
