package com.lifttrax.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.lifttrax.db.AccountProfile;
import com.lifttrax.db.SavedWorkout;
import com.lifttrax.db.TrainingDataStore;
import com.lifttrax.models.ExecutionSet;
import com.lifttrax.models.Lift;
import com.lifttrax.models.LiftExecution;
import com.lifttrax.models.SetMetric;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Writes a complete, portable JSON export of one account's persisted training data. */
final class AccountExportWriter {
  static final int SCHEMA_VERSION = 1;
  private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
  private static final ObjectWriter JSON = JSON_MAPPER.writerWithDefaultPrettyPrinter();
  private static final DateTimeFormatter FILE_TIME =
      DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss'Z'").withZone(ZoneOffset.UTC);

  private AccountExportWriter() {}

  static String write(TrainingDataStore db, AccountProfile account, Instant exportedAt)
      throws Exception {
    List<Lift> lifts = new ArrayList<>(db.listLifts());
    lifts.sort(Comparator.comparing(Lift::name, String.CASE_INSENSITIVE_ORDER));
    Map<String, Boolean> enabledStatuses = db.liftEnabledStatuses();
    Map<String, List<LiftExecution>> executionsByLift =
        db.getExecutionsByLift(lifts.stream().map(Lift::name).toList());

    List<JsonExecution> executions = new ArrayList<>();
    for (Lift lift : lifts) {
      for (LiftExecution execution : executionsByLift.getOrDefault(lift.name(), List.of())) {
        executions.add(JsonExecution.from(lift.name(), execution));
      }
    }
    executions.sort(
        Comparator.comparing(JsonExecution::date)
            .thenComparing(JsonExecution::liftName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(JsonExecution::id, Comparator.nullsLast(Integer::compareTo)));

    List<JsonSavedWorkout> savedWorkouts = new ArrayList<>();
    for (SavedWorkout workout : db.listSavedWorkouts()) {
      JsonNode document = JSON_MAPPER.readTree(db.getSavedWorkoutJson(workout.id()));
      savedWorkouts.add(JsonSavedWorkout.from(workout, document));
    }
    savedWorkouts.sort(
        Comparator.comparing(JsonSavedWorkout::createdAt).thenComparing(JsonSavedWorkout::id));

    JsonDocument document =
        new JsonDocument(
            SCHEMA_VERSION,
            exportedAt.toString(),
            new JsonAccount(blankToNull(account.username())),
            lifts.stream()
                .map(
                    lift ->
                        JsonCatalogEntry.from(
                            lift, enabledStatuses.getOrDefault(lift.name(), true)))
                .toList(),
            executions,
            savedWorkouts);
    return JSON.writeValueAsString(document) + System.lineSeparator();
  }

  static String fileName(Instant exportedAt) {
    return "lifttrax-account-" + FILE_TIME.format(exportedAt) + ".json";
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  private record JsonDocument(
      int schemaVersion,
      String exportedAt,
      JsonAccount account,
      List<JsonCatalogEntry> catalog,
      List<JsonExecution> executions,
      List<JsonSavedWorkout> plannedWorkouts) {}

  private record JsonAccount(String username) {}

  private record JsonCatalogEntry(
      String name,
      String region,
      String main,
      List<String> muscles,
      String notes,
      boolean enabled) {
    private static JsonCatalogEntry from(Lift lift, boolean enabled) {
      return new JsonCatalogEntry(
          lift.name(),
          lift.region().name(),
          lift.main() == null ? null : lift.main().name(),
          lift.muscles().stream().map(Enum::name).toList(),
          lift.notes(),
          enabled);
    }
  }

  private record JsonExecution(
      Integer id,
      String liftName,
      String date,
      boolean warmup,
      boolean deload,
      String notes,
      List<JsonSet> sets) {
    private static JsonExecution from(String liftName, LiftExecution execution) {
      return new JsonExecution(
          execution.id(),
          liftName,
          execution.date().toString(),
          execution.warmup(),
          execution.deload(),
          execution.notes(),
          execution.sets().stream().map(JsonSet::from).toList());
    }
  }

  private record JsonSet(JsonMetric metric, String weight, Float rpe, boolean missed) {
    private static JsonSet from(ExecutionSet set) {
      return new JsonSet(jsonMetric(set.metric()), set.weight(), set.rpe(), set.missed());
    }
  }

  private sealed interface JsonMetric
      permits JsonRepsMetric,
          JsonRepsLrMetric,
          JsonRepsRangeMetric,
          JsonTimeSecondsMetric,
          JsonDistanceFeetMetric {}

  private record JsonRepsMetric(String kind, int reps) implements JsonMetric {
    private JsonRepsMetric(int reps) {
      this("reps", reps);
    }
  }

  private record JsonRepsLrMetric(String kind, int left, int right) implements JsonMetric {
    private JsonRepsLrMetric(int left, int right) {
      this("reps_lr", left, right);
    }
  }

  private record JsonRepsRangeMetric(String kind, int min, int max) implements JsonMetric {
    private JsonRepsRangeMetric(int min, int max) {
      this("reps_range", min, max);
    }
  }

  private record JsonTimeSecondsMetric(String kind, int seconds) implements JsonMetric {
    private JsonTimeSecondsMetric(int seconds) {
      this("time_seconds", seconds);
    }
  }

  private record JsonDistanceFeetMetric(String kind, int feet) implements JsonMetric {
    private JsonDistanceFeetMetric(int feet) {
      this("distance_feet", feet);
    }
  }

  private static JsonMetric jsonMetric(SetMetric metric) {
    if (metric instanceof SetMetric.Reps reps) {
      return new JsonRepsMetric(reps.reps());
    }
    if (metric instanceof SetMetric.RepsLr repsLr) {
      return new JsonRepsLrMetric(repsLr.left(), repsLr.right());
    }
    if (metric instanceof SetMetric.RepsRange range) {
      return new JsonRepsRangeMetric(range.min(), range.max());
    }
    if (metric instanceof SetMetric.TimeSecs seconds) {
      return new JsonTimeSecondsMetric(seconds.seconds());
    }
    if (metric instanceof SetMetric.DistanceFeet feet) {
      return new JsonDistanceFeetMetric(feet.feet());
    }
    throw new IllegalArgumentException("Unsupported set metric: " + metric);
  }

  private record JsonSavedWorkout(String id, String name, String createdAt, JsonNode document) {
    private static JsonSavedWorkout from(SavedWorkout workout, JsonNode document) {
      return new JsonSavedWorkout(
          workout.id(),
          workout.name(),
          workout.createdAt().atOffset(ZoneOffset.UTC).toInstant().toString(),
          document);
    }
  }
}
