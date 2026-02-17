package com.lifttrax.workout;

import com.lifttrax.db.Database;
import com.lifttrax.models.ExecutionSet;
import com.lifttrax.models.LiftExecution;
import com.lifttrax.models.SetMetric;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class WaveMarkdownWriter {
    private WaveMarkdownWriter() {}

    public static List<String> createMarkdown(List<Map<DayOfWeek, Workout>> wave, Database db) throws Exception {
        List<String> out = new ArrayList<>();
        DayOfWeek[] order = {
                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY
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
                int count = 1;
                while (i + count < workout.lifts().size()) {
                    WorkoutLift next = workout.lifts().get(i + count);
                    if (next.kind() instanceof WorkoutLiftKind.SingleKind nk && sameSingle(sk.singleLift(), nk.singleLift())) {
                        count++;
                    } else {
                        break;
                    }
                }

                SingleLift s = sk.singleLift();
                lines.add(singleDesc(s, count));
                if (!s.lift().notes().isEmpty()) {
                    lines.add("   - Notes: " + s.lift().notes());
                }
                SetMetric historyMetric = s.metric() instanceof SetMetric.RepsRange ? null : s.metric();
                String last = lastExecDesc(db, s.lift().name(), false, historyMetric, s.deload());
                if (last != null) {
                    lines.add("   - Last: " + last);
                }
                String max = lastOneRepMax(db, s.lift().name());
                if (max != null) {
                    lines.add("   - Last 1RM: " + max);
                }
                i += count;
            } else if (lift.kind() instanceof WorkoutLiftKind.CircuitKind ck) {
                CircuitLift circuit = ck.circuitLift();
                lines.add("- Circuit: " + circuit.rounds() + " rounds");
                for (int j = 0; j < circuit.circuitLifts().size(); j++) {
                    SingleLift sl = circuit.circuitLifts().get(j);
                    String desc = circuit.warmup() ? "**" + sl.lift().name() + "**" : singleDesc(sl, circuit.rounds());
                    lines.add("  " + (j + 1) + ". " + desc);
                    if (!sl.lift().notes().isEmpty()) {
                        lines.add("     - Notes: " + sl.lift().notes());
                    }
                    String last = lastExecDesc(db, sl.lift().name(), circuit.warmup(), null, sl.deload());
                    if (last != null) {
                        lines.add("     - Last: " + last);
                    }
                    String max = lastOneRepMax(db, sl.lift().name());
                    if (max != null) {
                        lines.add("     - Last 1RM: " + max);
                    }
                }
                i += 1;
            }
        }
        return lines;
    }

    private static boolean sameSingle(SingleLift a, SingleLift b) {
        return a.lift().name().equals(b.lift().name())
                && equal(a.metric(), b.metric())
                && equal(a.percent(), b.percent())
                && equal(a.rpe(), b.rpe())
                && equal(a.accommodatingResistance(), b.accommodatingResistance());
    }

    private static String singleDesc(SingleLift s, int count) {
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
        return String.join(" ", parts);
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

    private static String lastExecDesc(Database db, String liftName, boolean warmup, SetMetric metric, boolean includeDeload) throws Exception {
        List<LiftExecution> executions = db.getExecutions(liftName).stream()
                .filter(e -> e.warmup() == warmup && (includeDeload || !e.deload()))
                .sorted(Comparator.comparing(LiftExecution::date).thenComparing(e -> e.id() == null ? Integer.MIN_VALUE : e.id()).reversed())
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));

        if (executions.isEmpty()) {
            return null;
        }

        if (metric != null) {
            prioritizeMetric(executions, metric);
        }

        List<String> summaries = new ArrayList<>();
        for (int i = 0; i < Math.min(3, executions.size()); i++) {
            summaries.add(formatExec(executions.get(i)));
        }
        return String.join(" | ", summaries);
    }

    private static void prioritizeMetric(List<LiftExecution> executions, SetMetric target) {
        for (int i = 0; i < executions.size(); i++) {
            ExecutionSet first = executions.get(i).sets().isEmpty() ? null : executions.get(i).sets().get(0);
            if (first != null && metricEquals(first.metric(), target)) {
                LiftExecution match = executions.remove(i);
                executions.add(0, match);
                return;
            }
        }

        int bestIdx = -1;
        int bestDiff = Integer.MAX_VALUE;
        for (int i = 0; i < executions.size(); i++) {
            ExecutionSet first = executions.get(i).sets().isEmpty() ? null : executions.get(i).sets().get(0);
            if (first == null) {
                continue;
            }
            Integer diff = metricDistance(first.metric(), target);
            if (diff != null && diff < bestDiff) {
                bestDiff = diff;
                bestIdx = i;
            }
        }
        if (bestIdx >= 0) {
            LiftExecution match = executions.remove(bestIdx);
            executions.add(0, match);
        }
    }

    private static Integer metricDistance(SetMetric candidate, SetMetric target) {
        if (candidate instanceof SetMetric.Reps a && target instanceof SetMetric.Reps b) {
            return Math.abs(a.reps() - b.reps());
        }
        if (candidate instanceof SetMetric.RepsLr a && target instanceof SetMetric.RepsLr b) {
            return Math.abs(a.left() - b.left()) + Math.abs(a.right() - b.right());
        }
        if (candidate instanceof SetMetric.RepsLr a && target instanceof SetMetric.Reps b) {
            return Math.abs(((a.left() + a.right()) / 2) - b.reps());
        }
        if (candidate instanceof SetMetric.Reps a && target instanceof SetMetric.RepsLr b) {
            return Math.abs(a.reps() - ((b.left() + b.right()) / 2));
        }
        if (candidate instanceof SetMetric.TimeSecs a && target instanceof SetMetric.TimeSecs b) {
            return Math.abs(a.seconds() - b.seconds());
        }
        if (candidate instanceof SetMetric.DistanceFeet a && target instanceof SetMetric.DistanceFeet b) {
            return Math.abs(a.feet() - b.feet());
        }
        return null;
    }

    private static boolean metricEquals(SetMetric a, SetMetric b) {
        return a.equals(b);
    }

    private static String formatExec(LiftExecution exec) {
        if (exec.sets().isEmpty()) {
            return exec.notes().isEmpty() ? "no sets recorded" : "no sets recorded - " + exec.notes();
        }
        ExecutionSet first = exec.sets().get(0);
        String rpe = first.rpe() == null ? "" : " RPE " + first.rpe();
        String weight = first.weight() == null || first.weight().equalsIgnoreCase("none") ? "" : "@ " + first.weight();
        String notes = exec.notes().isEmpty() ? "" : " - " + exec.notes();
        String tags = tagSuffix(exec);
        return exec.sets().size() + " sets x " + formatMetric(first.metric()) + " " + weight + rpe + tags + notes;
    }

    private static String tagSuffix(LiftExecution exec) {
        List<String> tags = new ArrayList<>();
        if (exec.warmup()) {
            tags.add("warm-up");
        }
        if (exec.deload()) {
            tags.add("deload");
        }
        if (tags.isEmpty()) {
            return "";
        }
        return " (" + String.join(", ", tags) + ")";
    }

    private static String lastOneRepMax(Database db, String liftName) throws Exception {
        for (LiftExecution exec : db.getExecutions(liftName)) {
            if (exec.warmup()) {
                continue;
            }
            for (ExecutionSet set : exec.sets()) {
                if (set.metric() instanceof SetMetric.Reps reps && reps.reps() == 1
                        && set.weight() != null && !set.weight().equalsIgnoreCase("none")) {
                    return set.weight();
                }
            }
        }
        return null;
    }

    private static String title(DayOfWeek day) {
        return day.name().charAt(0) + day.name().substring(1).toLowerCase();
    }

    private static boolean equal(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }
}
