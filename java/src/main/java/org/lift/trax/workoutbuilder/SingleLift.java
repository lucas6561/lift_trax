package org.lift.trax.workoutbuilder;

import org.lift.trax.Lift;

public class SingleLift implements WorkoutLift {
    public Lift lift;
    public SetMetric metric;
    public Integer percent;
    public AccommodatingResistance accommodatingResistance;

    public SingleLift(Lift lift, SetMetric metric, Integer percent,
                      AccommodatingResistance accommodatingResistance) {
        this.lift = lift;
        this.metric = metric;
        this.percent = percent;
        this.accommodatingResistance = accommodatingResistance;
    }
}

