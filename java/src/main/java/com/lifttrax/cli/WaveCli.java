package com.lifttrax.cli;

import com.lifttrax.db.SqliteDb;
import com.lifttrax.workout.ConjugateWorkoutBuilder;
import com.lifttrax.workout.WaveMarkdownWriter;

import java.nio.file.Files;
import java.nio.file.Path;

public final class WaveCli {
    private WaveCli() {}

    public static void main(String[] args) throws Exception {
        String dbPath = args.length > 0 ? args[0] : "lifts.db";
        int weeks = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        String output = args.length > 2 ? args[2] : "wave.md";

        try (SqliteDb db = new SqliteDb(dbPath)) {
            ConjugateWorkoutBuilder builder = new ConjugateWorkoutBuilder();
            var wave = builder.getWave(weeks, db);
            Files.write(Path.of(output), WaveMarkdownWriter.createMarkdown(wave, db));
            System.out.println("Wrote wave markdown to " + output);
        }
    }
}
