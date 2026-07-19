package com.lifttrax.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lifttrax.models.ExecutionSet;
import com.lifttrax.models.Lift;
import com.lifttrax.models.LiftExecution;
import com.lifttrax.models.LiftRegion;
import com.lifttrax.models.LiftType;
import com.lifttrax.models.Muscle;
import com.lifttrax.models.SetMetric;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HostedPostgresTrainingDataStoreTest {

  @Test
  void hostedAdapterScopesCoreLiftAndExecutionWorkflowsByUser() throws Exception {
    HostedPostgresTrainingDataStoreProvider provider = provider();
    TrainingDataStore userA = provider.forUser("user-a");
    TrainingDataStore userB = provider.forUser("user-b");

    userA.addLift("Bench Press", LiftRegion.UPPER, LiftType.BENCH_PRESS, List.of(Muscle.CHEST), "");
    userB.addLift("Bench Press", LiftRegion.LOWER, LiftType.ACCESSORY, List.of(Muscle.QUAD), "");
    userA.addLiftExecution(
        "Bench Press",
        new LiftExecution(
            null,
            LocalDate.of(2026, 6, 16),
            List.of(new ExecutionSet(new SetMetric.Reps(5), "225 lb", 8.0f)),
            false,
            false,
            "private"));

    List<Lift> userALifts = userA.listLifts();
    List<Lift> userBLifts = userB.listLifts();
    List<LiftExecution> userAExecutions = userA.getExecutions("Bench Press");

    assertEquals(List.of("Bench Press"), userALifts.stream().map(Lift::name).toList());
    assertEquals(List.of("Bench Press"), userBLifts.stream().map(Lift::name).toList());
    assertEquals(LiftRegion.UPPER, userA.getLift("Bench Press").region());
    assertEquals(LiftRegion.LOWER, userB.getLift("Bench Press").region());
    assertEquals(1, userAExecutions.size());
    assertEquals("private", userAExecutions.get(0).notes());
    assertTrue(userB.getExecutions("Bench Press").isEmpty());
    assertEquals(1, userA.latestExecutionsByLift().size());
    assertEquals(
        1,
        userA
            .executionHistorySummary(LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 20))
            .count());
    assertEquals(
        1, userA.getExecutionsBetween(LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 20)).size());
  }

  @Test
  void hostedAdapterLoadsMultipleExecutionHistoriesInOneBatch() throws Exception {
    TrainingDataStore store = provider().forUser("batch-user");
    store.addLift("Bench Press", LiftRegion.UPPER, LiftType.BENCH_PRESS, List.of(), "");
    store.addLift("Back Squat", LiftRegion.LOWER, LiftType.SQUAT, List.of(), "");
    store.addLiftExecution(
        "Bench Press",
        new LiftExecution(
            null,
            LocalDate.of(2026, 7, 17),
            List.of(new ExecutionSet(new SetMetric.Reps(1), "315 lb", null)),
            false,
            false,
            "bench"));
    store.addLiftExecution(
        "Back Squat",
        new LiftExecution(
            null,
            LocalDate.of(2026, 7, 18),
            List.of(new ExecutionSet(new SetMetric.Reps(3), "405 lb", 8.0f)),
            false,
            false,
            "squat"));

    Map<String, List<LiftExecution>> executionsByLift =
        store.getExecutionsByLift(List.of("Bench Press", "Back Squat", "Missing Lift"));

    assertEquals("bench", executionsByLift.get("Bench Press").get(0).notes());
    assertEquals("squat", executionsByLift.get("Back Squat").get(0).notes());
    assertTrue(executionsByLift.get("Missing Lift").isEmpty());
  }

  @Test
  void hostedAdapterRejectsCrossUserExecutionMutationWithoutLeakingPrivateRows() throws Exception {
    HostedPostgresTrainingDataStoreProvider provider = provider();
    TrainingDataStore owner = provider.forUser("owner");
    TrainingDataStore stranger = provider.forUser("stranger");

    owner.addLift("Back Squat", LiftRegion.LOWER, LiftType.SQUAT, List.of(), "");
    stranger.addLift("Back Squat", LiftRegion.LOWER, LiftType.SQUAT, List.of(), "");
    owner.addLiftExecution(
        "Back Squat",
        new LiftExecution(
            null,
            LocalDate.of(2026, 6, 16),
            List.of(new ExecutionSet(new SetMetric.Reps(3), "315 lb", null)),
            false,
            false,
            "owner only"));
    int privateExecutionId = owner.getExecutions("Back Squat").get(0).id();

    assertNull(stranger.getExecution("Back Squat", privateExecutionId));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            stranger.updateLiftExecution(
                privateExecutionId,
                new LiftExecution(
                    privateExecutionId,
                    LocalDate.of(2026, 6, 17),
                    List.of(new ExecutionSet(new SetMetric.Reps(1), "405 lb", null)),
                    false,
                    false,
                    "tamper")));
    assertThrows(
        IllegalArgumentException.class, () -> stranger.deleteLiftExecution(privateExecutionId));
    assertEquals("owner only", owner.getExecution("Back Squat", privateExecutionId).notes());
  }

  @Test
  void hostedAdapterSupportsDetailStatsAndEnabledFiltering() throws Exception {
    HostedPostgresTrainingDataStoreProvider provider = provider();
    TrainingDataStore store = provider.forUser("stats-user");

    store.addLift("Deadlift", LiftRegion.LOWER, LiftType.DEADLIFT, List.of(Muscle.HAMSTRING), "");
    store.addLift("Pulldown", LiftRegion.UPPER, LiftType.ACCESSORY, List.of(Muscle.LAT), "");
    store.addLiftExecution(
        "Deadlift",
        new LiftExecution(
            null,
            LocalDate.of(2026, 6, 15),
            List.of(new ExecutionSet(new SetMetric.Reps(5), "365 lb", null)),
            false,
            false,
            ""));
    store.addLiftExecution(
        "Deadlift",
        new LiftExecution(
            null,
            LocalDate.of(2026, 6, 16),
            List.of(new ExecutionSet(new SetMetric.Reps(5), "385 lb", null)),
            false,
            false,
            ""));

    assertEquals("385 lb", store.liftStats("Deadlift").bestByReps().get(5));
    assertEquals(
        LocalDate.of(2026, 6, 16), store.getLastExecution("Deadlift", false, false).date());
    assertEquals(
        List.of("Deadlift"),
        store.liftsByType(LiftType.DEADLIFT).stream().map(Lift::name).toList());
    assertEquals(
        List.of("Pulldown"),
        store.getAccessoriesByMuscle(Muscle.LAT).stream().map(Lift::name).toList());
    assertTrue(store.isLiftEnabled("Pulldown"));
    store.setLiftEnabled("Pulldown", false);
    assertFalse(store.isLiftEnabled("Pulldown"));
    assertTrue(store.getAccessoriesByMuscle(Muscle.LAT).isEmpty());
  }

  @Test
  void hostedImportCopiesLocalDatabaseIntoOneAccountAndSkipsRepeatedImports() throws Exception {
    Path source = Files.createTempFile("lifttrax-hosted-import-source", ".db");
    try (SqliteDb db = new SqliteDb(source.toString())) {
      db.addLift("Front Squat", LiftRegion.LOWER, LiftType.SQUAT, List.of(Muscle.QUAD), "");
      db.addLiftExecution(
          "Front Squat",
          new LiftExecution(
              null,
              LocalDate.of(2026, 6, 18),
              List.of(new ExecutionSet(new SetMetric.Reps(4), "255 lb", 8.5f)),
              false,
              false,
              "import me"));
    }
    HostedPostgresTrainingDataStoreProvider provider = provider();
    TrainingDataStore owner = provider.forUser("import-owner");
    TrainingDataStore other = provider.forUser("import-other");

    HostedLocalDatabaseImportService.ImportPreview preview =
        HostedLocalDatabaseImportService.preview(source);
    HostedLocalDatabaseImportService.ImportResult first =
        HostedLocalDatabaseImportService.importDatabase(source, owner);
    HostedLocalDatabaseImportService.ImportResult repeated =
        HostedLocalDatabaseImportService.importDatabase(source, owner);
    HostedLocalDatabaseImportService.ImportResult otherUser =
        HostedLocalDatabaseImportService.importDatabase(source, other);

    assertEquals(1, preview.liftCount());
    assertEquals(1, preview.executionCount());
    assertFalse(first.duplicate());
    assertEquals(1, first.insertedLifts());
    assertEquals(1, first.insertedExecutions());
    assertTrue(repeated.duplicate());
    assertEquals(0, repeated.insertedExecutions());
    assertFalse(otherUser.duplicate());
    assertEquals("import me", owner.getExecutions("Front Squat").get(0).notes());
    assertEquals("import me", other.getExecutions("Front Squat").get(0).notes());
  }

  @Test
  void hostedImportSupportsLegacyDatabaseWithoutUserOwnershipColumns() throws Exception {
    Path source = Files.createTempFile("lifttrax-legacy-hosted-import-source", ".db");
    try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + source);
        Statement statement = connection.createStatement()) {
      statement.execute(
          """
              CREATE TABLE lifts (
                  id INTEGER PRIMARY KEY,
                  name TEXT NOT NULL,
                  region TEXT NOT NULL,
                  main_lift TEXT,
                  muscles TEXT NOT NULL,
                  notes TEXT NOT NULL DEFAULT '',
                  enabled INTEGER NOT NULL DEFAULT 1
              )
              """);
      statement.execute(
          """
              CREATE TABLE lift_records (
                  id INTEGER PRIMARY KEY,
                  lift_id INTEGER NOT NULL,
                  date TEXT NOT NULL,
                  sets TEXT NOT NULL,
                  warmup INTEGER NOT NULL,
                  deload INTEGER NOT NULL,
                  notes TEXT NOT NULL DEFAULT ''
              )
              """);
      statement.execute(
          """
              INSERT INTO lifts (id, name, region, main_lift, muscles, notes, enabled)
              VALUES (1, 'Bench Press', 'UPPER', 'BENCH_PRESS', 'CHEST', '', 1)
              """);
      statement.execute(
          """
              INSERT INTO lift_records (id, lift_id, date, sets, warmup, deload, notes)
              VALUES (1, 1, '2026-06-01', '[{"reps":5,"weight":"225 lb"}]', 0, 0,
                      'legacy import')
              """);
      statement.execute("PRAGMA user_version = 11");
    }
    TrainingDataStore owner = provider().forUser("legacy-import-owner");

    HostedLocalDatabaseImportService.ImportResult result =
        HostedLocalDatabaseImportService.importDatabase(source, owner);

    assertEquals(1, result.insertedLifts());
    assertEquals(1, result.insertedExecutions());
    assertEquals("legacy import", owner.getExecutions("Bench Press").get(0).notes());
  }

  @Test
  void hostedConfigRequiresJdbcUrlWhenHostedModeIsSelected() {
    IllegalArgumentException error =
        assertThrows(IllegalArgumentException.class, () -> new HostedPostgresConfig("", "", ""));

    assertTrue(error.getMessage().contains("lifttrax.hosted.jdbcUrl"));
  }

  private static HostedPostgresTrainingDataStoreProvider provider() throws Exception {
    String name = "lifttrax_" + java.util.UUID.randomUUID().toString().replace("-", "");
    return new HostedPostgresTrainingDataStoreProvider(
        new HostedPostgresConfig(
            "jdbc:h2:mem:" + name + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "",
            ""));
  }
}
