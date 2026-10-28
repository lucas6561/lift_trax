package com.lifttrax.db;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Applies the ordered LiftTrax-owned Postgres application migrations. */
final class PostgresSchemaMigrator {
  static final String MIGRATIONS_TABLE = "lifttrax_schema_migrations";
  private static final String MIGRATION_ROOT = "postgres/migrations/";
  private static final String POSTGRES_ONLY_DIRECTIVE = "-- lifttrax:postgres-only";

  private PostgresSchemaMigrator() {}

  static void migrate(Connection connection) throws Exception {
    List<Migration> migrations = migrations();
    ensureMigrationsTable(connection);
    for (Migration migration : migrations) {
      if (!isApplied(connection, migration.version())) {
        applyMigration(connection, migration);
      }
    }
  }

  static int currentVersion() {
    try {
      List<Migration> migrations = migrations();
      return migrations.isEmpty() ? 0 : migrations.get(migrations.size() - 1).version();
    } catch (IOException e) {
      throw new IllegalStateException("Failed to read Postgres migration index", e);
    }
  }

  static List<String> migrationNames() throws IOException {
    return migrations().stream().map(Migration::name).toList();
  }

  private static List<Migration> migrations() throws IOException {
    String index = resourceText(MIGRATION_ROOT + "index.txt");
    List<Migration> migrations = new ArrayList<>();
    for (String line : index.lines().toList()) {
      String name = line.trim();
      if (name.isEmpty() || name.startsWith("#")) {
        continue;
      }
      int separator = name.indexOf("__");
      if (separator <= 0) {
        throw new IllegalStateException("Invalid Postgres migration name: " + name);
      }
      int version = Integer.parseInt(name.substring(0, separator));
      migrations.add(new Migration(version, name, resourceText(MIGRATION_ROOT + name)));
    }
    return migrations;
  }

  private static void ensureMigrationsTable(Connection connection) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.execute(
          """
              CREATE TABLE IF NOT EXISTS lifttrax_schema_migrations (
                  version INTEGER PRIMARY KEY,
                  name TEXT NOT NULL UNIQUE,
                  applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
              )
              """);
    }
  }

  private static boolean isApplied(Connection connection, int version) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement("SELECT 1 FROM " + MIGRATIONS_TABLE + " WHERE version = ?")) {
      statement.setInt(1, version);
      try (ResultSet resultSet = statement.executeQuery()) {
        return resultSet.next();
      }
    }
  }

  private static void applyMigration(Connection connection, Migration migration) throws Exception {
    boolean autoCommit = connection.getAutoCommit();
    connection.setAutoCommit(false);
    try {
      long legacyCount = migration.version() == 11 ? validateLegacyExerciseRows(connection) : -1;
      String databaseProductName = connection.getMetaData().getDatabaseProductName();
      if (shouldExecuteMigration(databaseProductName, migration.sql())) {
        executeSql(connection, migration.sql());
      }
      if (legacyCount >= 0) {
        try (Statement verify = connection.createStatement();
            ResultSet rows =
                verify.executeQuery(
                    "SELECT COUNT(*) FROM executions WHERE performed_catalog_id IS NOT NULL AND performed_exercise_name IS NOT NULL AND performed_region IS NOT NULL AND performed_muscles IS NOT NULL AND performed_catalog_notes IS NOT NULL")) {
          if (!rows.next() || rows.getLong(1) != legacyCount) {
            throw new IllegalStateException(
                "Snapshot backfill count does not match legacy execution count.");
          }
        }
      }
      try (PreparedStatement statement =
          connection.prepareStatement(
              "INSERT INTO " + MIGRATIONS_TABLE + " (version, name) VALUES (?, ?)")) {
        statement.setInt(1, migration.version());
        statement.setString(2, migration.name());
        statement.executeUpdate();
      }
      connection.commit();
    } catch (Exception e) {
      connection.rollback();
      throw new IllegalStateException("Failed to apply Postgres migration " + migration.name(), e);
    } finally {
      connection.setAutoCommit(autoCommit);
    }
  }

  static boolean shouldExecuteMigration(String databaseProductName, String sql) {
    return !sql.startsWith(POSTGRES_ONLY_DIRECTIVE)
        || "PostgreSQL".equalsIgnoreCase(databaseProductName);
  }

  private static long validateLegacyExerciseRows(Connection connection) throws Exception {
    long count = 0;
    try (Statement statement = connection.createStatement();
        ResultSet rows =
            statement.executeQuery(
                """
            SELECT c.name, c.region, c.main_lift, c.muscles, c.notes,
              c.lifter_profile_id AS catalog_profile, e.lifter_profile_id AS execution_profile,
              c.owner_user_id, p.owner_user_id AS profile_owner
            FROM executions e
            LEFT JOIN exercise_catalog_entries c ON c.id = e.catalog_entry_id
            LEFT JOIN lifter_profiles p ON p.id = e.lifter_profile_id
            """)) {
      while (rows.next()) {
        if (rows.getString("name") == null
            || rows.getString("name").isBlank()
            || !java.util.Objects.equals(
                rows.getString("catalog_profile"), rows.getString("execution_profile"))
            || !java.util.Objects.equals(
                rows.getString("owner_user_id"), rows.getString("profile_owner"))
            || rows.getString("muscles") == null
            || rows.getString("notes") == null) {
          throw new IllegalStateException(
              "Snapshot backfill requires an ownership-valid catalog row for every execution. Repair legacy data before retrying.");
        }
        HostedPostgresTrainingDataStore.mapLift(rows);
        count++;
      }
    }
    return count;
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

  private static String resourceText(String name) throws IOException {
    try (InputStream input =
        Thread.currentThread().getContextClassLoader().getResourceAsStream(name)) {
      if (input == null) {
        throw new IllegalStateException("Missing Postgres migration resource: " + name);
      }
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private record Migration(int version, String name, String sql) {}
}
