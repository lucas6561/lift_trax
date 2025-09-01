package org.lift.trax.workoutbuilder;

import java.util.List;

import org.lift.trax.Database;

public interface WorkoutBuilder {
    List<WorkoutWeek> getWave(int numWeeks, Database db) throws Exception;
}

