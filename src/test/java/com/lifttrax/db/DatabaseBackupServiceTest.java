package com.lifttrax.db;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;

class DatabaseBackupServiceTest {

  @Test
  void validatesLegacyLiftTraxSqliteFilesForExplicitImport() throws Exception {
    Path dbPath = Files.createTempFile("lifttrax-import-source", ".db");
    try (SqliteDb ignored = new SqliteDb(dbPath.toString())) {
      // Opening a test database applies the legacy SQLite schema.
    }

    DatabaseBackupService.ValidationResult result =
        DatabaseBackupService.validateLiftTraxDatabase(dbPath);

    assertTrue(result.tableNames().contains("lifts"));
    assertTrue(result.tableNames().contains("lift_records"));
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
}
