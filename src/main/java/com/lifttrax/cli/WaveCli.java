package com.lifttrax.cli;

import com.lifttrax.db.TrainingDataStore;
import com.lifttrax.db.TrainingDataStoreProvider;
import com.lifttrax.workout.ConjugateWorkoutBuilder;
import com.lifttrax.workout.PlannedWorkoutExporter;
import com.lifttrax.workout.PlannedWorkoutJson;
import com.lifttrax.workout.WaveMarkdownWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Core WaveCli component used by LiftTrax. */
public final class WaveCli {
  private WaveCli() {}

  public static void main(String[] args) throws Exception {
    Options options = parseArgs(args);
    String defaultOutput =
        "wave-"
            + ZonedDateTime.now(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
            + ".md";
    String output = options.output() == null ? defaultOutput : options.output();

    try (TrainingDataStoreProvider provider = TrainingDataStoreProvider.fromEnvironment()) {
      TrainingDataStore db = provider.forUser(options.userId());
      ConjugateWorkoutBuilder builder = new ConjugateWorkoutBuilder();
      var wave = builder.getWave(options.weeks(), db);
      if (output.toLowerCase(Locale.ROOT).endsWith(".json")) {
        PlannedWorkoutJson.writePath(
            Path.of(output), PlannedWorkoutExporter.fromWave("Conjugate Wave", "conjugate", wave));
        System.out.println("Wrote planned workout JSON to " + output);
      } else {
        Files.write(Path.of(output), WaveMarkdownWriter.createMarkdown(wave, db));
        System.out.println("Wrote wave markdown to " + output);
      }
    }
  }

  private static Options parseArgs(String... args) {
    String userId = null;
    int weeks = 7;
    String output = null;
    int positional = 0;
    int index = 0;
    while (index < args.length) {
      String arg = args[index];
      if ("--user".equals(arg)) {
        if (index + 1 >= args.length) {
          throw new IllegalArgumentException("Missing value after --user");
        }
        userId = args[index + 1];
        index += 2;
      } else if (arg.startsWith("--")) {
        throw new IllegalArgumentException("Unknown option: " + arg);
      } else if (positional == 0) {
        weeks = Integer.parseInt(arg);
        positional++;
        index++;
      } else if (output == null) {
        output = arg;
        positional++;
        index++;
      } else {
        throw new IllegalArgumentException("Unexpected argument: " + arg);
      }
    }
    if (userId == null || userId.isBlank()) {
      throw new IllegalArgumentException("--user is required.");
    }
    return new Options(userId, weeks, output);
  }

  private record Options(String userId, int weeks, String output) {}
}
