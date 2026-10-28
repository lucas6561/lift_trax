package com.lifttrax.db;

import static org.junit.jupiter.api.Assertions.*;

import com.lifttrax.catalog.CatalogImportPlan;
import com.lifttrax.catalog.ExerciseCatalogFile;
import com.lifttrax.models.*;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CatalogLifecycleTest {
  @Test
  void aDatabaseFailureRollsBackEveryCatalogChangeInTheConfirmedFile() throws Exception {
    var config = config();
    var store = new HostedPostgresTrainingDataStoreProvider(config).forUser("owner");
    try (var connection = DriverManager.getConnection(config.jdbcUrl());
        var statement = connection.createStatement()) {
      statement.execute(
          "ALTER TABLE exercise_catalog_entries ADD CONSTRAINT injected_write_failure CHECK (name <> 'Fail during write')");
    }
    var first =
        new CatalogEntry(
            new Lift("Valid first row", LiftRegion.UPPER, null, List.of(), "valid"),
            true,
            CatalogProvenance.unknown());
    var second =
        new CatalogEntry(
            new Lift("Fail during write", LiftRegion.UPPER, null, List.of(), "valid"),
            true,
            CatalogProvenance.unknown());
    var entries = List.of(first, second);
    var preview = CatalogImportPlan.preview(entries, store.listCatalogEntries());
    assertThrows(Exception.class, () -> store.importCatalog(entries, preview.fingerprint()));
    assertTrue(store.listCatalogEntries().isEmpty());
    assertTrue(store.performedHistory().isEmpty());
  }

  static HostedPostgresConfig config() {
    return new HostedPostgresConfig(
        "jdbc:h2:mem:catalog_"
            + UUID.randomUUID()
            + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "",
        "");
  }

  static LiftExecution execution(String notes) {
    return new LiftExecution(
        null,
        LocalDate.of(2026, 10, 27),
        List.of(
            new ExecutionSet(new SetMetric.Reps(5), "100 lb", 8f),
            new ExecutionSet(new SetMetric.TimeSecs(30), "none", null)),
        false,
        false,
        notes);
  }

  static String document(String name, String key, String notes) {
    return """
        {"schemaVersion":1,"exercises":[{"name":"%s","enabled":true,"region":"UPPER","main":"BENCH_PRESS","muscles":["CHEST"],"notes":"%s","source":{"namespace":"coach:test","key":"%s","revision":"1"}}]}
        """
        .formatted(name, notes, key);
  }

  @Test
  void snapshotsSurviveRenameClassificationDisableDeleteAndNameReuse() throws Exception {
    var config = config();
    var provider = new HostedPostgresTrainingDataStoreProvider(config);
    var owner = provider.forUser("owner");
    var other = provider.forUser("other");
    owner.addLift(
        "Bench", LiftRegion.UPPER, LiftType.BENCH_PRESS, List.of(Muscle.CHEST), "original");
    other.addLift("Bench", LiftRegion.LOWER, LiftType.SQUAT, List.of(), "private");
    owner.addLiftExecution("Bench", execution("kept"));
    var saved = owner.getExecutions("Bench").get(0);
    owner.updateLift(
        "Bench", "Paused Bench", LiftRegion.LOWER, LiftType.ACCESSORY, List.of(Muscle.QUAD), "new");
    var snapshot = owner.performedHistory().get(0);
    assertEquals(
        new Lift(
            "Bench", LiftRegion.UPPER, LiftType.BENCH_PRESS, List.of(Muscle.CHEST), "original"),
        snapshot.lift());
    assertEquals("Bench", owner.liftHistory("Paused Bench").get(0).lift().name());
    assertEquals(saved, owner.getExecutions("Paused Bench").get(0));
    assertEquals(saved, owner.getLastExecution("Paused Bench", false, false));
    assertEquals("100 lb", owner.liftStats("Paused Bench").bestByReps().get(5));
    owner.setLiftEnabled("Paused Bench", false);
    assertThrows(
        IllegalArgumentException.class,
        () -> owner.addLiftExecution("Paused Bench", execution("blocked")));
    owner.updateLiftExecution(saved.id(), execution("corrected"));
    assertEquals(snapshot.lift(), owner.performedHistory().get(0).lift());
    assertEquals(
        "Bench", owner.dashboardSnapshot(saved.date()).recentExecutions().get(0).lift().name());
    assertEquals(1, owner.dashboardSnapshot(saved.date()).todayCount());
    assertThrows(IllegalArgumentException.class, () -> other.deleteLift("Paused Bench"));
    assertThrows(IllegalArgumentException.class, () -> other.setLiftEnabled("Paused Bench", true));
    assertThrows(
        IllegalArgumentException.class,
        () -> other.updateLift("Paused Bench", "stolen", LiftRegion.UPPER, null, List.of(), ""));
    assertNull(other.getExecution("Bench", saved.id()));
    owner.deleteLift("Paused Bench");
    assertTrue(owner.listLifts().isEmpty());
    assertEquals("corrected", owner.getExecution("Bench", saved.id()).notes());
    assertEquals(2, owner.performedHistory().get(0).execution().sets().size());
    assertEquals("100 lb", owner.liftStats("Bench").bestByReps().get(5));
    assertEquals(1, owner.dashboardSnapshot(saved.date()).recentExecutions().size());
    owner.addLift("Bench", LiftRegion.UPPER, LiftType.BENCH_PRESS, List.of(), "new identity");
    assertTrue(owner.getExecutions("Bench").isEmpty());
    assertTrue(owner.liftHistory("Bench").isEmpty());
    assertEquals("original", owner.historyForExecution(saved.id()).get(0).lift().notes());
    assertTrue(other.historyForExecution(saved.id()).isEmpty());
    assertTrue(owner.liftStats("Bench").bestByReps().isEmpty());
    assertEquals(1, owner.performedHistory().size());
    assertTrue(other.performedHistory().isEmpty());
    try (var connection = DriverManager.getConnection(config.jdbcUrl())) {
      SnapshotIntegrity.validate(connection);
    }
  }

  @Test
  void executionAndOrderedSetsRollBackTogetherOnCreateAndEditFailure() throws Exception {
    var store = new HostedPostgresTrainingDataStoreProvider(config()).forUser("owner");
    store.addLift("Bench", LiftRegion.UPPER, null, List.of(), "");
    var bad =
        new LiftExecution(
            null,
            execution("").date(),
            Arrays.asList(execution("").sets().get(0), null),
            false,
            false,
            "bad");
    assertThrows(Exception.class, () -> store.addLiftExecution("Bench", bad));
    assertTrue(store.performedHistory().isEmpty());
    store.addLiftExecution("Bench", execution("original"));
    var original = store.getExecutions("Bench").get(0);
    assertThrows(Exception.class, () -> store.updateLiftExecution(original.id(), bad));
    assertEquals(original, store.getExecution("Bench", original.id()));
    assertThrows(
        IllegalArgumentException.class, () -> store.addLiftExecution("missing", execution("")));
  }

  @Test
  void sourceIdentityUpdatesRenamedRowsAndRequiresAFreshConflictFreePreview() throws Exception {
    var config = config();
    var provider = new HostedPostgresTrainingDataStoreProvider(config);
    var store = provider.forUser("owner");
    var entries = ExerciseCatalogFile.parse(document("Bench", "bench", "first"));
    var plan = CatalogImportPlan.preview(entries, store.listCatalogEntries());
    assertEquals(1, store.importCatalog(entries, plan.fingerprint()));
    store.addLiftExecution("Bench", execution("kept"));
    store.updateLift("Bench", "My bench", LiftRegion.UPPER, null, List.of(), "local");
    plan = CatalogImportPlan.preview(entries, store.listCatalogEntries());
    assertEquals(CatalogImportPlan.Action.CHANGED, plan.rows().get(0).action());
    assertEquals("My bench", plan.rows().get(0).existingName());
    assertEquals(1, store.importCatalog(entries, plan.fingerprint()));
    plan = CatalogImportPlan.preview(entries, store.listCatalogEntries());
    assertEquals(CatalogImportPlan.Action.UNCHANGED, plan.rows().get(0).action());
    assertEquals(0, store.importCatalog(entries, plan.fingerprint()));
    assertEquals("Bench", store.performedHistory().get(0).lift().name());
    var stale = plan.fingerprint();
    store.setLiftEnabled("Bench", false);
    assertThrows(IllegalArgumentException.class, () -> store.importCatalog(entries, stale));
    var conflicting = ExerciseCatalogFile.parse(document("Bench", "different", "wrong identity"));
    var conflict = CatalogImportPlan.preview(conflicting, store.listCatalogEntries());
    assertTrue(conflict.hasConflicts());
    assertThrows(
        IllegalArgumentException.class,
        () -> store.importCatalog(conflicting, conflict.fingerprint()));
    assertEquals("first", store.getLift("Bench").notes());
    var other = provider.forUser("other");
    assertEquals(
        1,
        other.importCatalog(
            entries, CatalogImportPlan.preview(entries, other.listCatalogEntries()).fingerprint()));
    assertEquals(1, other.listLifts().size());
    assertTrue(other.performedHistory().isEmpty());
    try (var connection = DriverManager.getConnection(config.jdbcUrl());
        var statement = connection.createStatement()) {
      assertThrows(
          Exception.class,
          () ->
              statement.executeUpdate(
                  "UPDATE exercise_catalog_entries SET source_key = NULL WHERE name = 'Bench'"));
    }
  }

  @Test
  void duplicateRenameIsRefusedAndDoesNotMergeHistory() throws Exception {
    var store = new HostedPostgresTrainingDataStoreProvider(config()).forUser("owner");
    store.addLift("Bench", LiftRegion.UPPER, null, List.of(), "");
    store.addLift("Other", LiftRegion.UPPER, null, List.of(), "");
    store.addLiftExecution("Bench", execution("original"));
    assertThrows(
        Exception.class,
        () -> store.updateLift("Bench", "Other", LiftRegion.UPPER, null, List.of(), ""));
    assertEquals(1, store.getExecutions("Bench").size());
    assertTrue(store.getExecutions("Other").isEmpty());
  }

  @Test
  void migrationBackfillsEveryRowAndRejectsMalformedOrCrossAccountLegacyMetadata()
      throws Exception {
    for (String malformed :
        List.of(
            "",
            "normalized-values",
            "bad-region",
            "bad-muscle",
            "wrong-owner",
            "wrong-profile",
            "blank-name")) {
      var config = config();
      try (Connection connection = DriverManager.getConnection(config.jdbcUrl())) {
        legacySchema(connection);
        try (var statement = connection.createStatement()) {
          statement.executeUpdate(
              "INSERT INTO app_users(id,auth_user_id) VALUES ('owner','owner'),('other','other')");
          statement.executeUpdate(
              "INSERT INTO lifter_profiles(id,owner_user_id,display_name) VALUES ('profile','owner','Owner'),('other-profile','other','Other')");
          statement.executeUpdate(
              "INSERT INTO exercise_catalog_entries(id,owner_user_id,lifter_profile_id,name,region,main_lift,muscles,notes,enabled) VALUES ('lift','owner','profile','Bench','UPPER',NULL,'CHEST','legacy',FALSE)");
          statement.executeUpdate(
              "INSERT INTO executions(id,lifter_profile_id,catalog_entry_id,performed_on) VALUES ('execution','profile','lift',DATE '2026-10-27')");
          switch (malformed) {
            case "normalized-values" ->
                statement.executeUpdate(
                    "UPDATE exercise_catalog_entries SET region='upper', main_lift='bench_press'");
            case "bad-region" ->
                statement.executeUpdate("UPDATE exercise_catalog_entries SET region='SIDEWAYS'");
            case "bad-muscle" ->
                statement.executeUpdate(
                    "UPDATE exercise_catalog_entries SET muscles='NOT_A_MUSCLE'");
            case "wrong-owner" ->
                statement.executeUpdate(
                    "UPDATE exercise_catalog_entries SET owner_user_id='other'");
            case "wrong-profile" ->
                statement.executeUpdate("UPDATE executions SET lifter_profile_id='other-profile'");
            case "blank-name" ->
                statement.executeUpdate("UPDATE exercise_catalog_entries SET name=' '");
            default -> {}
          }
        }
        if (malformed.isEmpty() || "normalized-values".equals(malformed)) {
          PostgresSchemaMigrator.migrate(connection);
          try (var rows = connection.createStatement().executeQuery("SELECT * FROM executions")) {
            assertTrue(rows.next());
            assertEquals("Bench", rows.getString("performed_exercise_name"));
            assertEquals("UPPER", rows.getString("performed_region"));
            assertEquals(
                "normalized-values".equals(malformed) ? "BENCH PRESS" : null,
                rows.getString("performed_main_lift"));
            assertEquals("CHEST", rows.getString("performed_muscles"));
            assertEquals("legacy", rows.getString("performed_catalog_notes"));
            assertEquals("lift", rows.getString("performed_catalog_id"));
            assertFalse(rows.next());
          }
          try (var rows =
              connection
                  .createStatement()
                  .executeQuery("SELECT origin, enabled FROM exercise_catalog_entries")) {
            rows.next();
            assertEquals("unknown", rows.getString(1));
            assertFalse(rows.getBoolean(2));
          }
          SnapshotIntegrity.validate(connection);
        } else {
          assertThrows(
              IllegalStateException.class, () -> PostgresSchemaMigrator.migrate(connection));
          try (var rows =
              connection
                  .createStatement()
                  .executeQuery(
                      "SELECT COUNT(*) FROM lifttrax_schema_migrations WHERE version=11")) {
            rows.next();
            assertEquals(0, rows.getInt(1));
          }
        }
      }
    }
  }

  static void legacySchema(Connection connection) throws Exception {
    try (var statement = connection.createStatement()) {
      statement.execute(
          "CREATE TABLE lifttrax_schema_migrations(version INTEGER PRIMARY KEY,name TEXT NOT NULL UNIQUE,applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
      for (String name :
          resource("index.txt")
              .lines()
              .filter(line -> !line.startsWith("#") && !line.isBlank())
              .toList()) {
        int version = Integer.parseInt(name.substring(0, 4));
        if (version > 10) {
          continue;
        }
        String sql = resource(name);
        if (PostgresSchemaMigrator.shouldExecuteMigration("H2", sql)) {
          for (String chunk : sql.split(";")) {
            if (!chunk.isBlank()) {
              statement.execute(chunk);
            }
          }
        }
        try (var insert =
            connection.prepareStatement(
                "INSERT INTO lifttrax_schema_migrations(version,name) VALUES (?,?)")) {
          insert.setInt(1, version);
          insert.setString(2, name);
          insert.executeUpdate();
        }
      }
    }
  }

  private static String resource(String name) throws Exception {
    try (var stream =
        CatalogLifecycleTest.class
            .getClassLoader()
            .getResourceAsStream("postgres/migrations/" + name)) {
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
