package org.lift.trax.workoutbuilder;

import java.util.List;

public class CircuitLift implements WorkoutLift {
    public List<SingleLift> circuitLifts;
    public int restTimeSec;
    public int rounds;

    public CircuitLift(List<SingleLift> circuitLifts, int restTimeSec, int rounds) {
        this.circuitLifts = circuitLifts;
        this.restTimeSec = restTimeSec;
        this.rounds = rounds;
    }
}

