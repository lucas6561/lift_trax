package com.lifttrax.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.util.List;
import org.junit.jupiter.api.Test;

class PostgresSchemaMigratorTest {
  private static final List<String> APPLICATION_TABLES =
      List.of(
          "app_users",
          "lifter_profiles",
          "exercise_catalog_entries",
          "executions",
          "execution_sets",
          "local_imports",
          "local_import_records",
          "workout_submission_receipts",
          "lifttrax_schema_migrations");

  @Test
  void securityMigrationProtectsEveryApplicationTableAndFutureObjects() throws Exception {
    String sql = resourceText("postgres/migrations/0004__secure-public-tables.sql");

    for (String table : APPLICATION_TABLES) {
      assertTrue(sql.contains("ALTER TABLE public." + table + " ENABLE ROW LEVEL SECURITY;"));
      assertTrue(
          sql.contains(
              "REVOKE ALL PRIVILEGES ON TABLE public." + table + " FROM anon, authenticated;"));
    }
    assertTrue(
        sql.contains(
            "ALTER DEFAULT PRIVILEGES IN SCHEMA public\n"
                + "    REVOKE ALL PRIVILEGES ON TABLES FROM anon, authenticated;"));
    assertTrue(
        sql.contains(
            "ALTER DEFAULT PRIVILEGES IN SCHEMA public\n"
                + "    REVOKE ALL PRIVILEGES ON SEQUENCES FROM anon, authenticated;"));
  }

  @Test
  void postgresOnlySecurityMigrationRunsOnlyOnPostgres() throws Exception {
    String sql = resourceText("postgres/migrations/0004__secure-public-tables.sql");

    assertTrue(PostgresSchemaMigrator.shouldExecuteMigration("PostgreSQL", sql));
    assertFalse(PostgresSchemaMigrator.shouldExecuteMigration("H2", sql));
    assertTrue(PostgresSchemaMigrator.shouldExecuteMigration("H2", "CREATE TABLE example(id INT)"));
  }

  @Test
  void compatibilityDatabaseRecordsSkippedPostgresOnlyMigration() throws Exception {
    String name = "migration_" + java.util.UUID.randomUUID().toString().replace("-", "");
    try (var connection =
        DriverManager.getConnection(
            "jdbc:h2:mem:" + name + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE")) {
      PostgresSchemaMigrator.migrate(connection);

      try (var statement =
          connection.prepareStatement(
              "SELECT name FROM lifttrax_schema_migrations WHERE version = ?"); ) {
        statement.setInt(1, 4);
        try (var result = statement.executeQuery()) {
          assertTrue(result.next());
          assertEquals("0004__secure-public-tables.sql", result.getString("name"));
        }
      }
    }
  }

  private static String resourceText(String name) throws Exception {
    try (InputStream input =
        Thread.currentThread().getContextClassLoader().getResourceAsStream(name)) {
      assertTrue(input != null, "Missing resource " + name);
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
