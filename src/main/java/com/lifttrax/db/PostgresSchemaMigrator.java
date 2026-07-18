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
      executeSql(connection, migration.sql());
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
