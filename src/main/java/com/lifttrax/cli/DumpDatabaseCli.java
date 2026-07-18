package com.lifttrax.cli;

import com.lifttrax.db.TrainingDataStore;
import com.lifttrax.db.TrainingDataStoreProvider;
import com.lifttrax.models.ExecutionSet;
import com.lifttrax.models.Lift;
import com.lifttrax.models.LiftExecution;
import com.lifttrax.models.SetMetric;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Core DumpDatabaseCli component used by LiftTrax. */
public final class DumpDatabaseCli {
  private DumpDatabaseCli() {}

  public static void main(String[] args) throws Exception {
    CliOptions options = parseArgs(args);
    try (TrainingDataStoreProvider provider = TrainingDataStoreProvider.fromEnvironment()) {
      dump(provider.forUser(options.userId()), options.liftsOnly(), options.includeDisabled());
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
    boolean liftsOnly = false;
    boolean includeDisabled = false;
    String userId = null;
    int index = 0;
    while (index < args.length) {
      String arg = args[index];
      if ("--lifts-only".equals(arg)) {
        liftsOnly = true;
        index++;
        continue;
      }
      if ("--include-disabled".equals(arg)) {
        includeDisabled = true;
        index++;
        continue;
      }
      if ("--user".equals(arg)) {
        if (index + 1 >= args.length) {
          throw new IllegalArgumentException("Missing value after --user");
        }
        userId = args[index + 1];
        index += 2;
        continue;
      }
      if (arg.startsWith("--")) {
        throw new IllegalArgumentException("Unknown option: " + arg);
      }
      throw new IllegalArgumentException("Unexpected argument: " + arg);
    }
    return new CliOptions(CliUserResolver.resolve(userId), liftsOnly, includeDisabled);
  }

  private record CliOptions(String userId, boolean liftsOnly, boolean includeDisabled) {}

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

  private static String formatTags(LiftExecution execution) {
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

  private static String formatSet(ExecutionSet set) {
    String metric = formatMetric(set.metric());
    String weight =
        set.weight() == null || "none".equalsIgnoreCase(set.weight()) ? "" : " @ " + set.weight();
    String rpe = set.rpe() == null ? "" : " RPE " + set.rpe();
    return metric + weight + rpe;
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
