package com.lifttrax.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lifttrax.models.ExecutionSet;
import com.lifttrax.models.Lift;
import com.lifttrax.models.LiftExecution;
import com.lifttrax.models.LiftRegion;
import com.lifttrax.models.LiftType;
import com.lifttrax.models.SetMetric;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class SqliteDbUserScopeTest {

  @Test
  void scopedUsersOnlyReadTheirOwnLiftsAndCanReuseLiftNames() throws Exception {
    Path dbPath = Files.createTempFile("lifttrax-user-scope-lifts", ".db");
    try (SqliteDb db = new SqliteDb(dbPath.toString())) {
      TrainingDataStore localUser = db.forUser("local-user");
      TrainingDataStore otherUser = db.forUser("other-user");

      localUser.addLift("Back Squat", LiftRegion.LOWER, LiftType.SQUAT, List.of(), "mine");
      otherUser.addLift("Back Squat", LiftRegion.UPPER, LiftType.ACCESSORY, List.of(), "theirs");

      assertEquals(List.of("Back Squat"), localUser.listLifts().stream().map(Lift::name).toList());
      assertEquals(List.of("Back Squat"), otherUser.listLifts().stream().map(Lift::name).toList());
      assertEquals("mine", localUser.getLift("Back Squat").notes());
      assertEquals("theirs", otherUser.getLift("Back Squat").notes());
    } finally {
      Files.deleteIfExists(dbPath);
    }
  }

  @Test
  void scopedUsersCannotReadOrMutateAnotherUsersExecutions() throws Exception {
    Path dbPath = Files.createTempFile("lifttrax-user-scope-executions", ".db");
    try (SqliteDb db = new SqliteDb(dbPath.toString())) {
      TrainingDataStore localUser = db.forUser("local-user");
      TrainingDataStore otherUser = db.forUser("other-user");

      localUser.addLift("Bench Press", LiftRegion.UPPER, LiftType.BENCH_PRESS, List.of(), "");
      otherUser.addLift("Bench Press", LiftRegion.UPPER, LiftType.BENCH_PRESS, List.of(), "");
      localUser.addLiftExecution(
          "Bench Press",
          new LiftExecution(
              null,
              LocalDate.of(2026, 6, 14),
              List.of(new ExecutionSet(new SetMetric.Reps(5), "185 lb", null)),
              false,
              false,
              "private"));
      int privateExecutionId = localUser.getExecutions("Bench Press").get(0).id();

      assertTrue(otherUser.getExecutions("Bench Press").isEmpty());
      assertNull(otherUser.getExecution("Bench Press", privateExecutionId));
      assertThrows(
          IllegalArgumentException.class,
          () ->
              otherUser.updateLiftExecution(
                  privateExecutionId,
                  new LiftExecution(
                      privateExecutionId,
                      LocalDate.of(2026, 6, 15),
                      List.of(new ExecutionSet(new SetMetric.Reps(1), "405 lb", null)),
                      false,
                      false,
                      "tamper")));
      assertThrows(
          IllegalArgumentException.class, () -> otherUser.deleteLiftExecution(privateExecutionId));
      assertEquals(1, localUser.getExecutions("Bench Press").size());
      assertEquals("private", localUser.getExecution("Bench Press", privateExecutionId).notes());
    } finally {
      Files.deleteIfExists(dbPath);
    }
  }
}
