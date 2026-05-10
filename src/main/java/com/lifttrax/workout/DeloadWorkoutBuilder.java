package com.lifttrax.workout;

import com.lifttrax.db.Database;
import com.lifttrax.models.Lift;
import com.lifttrax.models.LiftRegion;
import com.lifttrax.models.LiftType;
import com.lifttrax.models.SetMetric;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Dedicated builder for deload-only waves. */
public class DeloadWorkoutBuilder implements WorkoutBuilder {

  private static WorkoutLift technique(String name, Lift lift, int reps) {
    return new WorkoutLift(
        name,
        new WorkoutLiftKind.SingleKind(
            new SingleLift(lift, new SetMetric.Reps(reps), 70, 6.0f, null, true)));
  }

  private static WorkoutLift lightConditioning(Lift lift) {
    return new WorkoutLift(
        "Light Conditioning",
        new WorkoutLiftKind.SingleKind(
            new SingleLift(lift, new SetMetric.TimeSecs(300), null, null, null, true)));
  }

  @Override
  public List<Map<DayOfWeek, Workout>> getWave(int numWeeks, Database db) throws Exception {
    List<Lift> squats = db.liftsByType(LiftType.SQUAT);
    List<Lift> deadlifts = db.liftsByType(LiftType.DEADLIFT);
    List<Lift> benches = db.liftsByType(LiftType.BENCH_PRESS);
    List<Lift> overheads = db.liftsByType(LiftType.OVERHEAD_PRESS);
    List<Lift> lowerCond = db.liftsByRegionAndType(LiftRegion.LOWER, LiftType.CONDITIONING);
    List<Lift> upperCond = db.liftsByRegionAndType(LiftRegion.UPPER, LiftType.CONDITIONING);
    if (squats.isEmpty() || deadlifts.isEmpty() || benches.isEmpty() || overheads.isEmpty()) {
      throw new IllegalArgumentException("not enough main lifts available for deload wave");
    }
    if (lowerCond.isEmpty() || upperCond.isEmpty()) {
      throw new IllegalArgumentException("not enough conditioning lifts available");
    }

    List<Map<DayOfWeek, Workout>> weeks = new ArrayList<>();
    for (int week = 0; week < numWeeks; week++) {
      Map<DayOfWeek, Workout> workoutWeek = new EnumMap<>(DayOfWeek.class);

      workoutWeek.put(
          DayOfWeek.MONDAY,
          new Workout(
              List.of(
                  technique("Deload Technique", squats.get(week % squats.size()), 3),
                  technique("Deload Technique", deadlifts.get(week % deadlifts.size()), 3),
                  lightConditioning(lowerCond.get(week % lowerCond.size())))));
      workoutWeek.put(
          DayOfWeek.TUESDAY,
          new Workout(
              List.of(
                  technique("Deload Technique", benches.get(week % benches.size()), 3),
                  technique("Deload Technique", overheads.get(week % overheads.size()), 3),
                  lightConditioning(upperCond.get(week % upperCond.size())))));
      workoutWeek.put(
          DayOfWeek.THURSDAY,
          new Workout(
              List.of(
                  technique("Deload Speed Work", squats.get(week % squats.size()), 3),
                  technique("Deload Speed Work", deadlifts.get(week % deadlifts.size()), 2),
                  lightConditioning(lowerCond.get(week % lowerCond.size())))));
      workoutWeek.put(
          DayOfWeek.FRIDAY,
          new Workout(
              List.of(
                  technique("Deload Speed Work", benches.get(week % benches.size()), 3),
                  technique("Deload Speed Work", overheads.get(week % overheads.size()), 2),
                  lightConditioning(upperCond.get(week % upperCond.size())))));
      weeks.add(workoutWeek);
    }
    return weeks;
  }
}
