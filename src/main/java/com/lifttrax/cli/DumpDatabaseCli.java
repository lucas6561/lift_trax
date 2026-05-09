package com.lifttrax.cli;

import com.lifttrax.db.SqliteDb;
import com.lifttrax.models.ExecutionSet;
import com.lifttrax.models.Lift;
import com.lifttrax.models.LiftExecution;
import com.lifttrax.models.SetMetric;
import java.util.List;
import java.util.stream.Collectors;

/** Core DumpDatabaseCli component used by LiftTrax. */
public class DumpDatabaseCli {
  public static void main(String[] args) throws Exception {
    CliOptions options = parseArgs(args);
    String dbPath =
        options.dbPath() == null
            ? DbPathResolver.resolveFromArgsOrDefault(new String[0])
            : options.dbPath();

    try (SqliteDb db = new SqliteDb(dbPath)) {
      List<Lift> lifts = db.listLifts();
      if (lifts.isEmpty()) {
        System.out.println("No lifts found.");
        return;
      }

      for (Lift lift : lifts) {
        System.out.println(formatLiftHeader(lift));
        if (!options.liftsOnly()) {
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
  }

  private static CliOptions parseArgs(String... args) {
    boolean liftsOnly = false;
    String dbPath = null;
    for (String arg : args) {
      if ("--lifts-only".equals(arg)) {
        liftsOnly = true;
        continue;
      }
      if (arg.startsWith("--")) {
        throw new IllegalArgumentException("Unknown option: " + arg);
      }
      if (dbPath != null) {
        throw new IllegalArgumentException("Unexpected argument: " + arg);
      }
      dbPath = arg;
    }
    return new CliOptions(dbPath, liftsOnly);
  }

  private record CliOptions(String dbPath, boolean liftsOnly) {}

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
