package com.lifttrax.cli;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifttrax.catalog.CatalogImportPlan;
import com.lifttrax.catalog.ExerciseCatalogFile;
import com.lifttrax.db.*;
import com.lifttrax.models.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

class CatalogRecoveryDrillTest {
  @Test
  void compatibleDatabaseDrillValidatesHistoryOwnershipProvenanceSetsAndExports() throws Exception {
    drill(h2(), h2(), "H2 compatibility");
  }

  @Test
  @EnabledIfEnvironmentVariable(named = "LIFTTRAX_TEST_POSTGRES_ADMIN_URL", matches = ".+")
  void realPostgresRecoveryDrill() throws Exception {
    String admin = System.getenv("LIFTTRAX_TEST_POSTGRES_ADMIN_URL");
    assertTrue(
        admin.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/postgres"),
        "Drill admin must be a disposable loopback cluster.");
    String sourceName = "drill_source_" + UUID.randomUUID().toString().replace("-", "");
    String targetName = "drill_target_" + UUID.randomUUID().toString().replace("-", "");
    try (var connection = DriverManager.getConnection(admin, "postgres", "");
        var statement = connection.createStatement()) {
      for (String role : List.of("anon", "authenticated")) {
        try (var rows =
            statement.executeQuery("SELECT 1 FROM pg_roles WHERE rolname='" + role + "'")) {
          boolean exists = rows.next();
          if (!exists) {
            try (var create = connection.createStatement()) {
              create.execute("CREATE ROLE " + role);
            }
          }
        }
      }
      statement.execute("CREATE DATABASE " + sourceName);
      statement.execute("CREATE DATABASE " + targetName);
      try {
        drill(
            new HostedPostgresConfig(admin.replace("/postgres", "/" + sourceName), "postgres", ""),
            new HostedPostgresConfig(admin.replace("/postgres", "/" + targetName), "postgres", ""),
            "PostgreSQL");
      } finally {
        statement.execute("DROP DATABASE " + sourceName + " WITH (FORCE)");
        statement.execute("DROP DATABASE " + targetName + " WITH (FORCE)");
      }
    }
  }

  private static void drill(HostedPostgresConfig source, HostedPostgresConfig target, String engine)
      throws Exception {
    DrillSource state = seedSource(source);
    prepareSourceHistory(state);
    RecoveryState recovery = recoverSnapshot(state, source, target);
    assertRecoveredState(state, recovery, target);
    writeRecoveryReport(engine, recovery);
  }

  private static DrillSource seedSource(HostedPostgresConfig source) throws Exception {
    var provider = new HostedPostgresTrainingDataStoreProvider(source);
    var accountA = provider.forUser("fixture-a");
    var accountB = provider.forUser("fixture-b");
    provider.updateUsername("fixture-a", "fixture_a");
    provider.updateUsername("fixture-b", "fixture_b");
    String document =
        """
        {"schemaVersion":1,"exercises":[{"name":"Bench","enabled":true,"region":"UPPER","main":"BENCH_PRESS","muscles":["CHEST"],"notes":"catalog A","source":{"namespace":"drill:catalog","key":"bench","revision":"1"}}]}
        """;
    var entries = ExerciseCatalogFile.parse(document);
    accountA.importCatalog(
        entries, CatalogImportPlan.preview(entries, accountA.listCatalogEntries()).fingerprint());
    accountB.addLift(
        "Bench", LiftRegion.LOWER, LiftType.ACCESSORY, List.of(Muscle.QUAD), "catalog B");
    List<ExecutionSet> sets =
        List.of(
            new ExecutionSet(new SetMetric.Reps(5), "100 lb", 8f),
            new ExecutionSet(new SetMetric.RepsLr(8, 7), "25 lb", null),
            new ExecutionSet(new SetMetric.TimeSecs(30), "none", null, true));
    var execution =
        new LiftExecution(null, LocalDate.of(2026, 10, 27), sets, false, false, "private A");
    accountA.addLiftExecution("Bench", execution);
    accountB.addLiftExecution(
        "Bench", new LiftExecution(null, execution.date(), sets, true, true, "private B"));
    return new DrillSource(provider, accountA, accountB, sets, execution);
  }

  private static void prepareSourceHistory(DrillSource state) throws Exception {
    Path legacy = Files.createTempFile("drill-legacy-import", ".db");
    try (SqliteDb old = new SqliteDb(legacy.toString())) {
      old.addLift(
          "Bench",
          LiftRegion.LOWER,
          LiftType.ACCESSORY,
          List.of(Muscle.QUAD),
          "source legacy metadata");
      old.addLiftExecution(
          "Bench",
          new LiftExecution(
              null,
              state.execution().date().minusDays(1),
              List.of(new ExecutionSet(new SetMetric.RepsRange(8, 12), "25 lb", null)),
              false,
              false,
              "legacy A"));
    }
    HostedLocalDatabaseImportService.importDatabase(legacy, state.accountA());
    assertEquals(LiftRegion.LOWER, state.accountA().performedHistory().get(0).lift().region());
    assertEquals(LiftRegion.UPPER, state.accountA().getLift("Bench").region());
    state
        .accountA()
        .updateLift(
            "Bench",
            "Renamed Bench",
            LiftRegion.LOWER,
            LiftType.ACCESSORY,
            List.of(Muscle.QUAD),
            "live A");
    state.accountB().deleteLift("Bench");
    state
        .accountA()
        .recordWorkoutSubmission("drill:block:0", new WorkoutSubmissionReceipt("drill", 1, 0, 0));
    String workout = Files.readString(Path.of("shared/workouts/examples/conjugate-wave-v2.json"));
    state.accountA().saveWorkout("Private workout A", workout);
    state.accountB().saveWorkout("Private workout B", workout);
  }

  private static RecoveryState recoverSnapshot(
      DrillSource state, HostedPostgresConfig source, HostedPostgresConfig target)
      throws Exception {
    Instant exportTime = Instant.now();
    String beforeA =
        AccountExportWriter.write(
            state.accountA(), state.provider().accountFor("fixture-a", ""), exportTime);
    String beforeB =
        AccountExportWriter.write(
            state.accountB(), state.provider().accountFor("fixture-b", ""), exportTime);
    Path folder = Files.createTempDirectory("lifttrax-recovery-drill");
    Path backup = createBackup(source, folder.resolve("snapshot.db"));
    Instant afterSnapshot = Instant.now();
    state
        .accountA()
        .addLiftExecution(
            "Renamed Bench",
            new LiftExecution(
                null, state.execution().date(), state.sets(), false, false, "after snapshot"));
    var result = PostgresSnapshotRecovery.restore(backup, target);
    var recovered = new HostedPostgresTrainingDataStoreProvider(target);
    return new RecoveryState(
        beforeA,
        beforeB,
        exportTime,
        backup,
        afterSnapshot,
        result,
        recovered,
        recovered.forUser("fixture-a"),
        recovered.forUser("fixture-b"));
  }

  private static void assertRecoveredState(
      DrillSource state, RecoveryState recovery, HostedPostgresConfig target) throws Exception {
    assertEquals(
        recovery.beforeA(),
        AccountExportWriter.write(
            recovery.restoredA(),
            recovery.provider().accountFor("fixture-a", ""),
            recovery.exportTime()));
    assertEquals(
        recovery.beforeB(),
        AccountExportWriter.write(
            recovery.restoredB(),
            recovery.provider().accountFor("fixture-b", ""),
            recovery.exportTime()));
    JsonNode exported = new ObjectMapper().readTree(recovery.beforeA());
    assertEquals(
        "Bench",
        exported.path("executions").get(0).path("performedExercise").path("name").asText());
    assertEquals(
        "drill:catalog",
        exported.path("catalog").get(0).path("provenance").path("sourceNamespace").asText());
    assertFalse(recovery.beforeA().contains("private B"));
    assertFalse(recovery.beforeB().contains("private A"));
    assertEquals(state.sets(), recovery.restoredB().performedHistory().get(0).execution().sets());
    assertTrue(recovery.restoredB().listLifts().isEmpty());
    assertRecoveredCounts(recovery);
    assertNotNull(recovery.restoredA().getWorkoutSubmission("drill:block:0"));
    int existingId = recovery.restoredA().performedHistory().get(0).execution().id();
    recovery.restoredA().addLiftExecution("Renamed Bench", state.execution());
    assertTrue(
        recovery.restoredA().getExecutions("Renamed Bench").stream()
            .anyMatch(execution -> execution.id() > existingId));
    assertThrows(
        IllegalArgumentException.class,
        () -> PostgresSnapshotRecovery.restore(recovery.backup(), target));
  }

  private static void assertRecoveredCounts(RecoveryState recovery) {
    assertEquals(2L, recovery.result().rowCounts().get("app_users"));
    assertEquals(3L, recovery.result().rowCounts().get("executions"));
    assertEquals(7L, recovery.result().rowCounts().get("execution_sets"));
    assertEquals(2L, recovery.result().rowCounts().get("saved_workouts"));
    assertEquals(1L, recovery.result().rowCounts().get("local_imports"));
    assertEquals(1L, recovery.result().rowCounts().get("local_import_records"));
  }

  private static void writeRecoveryReport(String engine, RecoveryState recovery) throws Exception {
    Path report = Path.of("build/reports/recovery-drill-" + engine.replace(' ', '-') + ".md");
    Files.createDirectories(report.getParent());
    Files.writeString(
        report,
        "# Catalog and history recovery drill\\n\\nEngine: "
            + engine
            + "\\n\\nRecovery point: "
            + recovery.result().recoveryPointUtc()
            + "\\n\\nRecovery time: "
            + recovery.result().recoveryTimeMillis()
            + " ms\\n\\nObserved post-snapshot window: "
            + java.time.Duration.between(
                    Instant.parse(recovery.result().recoveryPointUtc()), recovery.afterSnapshot())
                .toMillis()
            + " ms. One deliberately later execution was excluded as expected.\\n\\nRestored table counts: "
            + recovery.result().rowCounts()
            + "\\n\\nValidated two isolated account exports byte-for-byte, overlapping names, provenance, renamed and detached snapshots, ordered sets, submission receipts, saved workouts, and next execution identity. Manual steps: start disposable cluster and run test; provider auth/configuration and partial production restore remain outside this drill. No production data used.\\n");
  }

  private record DrillSource(
      HostedPostgresTrainingDataStoreProvider provider,
      TrainingDataStore accountA,
      TrainingDataStore accountB,
      List<ExecutionSet> sets,
      LiftExecution execution) {}

  private record RecoveryState(
      String beforeA,
      String beforeB,
      Instant exportTime,
      Path backup,
      Instant afterSnapshot,
      PostgresSnapshotRecovery.RecoveryResult result,
      HostedPostgresTrainingDataStoreProvider provider,
      TrainingDataStore restoredA,
      TrainingDataStore restoredB) {}

  @Test
  void invalidArtifactsFailBeforeTargetInitialization() throws Exception {
    Path folder = Files.createTempDirectory("invalid-recovery");
    var target = h2();
    assertThrows(
        IllegalArgumentException.class,
        () -> PostgresSnapshotRecovery.restore(folder.resolve("missing.db"), target));
    Path corrupt = folder.resolve("corrupt.db");
    Files.writeString(corrupt, "not a database");
    assertThrows(Exception.class, () -> PostgresSnapshotRecovery.restore(corrupt, target));
    var source = h2();
    new HostedPostgresTrainingDataStoreProvider(source).forUser("fixture");
    Path backup = createBackup(source, folder.resolve("valid.db"));
    for (String mutation :
        List.of(
            "UPDATE lifttrax_backup_metadata SET metadata_value='999' WHERE metadata_key='format_version'",
            "UPDATE lifttrax_backup_metadata SET metadata_value='999' WHERE metadata_key='postgres_schema_version'",
            "DELETE FROM lifttrax_backup_row_counts WHERE table_name='executions'",
            "DELETE FROM lifttrax_schema_migrations WHERE version=1",
            "UPDATE lifter_profiles SET owner_user_id='missing'")) {
      Path damaged = folder.resolve(UUID.randomUUID() + ".db");
      Files.copy(backup, damaged);
      try (var connection = DriverManager.getConnection("jdbc:sqlite:" + damaged);
          var statement = connection.createStatement()) {
        statement.executeUpdate(mutation);
      }
      assertThrows(Exception.class, () -> PostgresSnapshotRecovery.restore(damaged, target));
    }
    try (var connection = DriverManager.getConnection(target.jdbcUrl());
        var rows = connection.getMetaData().getTables(null, null, "app_users", null)) {
      assertFalse(rows.next());
    }
  }

  private static Path createBackup(HostedPostgresConfig source, Path destination) throws Exception {
    return PostgresSqliteBackupService.createBackup(source, destination, false).backupPath();
  }

  private static HostedPostgresConfig h2() {
    return new HostedPostgresConfig(
        "jdbc:h2:mem:drill_"
            + UUID.randomUUID()
            + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "",
        "");
  }
}
