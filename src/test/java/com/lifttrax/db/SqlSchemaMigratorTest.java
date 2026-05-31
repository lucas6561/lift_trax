package com.lifttrax.db;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SqlSchemaMigratorTest {

  @Test
  void appliesMigrationsInDeterministicOrderToEmptyDatabase() throws Exception {
    Path dbPath = Files.createTempFile("lifttrax-migrations-empty", ".db");

    try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
      SqlSchemaMigrator.migrate(connection);
      assertEquals(SqlSchemaVersion.current(), SqlSchemaMigrator.activeVersion(connection));
    }

    assertEquals(List.of(10, 11), migrationVersions(dbPath));
  }

  @Test
  void doesNotReapplyMigrationsToCurrentDatabase() throws Exception {
    Path dbPath = Files.createTempFile("lifttrax-migrations-current", ".db");

    try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
      SqlSchemaMigrator.migrate(connection);
      assertEquals(SqlSchemaVersion.current(), SqlSchemaMigrator.activeVersion(connection));
    }
    try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
      connection
          .createStatement()
          .executeUpdate(
              "UPDATE schema_migrations SET applied_at = 'preserved' WHERE version = 11");
    }

    try (SqliteDb db = new SqliteDb(dbPath.toString())) {
      assertEquals(SqlSchemaVersion.current(), db.schemaVersion());
    }

    assertEquals(List.of(10, 11), migrationVersions(dbPath));
    assertEquals("preserved", appliedAt(dbPath, 11));
  }

  private static List<Integer> migrationVersions(Path dbPath) throws Exception {
    List<Integer> versions = new ArrayList<>();
    try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        ResultSet rs =
            connection
                .createStatement()
                .executeQuery("SELECT version FROM schema_migrations ORDER BY id")) {
      while (rs.next()) {
        versions.add(rs.getInt("version"));
      }
    }
    return versions;
  }

  private static String appliedAt(Path dbPath, int version) throws Exception {
    try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        var statement =
            connection.prepareStatement(
                "SELECT applied_at FROM schema_migrations WHERE version = ?")) {
      statement.setInt(1, version);
      try (ResultSet rs = statement.executeQuery()) {
        return rs.next() ? rs.getString("applied_at") : null;
      }
    }
  }
}
