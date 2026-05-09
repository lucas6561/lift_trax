package com.lifttrax.workout;

import com.lifttrax.db.Database;
import com.lifttrax.models.Lift;
import com.lifttrax.models.LiftRegion;
import com.lifttrax.models.LiftType;
import com.lifttrax.models.Muscle;
import com.lifttrax.models.SetMetric;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Builds a hypertrophy-focused wave with higher rep ranges and reduced intensity targets. */
public class HypertrophyWorkoutBuilder implements WorkoutBuilder {
  private static WorkoutLift single(
      String section, Lift lift, int reps, Integer percent, Float rpe) {
    return new WorkoutLift(
        section,
        new WorkoutLiftKind.SingleKind(
            new SingleLift(lift, new SetMetric.Reps(reps), percent, rpe, null, false)));
  }

  private static List<WorkoutLift> repeated(
      String section, Lift lift, int sets, int reps, Integer percent, Float rpe) {
    List<WorkoutLift> setLifts = new ArrayList<>();
    for (int i = 0; i < sets; i++) {
      setLifts.add(
          new WorkoutLift(
              section,
              new WorkoutLiftKind.SingleKind(
                  new SingleLift(lift, new SetMetric.Reps(reps), percent, rpe, null, false))));
    }
    return setLifts;
  }

  private static Lift require(List<Lift> lifts, String message) {
    if (lifts.isEmpty()) {
      throw new IllegalArgumentException(message);
    }
    return lifts.get(0);
  }

  private static Lift accessory(Database db, Muscle muscle) throws Exception {
    return require(
        db.getAccessoriesByMuscle(muscle), "missing accessory lift for muscle " + muscle.name());
  }

  private static Workout lowerDay(Database db, int week) throws Exception {
    List<Lift> squats = db.liftsByType(LiftType.SQUAT);
    List<Lift> deadlifts = db.liftsByType(LiftType.DEADLIFT);
    Lift squat = require(squats, "missing squat lift");
    Lift deadlift = require(deadlifts, "missing deadlift lift");
    Lift primary = week % 2 == 0 ? squat : deadlift;
    Lift secondary = week % 2 == 0 ? deadlift : squat;

    List<WorkoutLift> lifts = new ArrayList<>();
    lifts.add(
        new WorkoutLift(
            "Warm-up",
            new WorkoutLiftKind.CircuitKind(
                new CircuitLift(
                    List.of(
                        new SingleLift(
                            require(
                                db.liftsByRegionAndType(LiftRegion.LOWER, LiftType.MOBILITY),
                                "missing lower mobility lift"),
                            new SetMetric.Reps(8),
                            null,
                            null,
                            null,
                            false)),
                    2,
                    true))));
    lifts.addAll(repeated("Primary Hypertrophy", primary, 4, 8, 70, 7.5f));
    lifts.addAll(repeated("Secondary Hypertrophy", secondary, 3, 10, 65, 7.0f));
    lifts.add(single("Accessory", accessory(db, Muscle.HAMSTRING), 12, null, 8.0f));
    lifts.add(single("Accessory", accessory(db, Muscle.QUAD), 12, null, 8.0f));
    lifts.add(single("Core", accessory(db, Muscle.CORE), 15, null, 7.0f));
    return new Workout(lifts);
  }

  private static Workout upperDay(Database db, int week) throws Exception {
    List<Lift> benches = db.liftsByType(LiftType.BENCH_PRESS);
    List<Lift> overheads = db.liftsByType(LiftType.OVERHEAD_PRESS);
    Lift bench = require(benches, "missing bench press lift");
    Lift overhead = require(overheads, "missing overhead press lift");
    Lift primary = week % 2 == 0 ? bench : overhead;
    Lift secondary = week % 2 == 0 ? overhead : bench;

    List<WorkoutLift> lifts = new ArrayList<>();
    lifts.add(
        new WorkoutLift(
            "Warm-up",
            new WorkoutLiftKind.CircuitKind(
                new CircuitLift(
                    List.of(
                        new SingleLift(
                            require(
                                db.liftsByRegionAndType(LiftRegion.UPPER, LiftType.MOBILITY),
                                "missing upper mobility lift"),
                            new SetMetric.Reps(8),
                            null,
                            null,
                            null,
                            false)),
                    2,
                    true))));
    lifts.addAll(repeated("Primary Hypertrophy", primary, 4, 8, 70, 7.5f));
    lifts.addAll(repeated("Secondary Hypertrophy", secondary, 3, 10, 65, 7.0f));
    lifts.add(single("Accessory", accessory(db, Muscle.CHEST), 12, null, 8.0f));
    lifts.add(single("Accessory", accessory(db, Muscle.LAT), 12, null, 8.0f));
    lifts.add(single("Accessory", accessory(db, Muscle.TRICEP), 12, null, 8.0f));
    return new Workout(lifts);
  }

  @Override
  public List<Map<DayOfWeek, Workout>> getWave(int numWeeks, Database db) throws Exception {
    List<Map<DayOfWeek, Workout>> weeks = new ArrayList<>();
    for (int week = 0; week < numWeeks; week++) {
      Map<DayOfWeek, Workout> workoutWeek = new EnumMap<>(DayOfWeek.class);
      workoutWeek.put(DayOfWeek.MONDAY, lowerDay(db, week));
      workoutWeek.put(DayOfWeek.TUESDAY, upperDay(db, week));
      workoutWeek.put(DayOfWeek.THURSDAY, lowerDay(db, week + 1));
      workoutWeek.put(DayOfWeek.FRIDAY, upperDay(db, week + 1));
      weeks.add(workoutWeek);
    }
    return weeks;
  }
}
