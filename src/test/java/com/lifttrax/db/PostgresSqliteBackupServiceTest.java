package com.lifttrax.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lifttrax.models.ExecutionSet;
import com.lifttrax.models.LiftExecution;
import com.lifttrax.models.LiftRegion;
import com.lifttrax.models.LiftType;
import com.lifttrax.models.SetMetric;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class PostgresSqliteBackupServiceTest {

  @Test
  void snapshotIncludesAllUsersOwnershipEmptyTablesAndRoundTripMetadata() throws Exception {
    HostedPostgresConfig config = config();
    HostedPostgresTrainingDataStoreProvider provider =
        new HostedPostgresTrainingDataStoreProvider(config);
    TrainingDataStore first = provider.forUser("owner-a");
    TrainingDataStore second = provider.forUser("owner-b");
    provider.updateUsername("owner-a", "backup-user");
    first.addLift("Bench", LiftRegion.UPPER, LiftType.BENCH_PRESS, List.of(), "");
    second.addLift("Squat", LiftRegion.LOWER, LiftType.SQUAT, List.of(), "");
    first.addLiftExecution(
        "Bench",
        new LiftExecution(
            null,
            LocalDate.of(2026, 7, 18),
            List.of(new ExecutionSet(new SetMetric.Reps(5), "225 lb", 8.0f)),
            false,
            false,
            "snapshot"));
    Path destination = Files.createTempDirectory("postgres-sqlite-backup").resolve("backup.db");

    PostgresSqliteBackupService.BackupResult result =
        PostgresSqliteBackupService.createBackup(config, destination, false);

    assertTrue(Files.isRegularFile(result.backupPath()));
    assertTrue(result.backupPath().getFileName().toString().matches("backup-\\d{8}-\\d{6}Z\\.db"));
    assertEquals(2L, result.validation().rowCounts().get("app_users"));
    assertEquals(2L, result.validation().rowCounts().get("lifter_profiles"));
    assertEquals(2L, result.validation().rowCounts().get("exercise_catalog_entries"));
    assertEquals(1L, result.validation().rowCounts().get("executions"));
    assertEquals(0L, result.validation().rowCounts().get("local_imports"));
    assertEquals(result.validation(), PostgresSqliteBackupService.validate(result.backupPath()));
    try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + result.backupPath());
        ResultSet resultSet =
            connection
                .createStatement()
                .executeQuery(
                    "SELECT COUNT(DISTINCT owner_user_id) FROM exercise_catalog_entries")) {
      assertTrue(resultSet.next());
      assertEquals(2, resultSet.getInt(1));
    }
    try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + result.backupPath());
        ResultSet resultSet =
            connection
                .createStatement()
                .executeQuery("SELECT username FROM app_users WHERE auth_user_id = 'owner-a'")) {
      assertTrue(resultSet.next());
      assertEquals("backup-user", resultSet.getString(1));
    }
  }

  @Test
  void snapshotIsConsistentAndOverwriteRequiresConfirmation() throws Exception {
    HostedPostgresConfig config = config();
    HostedPostgresTrainingDataStoreProvider provider =
        new HostedPostgresTrainingDataStoreProvider(config);
    provider.forUser("before-snapshot");
    Path destination = Files.createTempDirectory("postgres-snapshot-consistency").resolve("all.db");
    AtomicBoolean inserted = new AtomicBoolean();
    Clock clock = Clock.fixed(Instant.parse("2026-07-18T14:35:22Z"), ZoneOffset.UTC);

    PostgresSqliteBackupService.BackupResult result =
        PostgresSqliteBackupService.createBackup(
            config,
            destination,
            false,
            tableName -> {
              if ("lifter_profiles".equals(tableName) && inserted.compareAndSet(false, true)) {
                provider.forUser("after-snapshot-started");
              }
            },
            clock);

    assertEquals(1L, result.validation().rowCounts().get("app_users"));
    assertEquals("all-20260718-143522Z.db", result.backupPath().getFileName().toString());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            PostgresSqliteBackupService.createBackup(
                config,
                destination,
                false,
                PostgresSqliteBackupService.BackupObserver.NONE,
                clock));
    PostgresSqliteBackupService.BackupResult replacement =
        PostgresSqliteBackupService.createBackup(
            config, destination, true, PostgresSqliteBackupService.BackupObserver.NONE, clock);
    assertEquals(
        2L,
        PostgresSqliteBackupService.validate(replacement.backupPath())
            .rowCounts()
            .get("app_users"));
  }

  @Test
  void failedSnapshotRemovesTemporaryArtifactAndDoesNotPublishDestination() throws Exception {
    HostedPostgresConfig config = config();
    new HostedPostgresTrainingDataStoreProvider(config).forUser("failure-owner");
    Path directory = Files.createTempDirectory("postgres-snapshot-failure");
    Path destination = directory.resolve("backup.db");

    assertThrows(
        IllegalStateException.class,
        () ->
            PostgresSqliteBackupService.createBackup(
                config,
                destination,
                false,
                tableName -> {
                  if ("executions".equals(tableName)) {
                    throw new IllegalStateException("simulated copy failure");
                  }
                }));

    assertFalse(Files.exists(destination));
    try (var paths = Files.list(directory)) {
      assertTrue(paths.findAny().isEmpty());
    }
  }

  private static HostedPostgresConfig config() {
    String name = "lifttrax_" + java.util.UUID.randomUUID().toString().replace("-", "");
    return new HostedPostgresConfig(
        "jdbc:h2:mem:" + name + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "",
        "");
  }
}
