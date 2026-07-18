package com.lifttrax.db;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.Set;

/** Validates legacy SQLite files before an explicit Postgres import. */
public final class DatabaseBackupService {
  private static final Set<String> REQUIRED_TABLES = Set.of("lifts", "lift_records");

  private DatabaseBackupService() {}

  public static ValidationResult validateLiftTraxDatabase(Path dbPath) throws SQLException {
    Path database = dbPath.toAbsolutePath().normalize();
    if (!Files.isRegularFile(database)) {
      throw new IllegalArgumentException("Database file not found: " + database);
    }

    try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database)) {
      Set<String> tables = tableNames(connection);
      Set<String> missing = new LinkedHashSet<>(REQUIRED_TABLES);
      missing.removeAll(tables);
      if (!missing.isEmpty()) {
        throw new IllegalArgumentException(
            "Not a LiftTrax database. Missing table(s): " + String.join(", ", missing));
      }

      int schemaVersion = activeVersion(connection);
      int currentVersion = SqlSchemaVersion.current();
      if (schemaVersion > currentVersion) {
        throw new IllegalArgumentException(
            "Database schema version "
                + schemaVersion
                + " is newer than supported version "
                + currentVersion);
      }
      return new ValidationResult(database, schemaVersion, tables);
    }
  }

  private static Set<String> tableNames(Connection connection) throws SQLException {
    Set<String> names = new LinkedHashSet<>();
    try (PreparedStatement statement =
            connection.prepareStatement("SELECT name FROM sqlite_master WHERE type = 'table'");
        ResultSet resultSet = statement.executeQuery()) {
      while (resultSet.next()) {
        names.add(resultSet.getString("name"));
      }
    }
    return names;
  }

  private static int activeVersion(Connection connection) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement("PRAGMA user_version");
        ResultSet resultSet = statement.executeQuery()) {
      return resultSet.next() ? resultSet.getInt(1) : 0;
    }
  }

  public record ValidationResult(Path dbPath, int schemaVersion, Set<String> tableNames) {}
}
