package org.lift.trax.workoutbuilder;

import java.time.DayOfWeek;
import java.util.List;

import org.lift.trax.Database;
import org.lift.trax.ExecutionSet;
import org.lift.trax.LiftExecution;
import org.lift.trax.SqliteDb;

public class WorkoutBuilderCli {
    public static void main(String[] args) throws Exception {
        String dbPath = args.length > 0 ? args[0] : "lift.db";
        int weeks = args.length > 1 ? Integer.parseInt(args[1]) : 4;

        Database db = new SqliteDb(dbPath);
        ConjugateWorkoutBuilder builder = new ConjugateWorkoutBuilder();
        List<WorkoutWeek> wave = builder.getWave(weeks, db);

        for (int i = 0; i < wave.size(); i++) {
            System.out.println("Week " + (i + 1) + ":");
            WorkoutWeek week = wave.get(i);
            for (DayOfWeek day : DayOfWeek.values()) {
                if (!week.containsKey(day)) continue;
                System.out.println(day + ":");
                Workout workout = week.get(day);
                for (WorkoutLift wl : workout.lifts) {
                    if (wl instanceof SingleLift sl) {
                        printSingleLift("  ", sl);
                    } else if (wl instanceof CircuitLift cl) {
                        System.out.println(
                                "  Circuit (" + cl.rounds + " rounds, rest " + cl.restTimeSec + "s)");
                        for (SingleLift sl : cl.circuitLifts) {
                            printSingleLift("    ", sl);
                        }
                    }
                }
            }
            System.out.println();
        }
    }

    private static void printSingleLift(String indent, SingleLift sl) {
        StringBuilder sb = new StringBuilder(indent).append("- ").append(sl.lift.name);
        if (sl.metric != null) {
            if (sl.metric.reps != null) sb.append(" x").append(sl.metric.reps);
            else if (sl.metric.timeSecs != null) sb.append(" ").append(sl.metric.timeSecs).append("s");
            else if (sl.metric.distanceM != null) sb.append(" ").append(sl.metric.distanceM).append("m");
        }
        if (sl.percent != null) sb.append(" @").append(sl.percent).append("%");
        if (sl.accommodatingResistance != null && sl.accommodatingResistance != AccommodatingResistance.NONE) {
            sb.append(" + ").append(sl.accommodatingResistance.name().toLowerCase());
        }
        System.out.println(sb.toString());

        if (sl.lift.notes != null && !sl.lift.notes.isBlank()) {
            System.out.println(indent + "    Notes: " + sl.lift.notes);
        }

        LiftExecution last = null;
        for (LiftExecution exec : sl.lift.executions) {
            if (!exec.warmup) {
                last = exec;
                break;
            }
        }
        if (last == null && !sl.lift.executions.isEmpty()) {
            last = sl.lift.executions.get(0);
        }
        if (last != null) {
            ExecutionSet first = last.sets.isEmpty() ? null : last.sets.get(0);
            StringBuilder desc = new StringBuilder(indent).append("    Last: ");
            if (first != null) {
                String metric = first.reps != null
                        ? first.reps + " reps"
                        : first.timeSecs != null
                                ? first.timeSecs + "s"
                                : first.distanceFeet != null
                                        ? first.distanceFeet + "ft"
                                        : "";
                desc.append(last.sets.size()).append(" sets");
                if (!metric.isEmpty()) desc.append(" x ").append(metric);
                String weight = first.displayWeight();
                if (weight != null && !weight.isBlank()) desc.append(" @ ").append(weight);
                if (first.rpe != null) desc.append(" RPE ").append(first.rpe);
            } else {
                desc.append("no sets recorded");
            }
            if (last.notes != null && !last.notes.isBlank()) {
                desc.append(" - ").append(last.notes);
            }
            System.out.println(desc.toString());
        }
    }
}

