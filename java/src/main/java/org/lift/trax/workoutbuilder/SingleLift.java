package org.lift.trax.workoutbuilder;

import org.lift.trax.Lift;

public class SingleLift implements WorkoutLift {
    public Lift lift;
    public Integer repCount;
    public Integer timeSec;
    public Integer distanceM;
    public Integer percent;
    public AccommodatingResistance accommodatingResistance;

    public SingleLift(Lift lift, Integer repCount, Integer timeSec, Integer distanceM,
                      Integer percent, AccommodatingResistance accommodatingResistance) {
        this.lift = lift;
        this.repCount = repCount;
        this.timeSec = timeSec;
        this.distanceM = distanceM;
        this.percent = percent;
        this.accommodatingResistance = accommodatingResistance;
    }
}

