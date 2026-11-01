package com.lifttrax.workout;

import com.lifttrax.db.Database;
import com.lifttrax.models.SetMetric;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Core WaveMarkdownWriter component used by LiftTrax. */
public final class WaveMarkdownWriter {
  private WaveMarkdownWriter() {}

  public static List<String> createMarkdown(List<Map<DayOfWeek, Workout>> wave, Database db)
      throws Exception {
    List<String> out = new ArrayList<>();
    DayOfWeek[] order = {
      DayOfWeek.MONDAY,
      DayOfWeek.TUESDAY,
      DayOfWeek.WEDNESDAY,
      DayOfWeek.THURSDAY,
      DayOfWeek.FRIDAY,
      DayOfWeek.SATURDAY,
      DayOfWeek.SUNDAY
    };

    for (int i = 0; i < wave.size(); i++) {
      out.add("# Week " + (i + 1));
      Map<DayOfWeek, Workout> week = wave.get(i);
      for (DayOfWeek day : order) {
        Workout workout = week.get(day);
        if (workout == null) {
          continue;
        }
        out.add("## " + title(day));
        out.addAll(workoutLines(workout, db));
        out.add("");
      }
      out.add("");
    }
    return out;
  }

  private static List<String> workoutLines(Workout workout, Database db) throws Exception {
    List<String> lines = new ArrayList<>();
    int i = 0;
    while (i < workout.lifts().size()) {
      WorkoutLift lift = workout.lifts().get(i);
      lines.add("### " + lift.name());
      if (lift.kind() instanceof WorkoutLiftKind.SingleKind sk) {
        int count = matchingSingles(workout, i, sk.singleLift());
        appendSingle(lines, sk.singleLift(), count, db);
        i += count;
      } else if (lift.kind() instanceof WorkoutLiftKind.CircuitKind ck) {
        appendCircuit(lines, ck.circuitLift(), db);
        i += 1;
      }
    }
    return lines;
  }

  private static int matchingSingles(Workout workout, int index, SingleLift first) {
    int count = 1;
    while (index + count < workout.lifts().size()) {
      WorkoutLift next = workout.lifts().get(index + count);
      if (!(next.kind() instanceof WorkoutLiftKind.SingleKind kind)
          || !sameSingle(first, kind.singleLift())) {
        break;
      }
      count++;
    }
    return count;
  }

  private static void appendSingle(List<String> lines, SingleLift lift, int count, Database db)
      throws Exception {
    lines.add(singleDesc(lift, count, db));
    appendNotes(lines, lift, "   ");
    appendHistory(lines, lift, false, db, "   ");
  }

  private static void appendCircuit(List<String> lines, CircuitLift circuit, Database db)
      throws Exception {
    lines.add("- Circuit: " + circuit.rounds() + " rounds");
    for (int index = 0; index < circuit.circuitLifts().size(); index++) {
      SingleLift lift = circuit.circuitLifts().get(index);
      String description =
          circuit.warmup()
              ? "**" + lift.lift().name() + "**"
              : singleDesc(lift, circuit.rounds(), db);
      lines.add("  " + (index + 1) + ". " + description);
      appendNotes(lines, lift, "     ");
      appendHistory(lines, lift, circuit.warmup(), db, "     ");
    }
  }

  private static void appendNotes(List<String> lines, SingleLift lift, String indent) {
    if (!lift.lift().notes().isEmpty()) {
      lines.add(indent + "- Notes: " + lift.lift().notes());
    }
  }

  private static void appendHistory(
      List<String> lines, SingleLift lift, boolean warmup, Database db, String indent)
      throws Exception {
    String last =
        WorkoutHistoryFormatter.lastExecutionSummary(db, lift.lift().name(), warmup, lift.deload());
    if (last != null) {
      lines.add(indent + "- Last: " + last);
    }
    String max = WorkoutHistoryFormatter.bestOneRepMax(db, lift.lift().name());
    if (max != null) {
      lines.add(indent + "- Best 1RM: " + max);
    }
  }

  private static boolean sameSingle(SingleLift a, SingleLift b) {
    return a.lift().name().equals(b.lift().name())
        && equal(a.metric(), b.metric())
        && equal(a.percent(), b.percent())
        && equal(a.rpe(), b.rpe())
        && equal(a.accommodatingResistance(), b.accommodatingResistance());
  }

  private static String singleDesc(SingleLift s, int count, Database db) {
    List<String> parts = new ArrayList<>();
    parts.add("**" + s.lift().name() + "**");
    if (s.metric() != null) {
      String metric = formatMetric(s.metric());
      if (count > 1) {
        parts.add(count + "x " + metric);
      } else {
        parts.add(metric);
      }
    } else if (count > 1) {
      parts.add(count + "x");
    }
    if (s.percent() != null) {
      parts.add("@ " + s.percent() + "%");
    }
    if (s.rpe() != null) {
      parts.add("RPE " + s.rpe());
    }
    if (s.accommodatingResistance() == AccommodatingResistance.CHAINS) {
      parts.add("Chains");
    } else if (s.accommodatingResistance() == AccommodatingResistance.BANDS) {
      parts.add("Bands");
    }
    String suggestion = suggestedWeight(s, db);
    if (!suggestion.isBlank()) {
      parts.add("(suggested " + suggestion + ")");
    }
    return String.join(" ", parts);
  }

  private static String suggestedWeight(SingleLift single, Database db) {
    PlannedWorkoutFile.PlannedSetTarget target =
        PlannedWorkoutExporter.plannedSetForDescription(single);
    return PlannedWorkoutText.suggestedWeight(db, single.lift().name(), target);
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
    if (metric instanceof SetMetric.TimeSecs time) {
      return time.seconds() + " sec";
    }
    if (metric instanceof SetMetric.DistanceFeet dist) {
      return dist.feet() + " ft";
    }
    return "";
  }

  private static String title(DayOfWeek day) {
    return day.name().charAt(0) + day.name().substring(1).toLowerCase(java.util.Locale.ROOT);
  }

  private static boolean equal(Object a, Object b) {
    return a == null ? b == null : a.equals(b);
  }
}
