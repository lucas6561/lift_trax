package org.lift.trax.workoutbuilder;

import java.util.List;

public class CircuitLift implements WorkoutLift {
    public List<SingleLift> circuitLifts;
    public int restTimeSec;

    public CircuitLift(List<SingleLift> circuitLifts, int restTimeSec) {
        this.circuitLifts = circuitLifts;
        this.restTimeSec = restTimeSec;
    }
}

