package com.lifttrax.cli;

import com.lifttrax.db.SqliteDb;
import com.lifttrax.workout.ConjugateWorkoutBuilder;
import com.lifttrax.workout.WaveMarkdownWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/** Core WaveCli component used by LiftTrax. */
public final class WaveCli {
  private WaveCli() {}

  public static void main(String[] args) throws Exception {
    String dbPath = DbPathResolver.resolveFromArgsOrDefault(args);
    int weeks = args.length > 1 ? Integer.parseInt(args[1]) : 7;
    String defaultOutput =
        "wave-"
            + ZonedDateTime.now(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
            + ".md";
    String output = args.length > 2 ? args[2] : defaultOutput;

    try (SqliteDb db = new SqliteDb(dbPath)) {
      ConjugateWorkoutBuilder builder = new ConjugateWorkoutBuilder();
      var wave = builder.getWave(weeks, db);
      Files.write(Path.of(output), WaveMarkdownWriter.createMarkdown(wave, db));
      System.out.println("Wrote wave markdown to " + output);
    }
  }
}
