package org.lift.trax.workoutbuilder;

import java.time.DayOfWeek;
import java.util.List;

import org.lift.trax.Database;
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
                        System.out.println("  Circuit (rest " + cl.restTimeSec + "s)");
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
        if (sl.repCount != null) sb.append(" x").append(sl.repCount);
        if (sl.timeSec != null) sb.append(" ").append(sl.timeSec).append("s");
        if (sl.distanceM != null) sb.append(" ").append(sl.distanceM).append("m");
        if (sl.percent != null) sb.append(" @").append(sl.percent).append("%");
        if (sl.accommodatingResistance != null && sl.accommodatingResistance != AccommodatingResistance.NONE) {
            sb.append(" + ").append(sl.accommodatingResistance.name().toLowerCase());
        }
        System.out.println(sb.toString());
    }
}

