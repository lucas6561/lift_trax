package com.lifttrax.cli;

import com.lifttrax.db.TrainingDataStore;
import com.lifttrax.db.TrainingDataStoreProvider;
import com.lifttrax.models.ExecutionSet;
import com.lifttrax.models.Lift;
import com.lifttrax.models.LiftExecution;
import com.lifttrax.models.SetMetric;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Core DumpDatabaseCli component used by LiftTrax. */
public final class DumpDatabaseCli {
  private DumpDatabaseCli() {}

  public static void main(String[] args) throws Exception {
    CliOptions options = parseArgs(args);
    try (TrainingDataStoreProvider provider = TrainingDataStoreProvider.fromEnvironment()) {
      TrainingDataStore db = provider.forUserIdentifier(options.userId());
      if (options.executionsOnly()) {
        ExecutionDumpWriter.write(db, options.from(), options.to(), options.format(), System.out);
      } else {
        dump(db, options.liftsOnly(), options.includeDisabled());
      }
    }
  }

  static void dump(TrainingDataStore db, boolean liftsOnly) throws Exception {
    dump(db, liftsOnly, false);
  }

  static void dump(TrainingDataStore db, boolean liftsOnly, boolean includeDisabled)
      throws Exception {
    List<Lift> lifts = db.listLifts();
    if (liftsOnly && !includeDisabled) {
      Map<String, Boolean> enabledStatuses = db.liftEnabledStatuses();
      lifts =
          lifts.stream().filter(lift -> enabledStatuses.getOrDefault(lift.name(), true)).toList();
    }
    if (lifts.isEmpty()) {
      System.out.println("No lifts found.");
      return;
    }
    for (Lift lift : lifts) {
      System.out.println(formatLiftHeader(lift));
      if (!liftsOnly) {
        List<LiftExecution> executions = db.getExecutions(lift.name());
        if (executions.isEmpty()) {
          System.out.println("  (no executions)");
        } else {
          for (LiftExecution execution : executions) {
            System.out.println("  - " + formatExecution(execution));
          }
        }
      }
      System.out.println();
    }
  }

  private static CliOptions parseArgs(String... args) {
    CliOptionsBuilder options = new CliOptionsBuilder();
    int index = 0;
    while (index < args.length) {
      index += options.consume(args, index);
    }
    return options.build();
  }

  private static final class CliOptionsBuilder {
    private boolean liftsOnly;
    private boolean includeDisabled;
    private boolean executionsOnly;
    private boolean formatSpecified;
    private LocalDate from;
    private LocalDate to;
    private ExecutionDumpWriter.Format format = ExecutionDumpWriter.Format.JSON;
    private String userId;

    int consume(String[] args, int index) {
      String arg = args[index];
      return switch (arg) {
        case "--lifts-only" -> setLiftsOnly();
        case "--include-disabled" -> setIncludeDisabled();
        case "--executions-only" -> setExecutionsOnly();
        case "--from" -> setFrom(args, index, arg);
        case "--to" -> setTo(args, index, arg);
        case "--format" -> setFormat(args, index, arg);
        case "--user" -> setUser(args, index, arg);
        default -> throw unexpected(arg);
      };
    }

    private int setLiftsOnly() {
      liftsOnly = true;
      return 1;
    }

    private int setIncludeDisabled() {
      includeDisabled = true;
      return 1;
    }

    private int setExecutionsOnly() {
      executionsOnly = true;
      return 1;
    }

    private int setFrom(String[] args, int index, String option) {
      from = parseDate(optionValue(args, index, option), option);
      return 2;
    }

    private int setTo(String[] args, int index, String option) {
      to = parseDate(optionValue(args, index, option), option);
      return 2;
    }

    private int setFormat(String[] args, int index, String option) {
      format = ExecutionDumpWriter.Format.parse(optionValue(args, index, option));
      formatSpecified = true;
      return 2;
    }

    private int setUser(String[] args, int index, String option) {
      userId = optionValue(args, index, option);
      return 2;
    }

    private static IllegalArgumentException unexpected(String arg) {
      String kind = arg.startsWith("--") ? "Unknown option: " : "Unexpected argument: ";
      return new IllegalArgumentException(kind + arg);
    }

    CliOptions build() {
      validateExecutionOptions(liftsOnly, executionsOnly, formatSpecified, from, to);
      return new CliOptions(
          CliUserResolver.resolve(userId),
          liftsOnly,
          includeDisabled,
          executionsOnly,
          from,
          to,
          format);
    }
  }

  private static String optionValue(String[] args, int index, String option) {
    if (index + 1 >= args.length) {
      throw new IllegalArgumentException("Missing value after " + option);
    }
    return args[index + 1];
  }

  private static LocalDate parseDate(String value, String option) {
    try {
      return LocalDate.parse(value);
    } catch (DateTimeParseException error) {
      throw new IllegalArgumentException(option + " must use YYYY-MM-DD: " + value, error);
    }
  }

  private static void validateExecutionOptions(
      boolean liftsOnly,
      boolean executionsOnly,
      boolean formatSpecified,
      LocalDate from,
      LocalDate to) {
    rejectConflictingOutputModes(liftsOnly, executionsOnly);
    rejectExecutionFiltersWithoutExecutionMode(executionsOnly, formatSpecified, from, to);
    rejectReversedDateRange(from, to);
  }

  private static void rejectConflictingOutputModes(boolean liftsOnly, boolean executionsOnly) {
    if (liftsOnly && executionsOnly) {
      throw new IllegalArgumentException("--lifts-only cannot be combined with --executions-only");
    }
  }

  private static void rejectExecutionFiltersWithoutExecutionMode(
      boolean executionsOnly, boolean formatSpecified, LocalDate from, LocalDate to) {
    if (!executionsOnly && (formatSpecified || from != null || to != null)) {
      throw new IllegalArgumentException("--format, --from, and --to require --executions-only");
    }
  }

  private static void rejectReversedDateRange(LocalDate from, LocalDate to) {
    if (from != null && to != null && from.isAfter(to)) {
      throw new IllegalArgumentException("--from must be on or before --to");
    }
  }

  private record CliOptions(
      String userId,
      boolean liftsOnly,
      boolean includeDisabled,
      boolean executionsOnly,
      LocalDate from,
      LocalDate to,
      ExecutionDumpWriter.Format format) {}

  private static String formatLiftHeader(Lift lift) {
    String main = lift.main() == null ? "" : " [" + lift.main() + "]";
    String muscles =
        lift.muscles().isEmpty()
            ? ""
            : " ["
                + lift.muscles().stream().map(Enum::name).collect(Collectors.joining(", "))
                + "]";
    String notes = lift.notes() == null || lift.notes().isBlank() ? "" : " - " + lift.notes();
    return lift.name() + " (" + lift.region() + ")" + main + muscles + notes;
  }

  private static String formatExecution(LiftExecution execution) {
    String sets =
        execution.sets().stream().map(DumpDatabaseCli::formatSet).collect(Collectors.joining(", "));
    String tags = formatTags(execution);
    String notes =
        execution.notes() == null || execution.notes().isBlank() ? "" : " - " + execution.notes();
    return execution.date() + ": " + sets + tags + notes;
  }

  static String formatTags(LiftExecution execution) {
    if (execution.warmup() && execution.deload()) {
      return " (warm-up, deload)";
    }
    if (execution.warmup()) {
      return " (warm-up)";
    }
    if (execution.deload()) {
      return " (deload)";
    }
    return "";
  }

  static String formatSet(ExecutionSet set) {
    String metric = formatMetric(set.metric());
    String weight =
        set.weight() == null || "none".equalsIgnoreCase(set.weight()) ? "" : " @ " + set.weight();
    String rpe = set.rpe() == null ? "" : " RPE " + set.rpe();
    return metric + weight + rpe + (set.missed() ? " (missed target)" : "");
  }

  private static String formatMetric(SetMetric metric) {
    if (metric instanceof SetMetric.Reps reps) {
      return reps.reps() + " reps";
    }
    if (metric instanceof SetMetric.RepsLr repsLr) {
      return repsLr.left() + "|" + repsLr.right() + " reps";
    }
    if (metric instanceof SetMetric.RepsRange range) {
      return range.min() + "-" + range.max() + " reps";
    }
    if (metric instanceof SetMetric.TimeSecs secs) {
      return secs.seconds() + " sec";
    }
    if (metric instanceof SetMetric.DistanceFeet feet) {
      return feet.feet() + " ft";
    }
    return "unknown metric";
  }
}
