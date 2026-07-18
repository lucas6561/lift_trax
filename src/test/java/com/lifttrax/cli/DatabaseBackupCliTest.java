package com.lifttrax.cli;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lifttrax.db.SqliteDb;
import com.lifttrax.models.LiftRegion;
import com.lifttrax.models.LiftType;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class DatabaseBackupCliTest {

  @Test
  void hostedImportCliPreviewReportsValidatedSource() throws Exception {
    Path tempDir = Files.createTempDirectory("lifttrax-import-hosted-cli");
    Path sourcePath = tempDir.resolve("source.db");
    try (SqliteDb db = new SqliteDb(sourcePath.toString())) {
      db.addLift("Rows", LiftRegion.UPPER, LiftType.ACCESSORY, List.of(), "");
    }

    String output =
        captureOutput(
            () -> ImportHostedDatabaseCli.main(new String[] {sourcePath.toString(), "--preview"}));

    assertTrue(output.contains("Import source:"));
    assertTrue(output.contains("Schema version:"));
    assertTrue(output.contains("Lifts: 1"));
    assertTrue(output.contains("Fingerprint:"));
  }

  private static String captureOutput(ThrowingRunnable action) throws Exception {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    PrintStream originalOut = System.out;
    try {
      System.setOut(new PrintStream(output));
      action.run();
    } finally {
      System.setOut(originalOut);
    }
    return output.toString();
  }

  private interface ThrowingRunnable {
    void run() throws Exception;
  }
}
