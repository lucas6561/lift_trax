package com.lifttrax.db;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.DriverManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

class PostgresSnapshotMigrationTest {
  @Test
  @EnabledIfEnvironmentVariable(named = "LIFTTRAX_TEST_POSTGRES_ADMIN_URL", matches = ".+")
  void realPostgresBackfillVerifiesCountsAndOwnershipAndRollsBackMalformedRows() throws Exception {
    String admin = System.getenv("LIFTTRAX_TEST_POSTGRES_ADMIN_URL");
    assertTrue(admin.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/postgres"));
    String database = "migration_" + UUID.randomUUID().toString().replace("-", "");
    try (var cluster = DriverManager.getConnection(admin, "postgres", "");
        var adminSql = cluster.createStatement()) {
      adminSql.execute("CREATE DATABASE " + database);
      try (var connection =
              DriverManager.getConnection(
                  admin.replace("/postgres", "/" + database), "postgres", "");
          var statement = connection.createStatement()) {
        CatalogLifecycleTest.legacySchema(connection);
        statement.executeUpdate(
            "INSERT INTO app_users(id,auth_user_id) VALUES ('owner','owner'),('other','other')");
        statement.executeUpdate(
            "INSERT INTO lifter_profiles(id,owner_user_id,display_name) VALUES ('profile','owner','Owner'),('other-profile','other','Other')");
        statement.executeUpdate(
            "INSERT INTO exercise_catalog_entries(id,owner_user_id,lifter_profile_id,name,region,main_lift,muscles,notes,enabled) VALUES ('lift','owner','profile','Bench','UPPER','','CHEST','before',FALSE)");
        statement.executeUpdate(
            "INSERT INTO executions(id,lifter_profile_id,catalog_entry_id,performed_on) VALUES ('one','profile','lift',DATE '2026-10-26'),('two','other-profile','lift',DATE '2026-10-27')");
        assertThrows(IllegalStateException.class, () -> PostgresSchemaMigrator.migrate(connection));
        try (var rows =
            statement.executeQuery(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name='executions' AND column_name='performed_exercise_name'")) {
          assertTrue(rows.next());
          assertEquals(0, rows.getInt(1));
        }
        statement.executeUpdate("UPDATE executions SET lifter_profile_id='profile' WHERE id='two'");
        statement.executeUpdate("UPDATE exercise_catalog_entries SET muscles='INVALID'");
        assertThrows(IllegalStateException.class, () -> PostgresSchemaMigrator.migrate(connection));
        statement.executeUpdate("UPDATE exercise_catalog_entries SET muscles='CHEST'");
        PostgresSchemaMigrator.migrate(connection);
        try (var rows =
            statement.executeQuery(
                "SELECT COUNT(*) FROM executions WHERE performed_exercise_name='Bench' AND performed_region='UPPER' AND performed_main_lift IS NULL AND performed_muscles='CHEST' AND performed_catalog_notes='before'")) {
          assertTrue(rows.next());
          assertEquals(2, rows.getInt(1));
        }
        statement.executeUpdate(
            "UPDATE exercise_catalog_entries SET name='Renamed', region='LOWER'");
        try (var rows =
            statement.executeQuery(
                "SELECT COUNT(*) FROM executions WHERE performed_exercise_name='Bench' AND performed_region='UPPER'")) {
          assertTrue(rows.next());
          assertEquals(2, rows.getInt(1));
        }
        SnapshotIntegrity.validate(connection);
      } finally {
        adminSql.execute("DROP DATABASE " + database + " WITH (FORCE)");
      }
    }
  }
}
