package com.lifttrax.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/** Applies SQLite migrations for test and legacy-import fixtures. */
final class SqlSchemaMigrator {
  private static final String CREATE_MIGRATIONS_TABLE_SQL =
      """
      CREATE TABLE IF NOT EXISTS schema_migrations (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          version INTEGER NOT NULL UNIQUE,
          name TEXT NOT NULL UNIQUE,
          applied_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
      )
      """;

  private SqlSchemaMigrator() {}

  static void migrate(Connection connection) throws Exception {
    List<SqlSchemaVersion.Migration> migrations = SqlSchemaVersion.migrations();
    ensureMigrationsTable(connection);
    int activeVersion = activeVersion(connection);
    int expectedVersion = SqlSchemaVersion.current();
    if (activeVersion > expectedVersion) {
      throw new IllegalStateException(
          "Database schema version "
              + activeVersion
              + " is newer than supported version "
              + expectedVersion);
    }

    for (SqlSchemaVersion.Migration migration : migrations) {
      if (migration.version() <= activeVersion) {
        recordMigration(connection, migration);
      } else if (isApplied(connection, migration.version())) {
        setActiveVersion(connection, migration.version());
        activeVersion = migration.version();
      } else {
        applyMigration(connection, migration);
        activeVersion = migration.version();
      }
    }

    if (activeVersion != expectedVersion) {
      throw new IllegalStateException(
          "Database schema version "
              + activeVersion
              + " does not match expected version "
              + expectedVersion);
    }
  }

  static int activeVersion(Connection connection) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement("PRAGMA user_version");
        ResultSet rs = statement.executeQuery()) {
      return rs.next() ? rs.getInt(1) : 0;
    }
  }

  private static void ensureMigrationsTable(Connection connection) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(CREATE_MIGRATIONS_TABLE_SQL)) {
      statement.executeUpdate();
    }
  }

  private static boolean isApplied(Connection connection, int version) throws SQLException {
    String sql = "SELECT 1 FROM schema_migrations WHERE version = ?";
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setInt(1, version);
      try (ResultSet rs = statement.executeQuery()) {
        return rs.next();
      }
    }
  }

  private static void applyMigration(Connection connection, SqlSchemaVersion.Migration migration)
      throws Exception {
    boolean autoCommit = connection.getAutoCommit();
    connection.setAutoCommit(false);
    try {
      executeSql(connection, migration.sql());
      recordMigration(connection, migration);
      setActiveVersion(connection, migration.version());
      connection.commit();
    } catch (SQLException e) {
      connection.rollback();
      throw new IllegalStateException("Failed to apply schema migration: " + migration.name(), e);
    } finally {
      connection.setAutoCommit(autoCommit);
    }
  }

  private static void executeSql(Connection connection, String sql) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      for (String chunk : sql.split(";")) {
        String command = chunk.trim();
        if (!command.isEmpty()) {
          statement.execute(command);
        }
      }
    }
  }

  private static void recordMigration(Connection connection, SqlSchemaVersion.Migration migration)
      throws SQLException {
    String sql = "INSERT OR IGNORE INTO schema_migrations (version, name) VALUES (?, ?)";
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setInt(1, migration.version());
      statement.setString(2, migration.name());
      statement.executeUpdate();
    }
  }

  private static void setActiveVersion(Connection connection, int version) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.execute("PRAGMA user_version = " + version);
    }
  }
}
