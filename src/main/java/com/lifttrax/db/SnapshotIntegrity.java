package com.lifttrax.db;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

/** Cross-table validation shared by operator snapshot inspection and recovery. */
final class SnapshotIntegrity {
  private SnapshotIntegrity() {}

  static void validate(Connection connection) throws Exception {
    if ("SQLite".equals(connection.getMetaData().getDatabaseProductName())) {
      try (Statement statement = connection.createStatement();
          ResultSet rows = statement.executeQuery("PRAGMA integrity_check")) {
        if (!rows.next() || !"ok".equals(rows.getString(1))) {
          throw new IllegalStateException("Snapshot SQLite integrity check failed.");
        }
      }
    }
    reject(
        connection,
        "SELECT 1 FROM lifter_profiles p LEFT JOIN app_users u ON u.id = p.owner_user_id WHERE u.id IS NULL",
        "profile owner");
    reject(
        connection,
        "SELECT 1 FROM exercise_catalog_entries c LEFT JOIN lifter_profiles p ON p.id = c.lifter_profile_id WHERE p.id IS NULL OR p.owner_user_id <> c.owner_user_id",
        "catalog ownership");
    reject(
        connection,
        "SELECT 1 FROM executions e LEFT JOIN lifter_profiles p ON p.id = e.lifter_profile_id LEFT JOIN exercise_catalog_entries c ON c.id = e.catalog_entry_id WHERE p.id IS NULL OR (e.catalog_entry_id IS NOT NULL AND (c.id IS NULL OR c.lifter_profile_id <> e.lifter_profile_id OR c.owner_user_id <> p.owner_user_id))",
        "execution ownership");
    reject(
        connection,
        "SELECT 1 FROM execution_sets s LEFT JOIN executions e ON e.id = s.execution_id WHERE e.id IS NULL OR s.metric_kind NOT IN ('reps', 'reps-lr', 'reps-range', 'time', 'distance')",
        "execution sets");
    reject(
        connection,
        "SELECT execution_id FROM execution_sets GROUP BY execution_id HAVING MIN(set_index) <> 0 OR MAX(set_index) <> COUNT(*) - 1 OR COUNT(DISTINCT set_index) <> COUNT(*)",
        "ordered sets");
    for (String table : List.of("saved_workouts", "workout_submission_receipts")) {
      reject(
          connection,
          "SELECT 1 FROM "
              + table
              + " w LEFT JOIN lifter_profiles p ON p.id = w.lifter_profile_id WHERE p.id IS NULL",
          table + " ownership");
    }
    reject(
        connection,
        "SELECT 1 FROM local_imports i LEFT JOIN lifter_profiles p ON p.id = i.target_lifter_profile_id WHERE p.id IS NULL OR p.owner_user_id <> i.target_app_user_id",
        "import ownership");
    reject(
        connection,
        "SELECT 1 FROM local_import_records r LEFT JOIN local_imports i ON i.id = r.import_id WHERE i.id IS NULL OR i.target_lifter_profile_id <> r.target_lifter_profile_id",
        "import record ownership");
    reject(
        connection,
        "SELECT 1 FROM exercise_catalog_entries WHERE origin NOT IN ('unknown','user_created','starter','shared_copy','file_import') OR ((source_namespace IS NULL) <> (source_key IS NULL)) OR (source_namespace IS NULL AND source_revision IS NOT NULL) OR TRIM(source_namespace) = '' OR TRIM(source_key) = ''",
        "catalog provenance");
    reject(
        connection,
        "SELECT lifter_profile_id, source_namespace, source_key FROM exercise_catalog_entries WHERE source_namespace IS NOT NULL GROUP BY lifter_profile_id, source_namespace, source_key HAVING COUNT(*) > 1",
        "duplicate provenance");
    try (Statement statement = connection.createStatement();
        ResultSet rows =
            statement.executeQuery(
                "SELECT name, region, main_lift, muscles, notes FROM exercise_catalog_entries")) {
      while (rows.next()) {
        validateLift(rows);
      }
    }
    try (Statement statement = connection.createStatement();
        ResultSet rows =
            statement.executeQuery(
                "SELECT performed_exercise_name AS name, performed_region AS region, performed_main_lift AS main_lift, performed_muscles AS muscles, performed_catalog_notes AS notes, performed_catalog_id FROM executions")) {
      while (rows.next()) {
        if (rows.getString("performed_catalog_id") == null
            || rows.getString("performed_catalog_id").isBlank()) {
          throw new IllegalStateException("Missing performed exercise identity.");
        }
        validateLift(rows);
      }
    }
    try (Statement statement = connection.createStatement();
        ResultSet rows =
            statement.executeQuery(
                "SELECT version, name FROM lifttrax_schema_migrations ORDER BY version")) {
      List<String> migrationNames = PostgresSchemaMigrator.migrationNames();
      int expected = 1;
      while (rows.next()) {
        if (rows.getInt(1) != expected
            || expected > migrationNames.size()
            || !migrationNames.get(expected - 1).equals(rows.getString(2))) {
          throw new IllegalStateException("Incomplete migration history.");
        }
        expected++;
      }
      if (expected - 1 != PostgresSchemaMigrator.currentVersion()) {
        throw new IllegalStateException("Unsupported migration history.");
      }
    }
    try (Statement statement = connection.createStatement();
        ResultSet rows = statement.executeQuery("SELECT workout_json FROM saved_workouts")) {
      while (rows.next()) {
        var document =
            new com.fasterxml.jackson.databind.ObjectMapper().readTree(rows.getString(1));
        if (document == null
            || !document.isObject()
            || !document.path("schemaVersion").isIntegralNumber()) {
          throw new IllegalStateException("Invalid saved workout document.");
        }
      }
    }
  }

  private static void validateLift(ResultSet rows) throws Exception {
    if (rows.getString("name") == null
        || rows.getString("name").isBlank()
        || rows.getString("muscles") == null
        || rows.getString("notes") == null) {
      throw new IllegalStateException("Incomplete exercise snapshot or catalog definition.");
    }
    HostedPostgresTrainingDataStore.mapLift(rows);
  }

  private static void reject(Connection connection, String sql, String reason) throws Exception {
    try (Statement statement = connection.createStatement();
        ResultSet rows = statement.executeQuery(sql)) {
      if (rows.next()) {
        throw new IllegalStateException("Snapshot integrity failed: " + reason);
      }
    }
  }
}
