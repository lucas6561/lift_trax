package com.lifttrax.db;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lifttrax.cli.CompareSnapshotsCli;
import com.lifttrax.models.LiftRegion;
import com.lifttrax.models.LiftType;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PostgresSnapshotComparisonTest {
  @TempDir Path directory;

  @Test
  void comparesAllRowsWhileIgnoringBackupCreationTimes() throws Exception {
    var config = config();
    try (var provider = new HostedPostgresTrainingDataStoreProvider(config)) {
      var owner = provider.forUser("existing-owner");
      owner.addLift("Bench", LiftRegion.UPPER, LiftType.BENCH_PRESS, List.of(), "private notes");
      provider.forUser("another-owner");
      Path first = snapshot(config, "first", Instant.parse("2026-10-28T10:00:00Z"));
      Path second = snapshot(config, "second", Instant.parse("2026-10-28T10:00:01Z"));
      assertDoesNotThrow(
          () -> CompareSnapshotsCli.main(new String[] {first.toString(), second.toString()}));
      try (var connection = DriverManager.getConnection("jdbc:sqlite:" + second);
          var statement = connection.createStatement()) {
        statement.executeUpdate(
            "UPDATE exercise_catalog_entries SET notes = 'changed private notes'");
      }
      var error =
          assertThrows(
              IllegalStateException.class,
              () -> PostgresSnapshotComparison.requireIdentical(first, second));
      assertTrue(error.getMessage().contains("exercise_catalog_entries"));
      assertTrue(!error.getMessage().contains("private notes"));
    }
  }

  @Test
  void rejectsChangedCountsAndMissingSnapshots() throws Exception {
    var config = config();
    try (var provider = new HostedPostgresTrainingDataStoreProvider(config)) {
      provider.forUser("owner");
      Path before = snapshot(config, "before", Instant.parse("2026-10-28T10:00:00Z"));
      provider.forUser("new-owner");
      Path after = snapshot(config, "after", Instant.parse("2026-10-28T10:00:01Z"));
      assertThrows(
          IllegalStateException.class,
          () -> PostgresSnapshotComparison.requireIdentical(before, after));
      assertThrows(
          IllegalArgumentException.class,
          () ->
              PostgresSnapshotComparison.requireIdentical(before, directory.resolve("missing.db")));
      assertThrows(IllegalArgumentException.class, () -> CompareSnapshotsCli.main(new String[] {}));
    }
  }

  @Test
  void detectsOwnershipAndIdentityChangesEvenWhenCountsAreEqual() throws Exception {
    var config = config();
    try (var provider = new HostedPostgresTrainingDataStoreProvider(config)) {
      provider.forUser("original-owner");
      Path before = snapshot(config, "before", Instant.parse("2026-10-28T10:00:00Z"));
      Path after = snapshot(config, "after", Instant.parse("2026-10-28T10:00:01Z"));
      try (var connection = DriverManager.getConnection("jdbc:sqlite:" + after);
          var statement = connection.createStatement()) {
        statement.executeUpdate("UPDATE app_users SET auth_user_id = 'different-account'");
      }
      assertThrows(
          IllegalStateException.class,
          () -> PostgresSnapshotComparison.requireIdentical(before, after));
    }
  }

  private Path snapshot(HostedPostgresConfig config, String name, Instant time) throws Exception {
    return PostgresSqliteBackupService.createBackup(
            config,
            directory.resolve(name + ".db"),
            false,
            PostgresSqliteBackupService.BackupObserver.NONE,
            Clock.fixed(time, ZoneOffset.UTC))
        .backupPath();
  }

  private static HostedPostgresConfig config() {
    return new HostedPostgresConfig(
        "jdbc:h2:mem:compare_"
            + UUID.randomUUID()
            + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "",
        "");
  }
}
