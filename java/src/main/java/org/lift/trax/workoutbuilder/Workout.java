package org.lift.trax.workoutbuilder;

import java.util.ArrayList;
import java.util.List;

public class Workout {
    public List<WorkoutLift> lifts;

    public Workout(List<WorkoutLift> lifts) {
        this.lifts = new ArrayList<>(lifts);
    }
}

