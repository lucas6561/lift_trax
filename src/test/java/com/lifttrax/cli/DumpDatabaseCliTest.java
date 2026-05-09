package com.lifttrax.cli;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lifttrax.db.SqliteDb;
import com.lifttrax.models.ExecutionSet;
import com.lifttrax.models.LiftExecution;
import com.lifttrax.models.LiftRegion;
import com.lifttrax.models.LiftType;
import com.lifttrax.models.Muscle;
import com.lifttrax.models.SetMetric;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class DumpDatabaseCliTest {

  @Test
  void liftsOnlyOptionPrintsLiftMetadataWithoutExecutions() throws Exception {
    Path tempDir = Files.createTempDirectory("lifttrax-dump-cli");
    Path dbPath = tempDir.resolve("lifts.db");

    try (SqliteDb db = new SqliteDb(dbPath.toString())) {
      db.addLift(
          "Front Squat",
          LiftRegion.LOWER,
          LiftType.SQUAT,
          List.of(Muscle.QUAD, Muscle.GLUTE),
          "heels elevated");
      db.addLiftExecution(
          "Front Squat",
          new LiftExecution(
              null,
              LocalDate.parse("2026-03-12"),
              List.of(new ExecutionSet(new SetMetric.Reps(5), "185 lb", 8.0f)),
              false,
              false,
              "smooth"));
    }

    ByteArrayOutputStream output = new ByteArrayOutputStream();
    PrintStream originalOut = System.out;
    try {
      System.setOut(new PrintStream(output));
      DumpDatabaseCli.main(new String[] {dbPath.toString(), "--lifts-only"});
    } finally {
      System.setOut(originalOut);
    }

    String text = output.toString();
    assertTrue(text.contains("Front Squat (LOWER) [SQUAT] [QUAD, GLUTE] - heels elevated"));
    assertFalse(text.contains("2026-03-12:"));
    assertFalse(text.contains("(no executions)"));
  }
}
