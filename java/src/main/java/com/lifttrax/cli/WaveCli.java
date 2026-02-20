package com.lifttrax.cli;

import com.lifttrax.db.SqliteDb;
import com.lifttrax.workout.ConjugateWorkoutBuilder;
import com.lifttrax.workout.HypertrophyWorkoutBuilder;
import com.lifttrax.workout.WorkoutBuilder;
import com.lifttrax.workout.WaveMarkdownWriter;

import java.nio.file.Files;
import java.nio.file.Path;

public final class WaveCli {
    private WaveCli() {}

    public static void main(String[] args) throws Exception {
        String dbPath = args.length > 0 ? args[0] : "lifts.db";
        int weeks = args.length > 1 ? Integer.parseInt(args[1]) : 7;
        String output = args.length > 2 ? args[2] : "wave.md";
        String program = args.length > 3 ? args[3] : "conjugate";

        try (SqliteDb db = new SqliteDb(dbPath)) {
            WorkoutBuilder builder = builderForProgram(program);
            var wave = builder.getWave(weeks, db);
            Files.write(Path.of(output), WaveMarkdownWriter.createMarkdown(wave, db));
            System.out.println("Wrote wave markdown to " + output);
        }
    }

    static WorkoutBuilder builderForProgram(String program) {
        if (program == null || program.isBlank() || program.equalsIgnoreCase("conjugate")) {
            return new ConjugateWorkoutBuilder();
        }
        if (program.equalsIgnoreCase("hypertrophy")) {
            return new HypertrophyWorkoutBuilder();
        }

        throw new IllegalArgumentException("unknown program: " + program + " (supported: conjugate, hypertrophy)");
    }
}
