package com.lifttrax.workout;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Versioned planned-workout file that can be exported, imported, and displayed. */
public record PlannedWorkoutFile(
    int schemaVersion,
    PlannedWorkoutMetadata metadata,
    PlannedWorkoutSource source,
    List<PlannedWorkoutWeek> weeks,
    List<CompletedWorkout> completedWorkouts) {
  public static final int LATEST_SCHEMA_VERSION = PlannedWorkoutSchemaVersions.latest();

  public PlannedWorkoutFile {
    if (!PlannedWorkoutSchemaVersions.supports(schemaVersion)) {
      throw new IllegalArgumentException(
          PlannedWorkoutSchemaVersions.unsupportedVersionMessage(schemaVersion));
    }
    metadata = Objects.requireNonNull(metadata, "metadata is required");
    source = Objects.requireNonNull(source, "source is required");
    weeks = copyRequired(weeks, "weeks");
    completedWorkouts = copyOptional(completedWorkouts);
    if (schemaVersion >= 3) {
      validateSingleIntensityMode(weeks);
    }
  }

  public record PlannedWorkoutMetadata(
      String name, String description, int totalWeeks, List<String> tags) {
    public PlannedWorkoutMetadata {
      name = requiredText(name, "metadata.name");
      description = optionalText(description);
      if (totalWeeks < 1) {
        throw new IllegalArgumentException("metadata.totalWeeks must be greater than 0");
      }
      tags = copyOptional(tags);
    }
  }

  public record PlannedWorkoutSource(
      String kind,
      String generator,
      String programName,
      Integer programSchemaVersion,
      String generatedAt) {
    public PlannedWorkoutSource {
      kind = requiredText(kind, "source.kind");
      generator = requiredText(generator, "source.generator");
      programName = requiredText(programName, "source.programName");
      generatedAt = requiredText(generatedAt, "source.generatedAt");
      if (programSchemaVersion != null && programSchemaVersion < 1) {
        throw new IllegalArgumentException("source.programSchemaVersion must be positive");
      }
    }
  }

  public record PlannedWorkoutWeek(int weekNumber, List<PlannedWorkoutDay> days) {
    public PlannedWorkoutWeek {
      if (weekNumber < 1) {
        throw new IllegalArgumentException("weekNumber must be greater than 0");
      }
      days = copyRequired(days, "weeks[" + weekNumber + "].days");
    }
  }

  public record PlannedWorkoutDay(
      String dayOfWeek, String title, List<PlannedWorkoutBlock> blocks, List<String> notes) {
    public PlannedWorkoutDay {
      dayOfWeek = requiredText(dayOfWeek, "day.dayOfWeek");
      title = requiredText(title, "day.title");
      blocks = copyRequired(blocks, "day.blocks");
      notes = copyOptional(notes);
    }
  }

  public record PlannedWorkoutBlock(
      int order,
      String title,
      String blockType,
      Integer rounds,
      boolean warmup,
      List<PlannedExercise> exercises,
      List<String> notes) {
    public PlannedWorkoutBlock {
      if (order < 1) {
        throw new IllegalArgumentException("block.order must be greater than 0");
      }
      title = requiredText(title, "block.title");
      blockType = requiredText(blockType, "block.blockType");
      if (rounds != null && rounds < 1) {
        throw new IllegalArgumentException("block.rounds must be positive when present");
      }
      exercises = copyRequired(exercises, "block.exercises");
      notes = copyOptional(notes);
    }
  }

  public record PlannedExercise(
      String name,
      String region,
      String type,
      List<String> muscles,
      List<PlannedSetTarget> plannedSets,
      String notes,
      List<String> substitutionOptions) {
    public PlannedExercise {
      name = requiredText(name, "exercise.name");
      muscles = copyOptional(muscles);
      plannedSets = copyOptional(plannedSets);
      notes = optionalText(notes);
      substitutionOptions = copyOptional(substitutionOptions);
    }
  }

  public record PlannedSetTarget(
      Integer setNumber,
      String metricType,
      Integer reps,
      Integer repsLeft,
      Integer repsRight,
      Integer repsMin,
      Integer repsMax,
      Integer seconds,
      Integer distanceFeet,
      Integer percent,
      Float rpe,
      String accommodatingResistance,
      boolean deload) {
    public PlannedSetTarget {
      metricType = requiredText(metricType, "plannedSet.metricType");
      metricType = normalizeMetricType(metricType);
      if ("reps".equals(metricType) && reps == null && repsMin != null && repsMax != null) {
        metricType = "reps_range";
      }
      if (setNumber != null && setNumber < 1) {
        throw new IllegalArgumentException("plannedSet.setNumber must be positive when present");
      }
    }
  }

  public record CompletedWorkout(
      String completionId,
      int weekNumber,
      String dayOfWeek,
      String completedAt,
      List<CompletedExerciseResult> exercises,
      String notes) {
    public CompletedWorkout {
      completionId = requiredText(completionId, "completedWorkout.completionId");
      if (weekNumber < 1) {
        throw new IllegalArgumentException("completedWorkout.weekNumber must be greater than 0");
      }
      dayOfWeek = requiredText(dayOfWeek, "completedWorkout.dayOfWeek");
      completedAt = requiredText(completedAt, "completedWorkout.completedAt");
      exercises = copyOptional(exercises);
      notes = optionalText(notes);
    }
  }

  public record CompletedExerciseResult(String name, List<CompletedSetResult> sets, String notes) {
    public CompletedExerciseResult {
      name = requiredText(name, "completedExercise.name");
      sets = copyOptional(sets);
      notes = optionalText(notes);
    }
  }

  public record CompletedSetResult(
      int setNumber, String result, String weight, Float rpe, String notes) {
    public CompletedSetResult {
      if (setNumber < 1) {
        throw new IllegalArgumentException("completedSet.setNumber must be greater than 0");
      }
      result = requiredText(result, "completedSet.result");
      weight = optionalText(weight);
      notes = optionalText(notes);
    }
  }

  private static String requiredText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " is required");
    }
    return value.trim();
  }

  private static String normalizeMetricType(String value) {
    String normalized = value.trim().replace('-', '_');
    String lower = normalized.toLowerCase(Locale.ROOT);
    return switch (lower) {
      case "repslr", "reps_lr" -> "reps_lr";
      case "repsrange", "reps_range" -> "reps_range";
      case "timesecs", "time_seconds" -> "time_seconds";
      case "distancefeet", "distance_feet" -> "distance_feet";
      default -> lower;
    };
  }

  private static String optionalText(String value) {
    return value == null ? "" : value;
  }

  private static <T> List<T> copyRequired(List<T> values, String field) {
    if (values == null || values.isEmpty()) {
      throw new IllegalArgumentException(field + " must contain at least one item");
    }
    return List.copyOf(values);
  }

  private static <T> List<T> copyOptional(List<T> values) {
    if (values == null || values.isEmpty()) {
      return List.of();
    }
    return List.copyOf(values);
  }

  static void validateSingleIntensityMode(List<PlannedWorkoutWeek> weeks) {
    for (PlannedWorkoutWeek week : weeks) {
      for (PlannedWorkoutDay day : week.days()) {
        for (PlannedWorkoutBlock block : day.blocks()) {
          for (PlannedExercise exercise : block.exercises()) {
            for (PlannedSetTarget set : exercise.plannedSets()) {
              if (set.percent() != null && set.rpe() != null) {
                throw new IllegalArgumentException(
                    "plannedSet cannot set both percent and rpe in schemaVersion 3");
              }
            }
          }
        }
      }
    }
  }
}
