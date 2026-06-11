package com.lifttrax.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class SqliteDbExecutionEditTest {

  @Test
  void updateLiftExecutionReplacesRecordAndExecutionSetRows() throws Exception {
    Path dbPath = Files.createTempFile("lifttrax-execution-update", ".db");
    try (SqliteDb db = new SqliteDb(dbPath.toString())) {
      db.addLift("Back Squat", LiftRegion.LOWER, LiftType.SQUAT, List.of(), "");
      db.addLiftExecution(
          "Back Squat",
          new LiftExecution(
              null,
              LocalDate.of(2026, 4, 20),
              List.of(
                  new ExecutionSet(new SetMetric.Reps(5), "225 lb", 8.0f),
                  new ExecutionSet(new SetMetric.Reps(3), "245 lb", 8.5f)),
              false,
              false,
              "original"));
      int executionId = db.getExecutions("Back Squat").get(0).id();

      db.updateLiftExecution(
          executionId,
          new LiftExecution(
              executionId,
              LocalDate.of(2026, 4, 21),
              List.of(new ExecutionSet(new SetMetric.TimeSecs(30), "sled", 7.0f)),
              true,
              false,
              "fixed"));

      LiftExecution updated = db.getExecution("Back Squat", executionId);
      assertEquals(LocalDate.of(2026, 4, 21), updated.date());
      assertTrue(updated.warmup());
      assertEquals("fixed", updated.notes());
      assertEquals(
          List.of(new ExecutionSet(new SetMetric.TimeSecs(30), "sled", 7.0f)), updated.sets());
      assertEquals(1, executionSetRows(dbPath, executionId));
    } finally {
      Files.deleteIfExists(dbPath);
    }
  }

  @Test
  void deleteLiftExecutionRemovesRecordAndExecutionSetRows() throws Exception {
    Path dbPath = Files.createTempFile("lifttrax-execution-delete", ".db");
    try (SqliteDb db = new SqliteDb(dbPath.toString())) {
      db.addLift("Bench Press", LiftRegion.UPPER, LiftType.BENCH_PRESS, List.of(), "");
      db.addLiftExecution(
          "Bench Press",
          new LiftExecution(
              null,
              LocalDate.of(2026, 4, 20),
              List.of(
                  new ExecutionSet(new SetMetric.Reps(5), "185 lb", null),
                  new ExecutionSet(new SetMetric.Reps(5), "195 lb", null)),
              false,
              false,
              ""));
      int executionId = db.getExecutions("Bench Press").get(0).id();

      db.deleteLiftExecution(executionId);

      assertTrue(db.getExecutions("Bench Press").isEmpty());
      assertEquals(0, executionSetRows(dbPath, executionId));
    } finally {
      Files.deleteIfExists(dbPath);
    }
  }

  private static int executionSetRows(Path dbPath, int executionId) throws Exception {
    try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        var statement =
            connection.prepareStatement(
                "SELECT COUNT(*) FROM execution_sets WHERE record_id = ?")) {
      statement.setInt(1, executionId);
      try (var rs = statement.executeQuery()) {
        return rs.next() ? rs.getInt(1) : 0;
      }
    }
  }
}
