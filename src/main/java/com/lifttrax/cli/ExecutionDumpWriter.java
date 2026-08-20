package com.lifttrax.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.lifttrax.db.LiftExecutionRow;
import com.lifttrax.db.TrainingDataStore;
import com.lifttrax.models.ExecutionSet;
import com.lifttrax.models.Lift;
import com.lifttrax.models.LiftExecution;
import com.lifttrax.models.SetMetric;
import java.io.PrintStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Writes account-scoped execution history in versioned JSON or readable text. */
final class ExecutionDumpWriter {
  static final int SCHEMA_VERSION = 1;
  private static final ObjectWriter JSON = new ObjectMapper().writerWithDefaultPrettyPrinter();

  private ExecutionDumpWriter() {}

  static void write(
      TrainingDataStore db, LocalDate from, LocalDate to, Format format, PrintStream output)
      throws Exception {
    List<LiftExecutionRow> rows = loadRows(db, from, to);
    if (format == Format.JSON) {
      writeJson(rows, from, to, output);
    } else {
      writeHuman(rows, from, to, output);
    }
  }

  private static List<LiftExecutionRow> loadRows(TrainingDataStore db, LocalDate from, LocalDate to)
      throws Exception {
    List<Lift> lifts = db.listLifts();
    Map<String, List<LiftExecution>> executionsByLift =
        db.getExecutionsByLift(lifts.stream().map(Lift::name).toList());
    List<LiftExecutionRow> rows = new ArrayList<>();
    for (Lift lift : lifts) {
      for (LiftExecution execution : executionsByLift.getOrDefault(lift.name(), List.of())) {
        if (withinRange(execution.date(), from, to)) {
          rows.add(new LiftExecutionRow(lift, execution));
        }
      }
    }
    rows.sort(
        Comparator.comparing((LiftExecutionRow row) -> row.execution().date())
            .thenComparing(row -> row.lift().name())
            .thenComparing(row -> row.execution().id(), Comparator.nullsLast(Integer::compareTo)));
    return rows;
  }

  private static boolean withinRange(LocalDate date, LocalDate from, LocalDate to) {
    return (from == null || !date.isBefore(from)) && (to == null || !date.isAfter(to));
  }

  private static void writeJson(
      List<LiftExecutionRow> rows, LocalDate from, LocalDate to, PrintStream output)
      throws Exception {
    List<JsonExecution> executions = rows.stream().map(JsonExecution::from).toList();
    JsonDocument document =
        new JsonDocument(
            SCHEMA_VERSION,
            new JsonDateRange(dateValue(from), dateValue(to)),
            executions.size(),
            executions);
    output.println(JSON.writeValueAsString(document));
  }

  private static void writeHuman(
      List<LiftExecutionRow> rows, LocalDate from, LocalDate to, PrintStream output) {
    output.println("Execution dump");
    output.println("Date range: " + formatDateRange(from, to));
    output.println("Executions: " + rows.size());
    if (rows.isEmpty()) {
      output.println();
      output.println("No executions found.");
      return;
    }
    for (LiftExecutionRow row : rows) {
      LiftExecution execution = row.execution();
      output.println();
      output.println(
          execution.date() + " | " + row.lift().name() + " | execution " + execution.id());
      if (execution.sets().isEmpty()) {
        output.println("  Sets: (none)");
      } else {
        for (int index = 0; index < execution.sets().size(); index++) {
          output.println(
              "  Set "
                  + (index + 1)
                  + ": "
                  + DumpDatabaseCli.formatSet(execution.sets().get(index)));
        }
      }
      String tags = DumpDatabaseCli.formatTags(execution).trim();
      if (!tags.isEmpty()) {
        output.println("  Tags: " + tags);
      }
      if (execution.notes() != null && !execution.notes().isBlank()) {
        output.println("  Notes: " + execution.notes());
      }
    }
  }

  private static String formatDateRange(LocalDate from, LocalDate to) {
    if (from == null && to == null) {
      return "all dates";
    }
    if (from == null) {
      return "through " + to;
    }
    if (to == null) {
      return "from " + from;
    }
    return from + " through " + to;
  }

  private static String dateValue(LocalDate date) {
    return date == null ? null : date.toString();
  }

  enum Format {
    JSON,
    HUMAN;

    static Format parse(String value) {
      for (Format format : values()) {
        if (format.name().equalsIgnoreCase(value)) {
          return format;
        }
      }
      throw new IllegalArgumentException("Unsupported execution dump format: " + value);
    }
  }

  private record JsonDocument(
      int schemaVersion,
      JsonDateRange dateRange,
      int executionCount,
      List<JsonExecution> executions) {}

  private record JsonDateRange(String from, String to) {}

  private record JsonExecution(
      Integer id,
      JsonLift lift,
      String date,
      boolean warmup,
      boolean deload,
      String notes,
      List<JsonSet> sets) {
    private static JsonExecution from(LiftExecutionRow row) {
      LiftExecution execution = row.execution();
      return new JsonExecution(
          execution.id(),
          JsonLift.from(row.lift()),
          execution.date().toString(),
          execution.warmup(),
          execution.deload(),
          execution.notes(),
          execution.sets().stream().map(JsonSet::from).toList());
    }
  }

  private record JsonLift(
      String name, String region, String main, List<String> muscles, String notes) {
    private static JsonLift from(Lift lift) {
      return new JsonLift(
          lift.name(),
          lift.region().name(),
          lift.main() == null ? null : lift.main().name(),
          lift.muscles().stream().map(Enum::name).toList(),
          lift.notes());
    }
  }

  private record JsonSet(JsonMetric metric, String weight, Float rpe) {
    private static JsonSet from(ExecutionSet set) {
      return new JsonSet(jsonMetric(set.metric()), set.weight(), set.rpe());
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
}
