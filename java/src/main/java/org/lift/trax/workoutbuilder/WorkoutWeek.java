package org.lift.trax.workoutbuilder;

import java.time.DayOfWeek;
import java.util.EnumMap;

public class WorkoutWeek extends EnumMap<DayOfWeek, Workout> {
    public WorkoutWeek() {
        super(DayOfWeek.class);
    }
}

