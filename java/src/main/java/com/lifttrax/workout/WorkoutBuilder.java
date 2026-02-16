package com.lifttrax.workout;

import com.lifttrax.db.Database;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;

public interface WorkoutBuilder {
    List<Map<DayOfWeek, Workout>> getWave(int numWeeks, Database db) throws Exception;
}
