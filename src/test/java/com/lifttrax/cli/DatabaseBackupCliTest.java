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
  void backupCliCreatesRequestedOutputFile() throws Exception {
    Path tempDir = Files.createTempDirectory("lifttrax-backup-cli");
    Path dbPath = tempDir.resolve("lifts.db");
    Path backupPath = tempDir.resolve("manual-backup.db");
    try (SqliteDb db = new SqliteDb(dbPath.toString())) {
      db.addLift("Press", LiftRegion.UPPER, LiftType.OVERHEAD_PRESS, List.of(), "");
    }

    String output =
        captureOutput(
            () ->
                BackupDatabaseCli.main(
                    new String[] {dbPath.toString(), "--output", backupPath.toString()}));

    assertTrue(Files.isRegularFile(backupPath));
    assertTrue(output.contains("Backup created:"));
    assertTrue(output.contains("Schema version:"));
  }

  @Test
  void restoreCliRestoresIntoRequestedDatabase() throws Exception {
    Path tempDir = Files.createTempDirectory("lifttrax-restore-cli");
    Path sourcePath = tempDir.resolve("source.db");
    Path targetPath = tempDir.resolve("target.db");
    try (SqliteDb db = new SqliteDb(sourcePath.toString())) {
      db.addLift("Deadlift", LiftRegion.LOWER, LiftType.DEADLIFT, List.of(), "");
    }
    try (SqliteDb db = new SqliteDb(targetPath.toString())) {
      db.addLift("Bench", LiftRegion.UPPER, LiftType.BENCH_PRESS, List.of(), "");
    }

    String output =
        captureOutput(
            () ->
                RestoreDatabaseCli.main(
                    new String[] {
                      sourcePath.toString(), targetPath.toString(), "--confirm-overwrite"
                    }));

    assertTrue(output.contains("Restored database:"));
    assertTrue(output.contains("Previous database saved as:"));
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
