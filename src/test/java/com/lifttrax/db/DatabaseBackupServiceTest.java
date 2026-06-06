package com.lifttrax.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lifttrax.models.LiftRegion;
import com.lifttrax.models.LiftType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class DatabaseBackupServiceTest {

  @Test
  void createsPlainBackupInBackupsDirectory() throws Exception {
    Path dbPath = seededDatabase("lifttrax-backup-source", "Backup Bench");

    DatabaseBackupService.BackupResult result = DatabaseBackupService.createBackup(dbPath);

    assertTrue(Files.isRegularFile(result.backupPath()));
    assertEquals(dbPath.toAbsolutePath().normalize(), result.source());
    assertEquals(dbPath.getParent().resolve("backups"), result.backupPath().getParent());
    assertTrue(result.backupPath().getFileName().toString().startsWith("lifts-backup-"));
    assertTrue(
        DatabaseBackupService.validateLiftTraxDatabase(result.backupPath())
            .tableNames()
            .contains("lifts"));
  }

  @Test
  void restoreRefusesOverwriteUntilConfirmedThenKeepsPreRestoreCopy() throws Exception {
    Path targetPath = seededDatabase("lifttrax-restore-target", "Old Squat");
    Path sourcePath = seededDatabase("lifttrax-restore-source", "New Squat");
    Path backupPath = DatabaseBackupService.createBackup(sourcePath).backupPath();

    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () -> DatabaseBackupService.restoreBackup(backupPath, targetPath, false));

    assertTrue(error.getMessage().contains("--confirm-overwrite"));
    assertEquals("Old Squat", firstLiftName(targetPath));

    DatabaseBackupService.RestoreResult result =
        DatabaseBackupService.restoreBackup(backupPath, targetPath, true);

    assertEquals("New Squat", firstLiftName(targetPath));
    assertTrue(Files.isRegularFile(result.preRestoreBackupPath()));
    assertEquals("Old Squat", firstLiftName(result.preRestoreBackupPath()));
  }

  @Test
  void rejectsNonLiftTraxSqliteFiles() throws Exception {
    Path dbPath = Files.createTempFile("not-lifttrax", ".db");
    try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
      connection.createStatement().execute("CREATE TABLE unrelated (id INTEGER PRIMARY KEY)");
    }

    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () -> DatabaseBackupService.validateLiftTraxDatabase(dbPath));

    assertTrue(error.getMessage().contains("Missing table"));
    assertFalse(error.getMessage().contains("unrelated"));
  }

  private static Path seededDatabase(String prefix, String liftName) throws Exception {
    Path tempDir = Files.createTempDirectory(prefix);
    Path dbPath = tempDir.resolve("lifts.db");
    try (SqliteDb db = new SqliteDb(dbPath.toString())) {
      db.addLift(liftName, LiftRegion.UPPER, LiftType.BENCH_PRESS, List.of(), "");
    }
    return dbPath;
  }

  private static String firstLiftName(Path dbPath) throws Exception {
    try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        ResultSet rs =
            connection.createStatement().executeQuery("SELECT name FROM lifts ORDER BY name")) {
      if (rs.next()) {
        return rs.getString("name");
      }
    }
    return "";
  }
}
