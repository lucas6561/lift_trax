package com.lifttrax.workout;

import com.lifttrax.models.Lift;
import com.lifttrax.models.Muscle;
import com.lifttrax.models.SetMetric;
import java.time.DayOfWeek;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Converts generated LiftTrax waves into workout file format v1. */
public final class PlannedWorkoutExporter {
  private PlannedWorkoutExporter() {}

  public static PlannedWorkoutFile fromWave(
      String programName, String generator, List<Map<DayOfWeek, Workout>> wave) {
    return fromWave(
        programName,
        generator,
        ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT),
        wave);
  }

  static PlannedWorkoutFile fromWave(
      String programName,
      String generator,
      String generatedAt,
      List<Map<DayOfWeek, Workout>> wave) {
    if (wave == null || wave.isEmpty()) {
      throw new IllegalArgumentException("wave must contain at least one week");
    }

    PlannedWorkoutFile.PlannedWorkoutMetadata metadata =
        new PlannedWorkoutFile.PlannedWorkoutMetadata(
            programName + " Planned Workouts",
            "Generated from LiftTrax wave generation.",
            wave.size(),
            List.of("generated", generator));
    PlannedWorkoutFile.PlannedWorkoutSource source =
        new PlannedWorkoutFile.PlannedWorkoutSource(
            "wave-generation", generator, programName, null, generatedAt);
    List<PlannedWorkoutFile.PlannedWorkoutWeek> weeks = new ArrayList<>();
    for (int index = 0; index < wave.size(); index++) {
      weeks.add(plannedWeek(index + 1, wave.get(index)));
    }
    return new PlannedWorkoutFile(
        PlannedWorkoutFile.LATEST_SCHEMA_VERSION, metadata, source, weeks, List.of());
  }

  private static PlannedWorkoutFile.PlannedWorkoutWeek plannedWeek(
      int weekNumber, Map<DayOfWeek, Workout> week) {
    List<PlannedWorkoutFile.PlannedWorkoutDay> days = new ArrayList<>();
    for (DayOfWeek day : dayOrder()) {
      Workout workout = week.get(day);
      if (workout != null) {
        days.add(plannedDay(day, workout));
      }
    }
    return new PlannedWorkoutFile.PlannedWorkoutWeek(weekNumber, days);
  }

  private static PlannedWorkoutFile.PlannedWorkoutDay plannedDay(DayOfWeek day, Workout workout) {
    List<PlannedWorkoutFile.PlannedWorkoutBlock> blocks = new ArrayList<>();
    int index = 0;
    int order = 1;
    while (index < workout.lifts().size()) {
      WorkoutLift lift = workout.lifts().get(index);
      if (lift.kind() instanceof WorkoutLiftKind.SingleKind singleKind) {
        int count = countMatchingSingles(workout.lifts(), index, lift);
        blocks.add(singleBlock(order, lift.name(), singleKind.singleLift(), count));
        index += count;
      } else if (lift.kind() instanceof WorkoutLiftKind.CircuitKind circuitKind) {
        blocks.add(circuitBlock(order, lift.name(), circuitKind.circuitLift()));
        index++;
      }
      order++;
    }
    return new PlannedWorkoutFile.PlannedWorkoutDay(day.name(), title(day), blocks, List.of());
  }

  private static int countMatchingSingles(List<WorkoutLift> lifts, int start, WorkoutLift lift) {
    WorkoutLiftKind.SingleKind current = (WorkoutLiftKind.SingleKind) lift.kind();
    int count = 1;
    while (start + count < lifts.size()) {
      WorkoutLift next = lifts.get(start + count);
      if (next.kind() instanceof WorkoutLiftKind.SingleKind nextKind
          && lift.name().equals(next.name())
          && sameSingle(current.singleLift(), nextKind.singleLift())) {
        count++;
      } else {
        break;
      }
    }
    return count;
  }

  private static PlannedWorkoutFile.PlannedWorkoutBlock singleBlock(
      int order, String title, SingleLift singleLift, int count) {
    return new PlannedWorkoutFile.PlannedWorkoutBlock(
        order,
        title,
        blockType(title, false, false),
        null,
        false,
        List.of(plannedExercise(singleLift, plannedSets(singleLift, count))),
        blockNotes(title));
  }

  private static PlannedWorkoutFile.PlannedWorkoutBlock circuitBlock(
      int order, String title, CircuitLift circuitLift) {
    List<PlannedWorkoutFile.PlannedExercise> exercises = new ArrayList<>();
    for (SingleLift singleLift : circuitLift.circuitLifts()) {
      exercises.add(plannedExercise(singleLift, List.of(plannedSet(singleLift, 1))));
    }
    return new PlannedWorkoutFile.PlannedWorkoutBlock(
        order,
        title,
        blockType(title, true, circuitLift.warmup()),
        circuitLift.rounds(),
        circuitLift.warmup(),
        exercises,
        List.of());
  }

  private static PlannedWorkoutFile.PlannedExercise plannedExercise(
      SingleLift singleLift, List<PlannedWorkoutFile.PlannedSetTarget> plannedSets) {
    Lift lift = singleLift.lift();
    return new PlannedWorkoutFile.PlannedExercise(
        lift.name(),
        lift.region() == null ? null : lift.region().name(),
        lift.main() == null ? null : lift.main().name(),
        muscleNames(lift),
        plannedSets,
        lift.notes(),
        List.of());
  }

  private static List<String> muscleNames(Lift lift) {
    if (lift.muscles() == null || lift.muscles().isEmpty()) {
      return List.of();
    }
    return lift.muscles().stream().map(Muscle::name).toList();
  }

  private static List<PlannedWorkoutFile.PlannedSetTarget> plannedSets(
      SingleLift singleLift, int count) {
    List<PlannedWorkoutFile.PlannedSetTarget> sets = new ArrayList<>();
    for (int setNumber = 1; setNumber <= count; setNumber++) {
      sets.add(plannedSet(singleLift, setNumber));
    }
    return sets;
  }

  private static PlannedWorkoutFile.PlannedSetTarget plannedSet(
      SingleLift singleLift, Integer setNumber) {
    return plannedSetTarget(singleLift, setNumber);
  }

  static PlannedWorkoutFile.PlannedSetTarget plannedSetForDescription(SingleLift singleLift) {
    return plannedSetTarget(singleLift, 1);
  }

  private static PlannedWorkoutFile.PlannedSetTarget plannedSetTarget(
      SingleLift singleLift, Integer setNumber) {
    SetMetric metric = singleLift.metric();
    String metricType = "none";
    Integer reps = null;
    Integer repsLeft = null;
    Integer repsRight = null;
    Integer repsMin = null;
    Integer repsMax = null;
    Integer seconds = null;
    Integer distanceFeet = null;

    if (metric instanceof SetMetric.Reps value) {
      metricType = "reps";
      reps = value.reps();
    } else if (metric instanceof SetMetric.RepsLr value) {
      metricType = "reps_lr";
      repsLeft = value.left();
      repsRight = value.right();
    } else if (metric instanceof SetMetric.RepsRange value) {
      metricType = "reps_range";
      repsMin = value.min();
      repsMax = value.max();
    } else if (metric instanceof SetMetric.TimeSecs value) {
      metricType = "time_seconds";
      seconds = value.seconds();
    } else if (metric instanceof SetMetric.DistanceFeet value) {
      metricType = "distance_feet";
      distanceFeet = value.feet();
    }

    return new PlannedWorkoutFile.PlannedSetTarget(
        setNumber,
        metricType,
        reps,
        repsLeft,
        repsRight,
        repsMin,
        repsMax,
        seconds,
        distanceFeet,
        singleLift.percent(),
        singleLift.rpe(),
        singleLift.accommodatingResistance() == null
            ? null
            : singleLift.accommodatingResistance().name(),
        singleLift.deload());
  }

  private static String blockType(String title, boolean circuit, boolean warmup) {
    String normalized = title.toLowerCase(Locale.ROOT);
    if (warmup || normalized.contains("warmup")) {
      return "warmup";
    }
    if (normalized.contains("max effort")) {
      return "max_effort";
    }
    if (normalized.contains("dynamic effort")) {
      return "dynamic_effort";
    }
    if (normalized.contains("supplemental")) {
      return "supplemental";
    }
    if (normalized.contains("accessory") || normalized.contains("finisher")) {
      return "accessory";
    }
    if (normalized.contains("conditioning")) {
      return "conditioning";
    }
    if (circuit) {
      return "circuit";
    }
    return "single";
  }

  private static List<String> blockNotes(String title) {
    if (title.contains("target 95%")) {
      return List.of("Target 95% of max; go higher only if it feels great.");
    }
    return List.of();
  }

  private static boolean sameSingle(SingleLift first, SingleLift second) {
    return first.lift().name().equals(second.lift().name())
        && equal(first.metric(), second.metric())
        && equal(first.percent(), second.percent())
        && equal(first.rpe(), second.rpe())
        && equal(first.accommodatingResistance(), second.accommodatingResistance())
        && first.deload() == second.deload();
  }

  private static List<DayOfWeek> dayOrder() {
    return List.of(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY,
        DayOfWeek.SATURDAY,
        DayOfWeek.SUNDAY);
  }

  private static String title(DayOfWeek day) {
    String value = day.name().toLowerCase(Locale.ROOT);
    return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
  }

  private static boolean equal(Object first, Object second) {
    return first == null ? second == null : first.equals(second);
  }
}
