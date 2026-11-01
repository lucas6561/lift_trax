package com.lifttrax.db;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifttrax.models.ExecutionSet;
import com.lifttrax.models.Lift;
import com.lifttrax.models.LiftExecution;
import com.lifttrax.models.LiftRegion;
import com.lifttrax.models.LiftStats;
import com.lifttrax.models.LiftType;
import com.lifttrax.models.Muscle;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Write-side operations for the SQLite compatibility fixture. */
abstract class SqliteDbWriter implements TrainingDataStore {
  public static final int MAX_BACKUPS = 5;
  public static final String LEGACY_OWNER_USER_ID = "local-user";

  private static final Logger LOGGER = LoggerFactory.getLogger(SqliteDb.class);

  protected Connection connection;
  protected final ObjectMapper objectMapper = new ObjectMapper();

  protected abstract Integer findLiftIdForUser(String ownerUserId, String name) throws Exception;

  protected abstract LiftExecution getLastExecutionForUser(String ownerUserId, int liftId)
      throws Exception;

  protected abstract Map<Integer, String> collectBestByRepsForUser(String ownerUserId, int liftId)
      throws Exception;

  protected abstract Lift mapLift(ResultSet rs) throws Exception;

  protected abstract void saveExecutionSets(int recordId, List<ExecutionSet> sets) throws Exception;

  protected static void logInfo(String message, Object... args) {
    if (LOGGER.isInfoEnabled()) {
      LOGGER.info(message, args);
    }
  }

  private static String requireOwnerUserId(String ownerUserId) {
    if (ownerUserId == null || ownerUserId.isBlank()) {
      throw new IllegalArgumentException("Owner user ID is required.");
    }
    return ownerUserId;
  }

  @Override
  public void addLift(
      String name, LiftRegion region, LiftType main, List<Muscle> muscles, String notes)
      throws Exception {
    addLiftForUser(LEGACY_OWNER_USER_ID, name, region, main, muscles, notes);
  }

  void addLiftForUser(
      String ownerUserId,
      String name,
      LiftRegion region,
      LiftType main,
      List<Muscle> muscles,
      String notes)
      throws Exception {
    String sql =
        """
                INSERT INTO lifts (owner_user_id, name, region, main_lift, muscles, notes)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
    String musclesValue =
        muscles == null || muscles.isEmpty()
            ? ""
            : muscles.stream().map(Enum::name).reduce((a, b) -> a + "," + b).orElse("");
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, requireOwnerUserId(ownerUserId));
      statement.setString(2, name);
      statement.setString(3, region.name());
      statement.setString(4, main == null ? null : main.toDbValue());
      statement.setString(5, musclesValue);
      statement.setString(6, notes == null ? "" : notes);
      int rows = statement.executeUpdate();
      logInfo(
          "db.addLift name={} region={} main={} result=rows:{}",
          name,
          region,
          main == null ? "" : main.toDbValue(),
          rows);
    }
  }

  @Override
  public void addLiftExecution(String name, LiftExecution execution) throws Exception {
    addLiftExecutionForUser(LEGACY_OWNER_USER_ID, name, execution);
  }

  void addLiftExecutionForUser(String ownerUserId, String name, LiftExecution execution)
      throws Exception {
    String owner = requireOwnerUserId(ownerUserId);
    Integer liftId = findLiftIdForUser(owner, name);
    if (liftId == null) {
      logInfo("db.addLiftExecution lift={} result=not_found", name);
      throw new IllegalArgumentException("Lift not found: " + name);
    }
    String setsJson = objectMapper.writeValueAsString(execution.sets());
    String sql =
        """
                INSERT INTO lift_records (owner_user_id, lift_id, date, sets, warmup, deload, notes)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
    try (PreparedStatement statement =
        connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
      statement.setString(1, owner);
      statement.setInt(2, liftId);
      statement.setString(3, execution.date().toString());
      statement.setString(4, setsJson);
      statement.setInt(5, execution.warmup() ? 1 : 0);
      statement.setInt(6, execution.deload() ? 1 : 0);
      statement.setString(7, execution.notes() == null ? "" : execution.notes());
      statement.executeUpdate();
      Integer recordId = null;
      try (ResultSet keys = statement.getGeneratedKeys()) {
        if (keys.next()) {
          recordId = keys.getInt(1);
          saveExecutionSets(recordId, execution.sets());
        }
      }
      logInfo(
          "db.addLiftExecution lift={} date={} sets={} result=id:{}",
          name,
          execution.date(),
          execution.sets().size(),
          recordId == null ? "" : recordId);
    }
  }

  @Override
  public WorkoutSubmissionReceipt getWorkoutSubmission(String submissionId) throws Exception {
    return getWorkoutSubmissionForUser(LEGACY_OWNER_USER_ID, submissionId);
  }

  WorkoutSubmissionReceipt getWorkoutSubmissionForUser(String ownerUserId, String submissionId)
      throws Exception {
    String sql =
        """
            SELECT payload_fingerprint, logged_execution_count, skipped_exercises, skipped_sets
            FROM workout_submission_receipts
            WHERE owner_user_id = ? AND submission_id = ?
            """;
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, requireOwnerUserId(ownerUserId));
      statement.setString(2, submissionId);
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          return null;
        }
        return new WorkoutSubmissionReceipt(
            resultSet.getString("payload_fingerprint"),
            resultSet.getInt("logged_execution_count"),
            resultSet.getInt("skipped_exercises"),
            resultSet.getInt("skipped_sets"));
      }
    }
  }

  @Override
  public void recordWorkoutSubmission(String submissionId, WorkoutSubmissionReceipt receipt)
      throws Exception {
    recordWorkoutSubmissionForUser(LEGACY_OWNER_USER_ID, submissionId, receipt);
  }

  void recordWorkoutSubmissionForUser(
      String ownerUserId, String submissionId, WorkoutSubmissionReceipt receipt) throws Exception {
    String sql =
        """
            INSERT OR IGNORE INTO workout_submission_receipts (
                owner_user_id, submission_id, payload_fingerprint,
                logged_execution_count, skipped_exercises, skipped_sets
            )
            VALUES (?, ?, ?, ?, ?, ?)
            """;
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, requireOwnerUserId(ownerUserId));
      statement.setString(2, submissionId);
      statement.setString(3, receipt.fingerprint());
      statement.setInt(4, receipt.loggedExecutionCount());
      statement.setInt(5, receipt.skippedExercises());
      statement.setInt(6, receipt.skippedSets());
      statement.executeUpdate();
    }
    WorkoutSubmissionReceipt stored = getWorkoutSubmissionForUser(ownerUserId, submissionId);
    if (stored == null || !stored.fingerprint().equals(receipt.fingerprint())) {
      throw new IllegalArgumentException(
          "This workout block conflicts with data already submitted from this session.");
    }
  }

  @Override
  public void updateLift(
      String currentName,
      String newName,
      LiftRegion region,
      LiftType main,
      List<Muscle> muscles,
      String notes)
      throws Exception {
    updateLiftForUser(LEGACY_OWNER_USER_ID, currentName, newName, region, main, muscles, notes);
  }

  void updateLiftForUser(
      String ownerUserId,
      String currentName,
      String newName,
      LiftRegion region,
      LiftType main,
      List<Muscle> muscles,
      String notes)
      throws Exception {
    String sql =
        """
                UPDATE lifts
                SET name = ?, region = ?, main_lift = ?, muscles = ?, notes = ?
                WHERE owner_user_id = ? AND name = ?
                """;
    String musclesValue =
        muscles == null || muscles.isEmpty()
            ? ""
            : muscles.stream().map(Enum::name).reduce((a, b) -> a + "," + b).orElse("");
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, newName);
      statement.setString(2, region.name());
      statement.setString(3, main == null ? null : main.toDbValue());
      statement.setString(4, musclesValue);
      statement.setString(5, notes == null ? "" : notes);
      statement.setString(6, requireOwnerUserId(ownerUserId));
      statement.setString(7, currentName);
      int rows = statement.executeUpdate();
      if (rows == 0) {
        throw new IllegalArgumentException("Lift not found.");
      }
      logInfo(
          "db.updateLift currentName={} newName={} region={} main={} result=rows:{}",
          currentName,
          newName,
          region,
          main == null ? "" : main.toDbValue(),
          rows);
    }
  }

  @Override
  public void deleteLift(String name) throws Exception {
    deleteLiftForUser(LEGACY_OWNER_USER_ID, name);
  }

  void deleteLiftForUser(String ownerUserId, String name) throws Exception {
    String owner = requireOwnerUserId(ownerUserId);
    Integer liftId = findLiftIdForUser(owner, name);
    if (liftId == null) {
      logInfo("db.deleteLift name={} result=not_found", name);
      throw new IllegalArgumentException("Lift not found.");
    }
    int recordsDeleted;
    try (PreparedStatement statement =
        connection.prepareStatement(
            "DELETE FROM lift_records WHERE owner_user_id = ? AND lift_id = ?")) {
      statement.setString(1, owner);
      statement.setInt(2, liftId);
      recordsDeleted = statement.executeUpdate();
    }
    int liftsDeleted;
    try (PreparedStatement statement =
        connection.prepareStatement("DELETE FROM lifts WHERE owner_user_id = ? AND id = ?")) {
      statement.setString(1, owner);
      statement.setInt(2, liftId);
      liftsDeleted = statement.executeUpdate();
    }
    logInfo("db.deleteLift name={} result=lifts:{},records:{}", name, liftsDeleted, recordsDeleted);
  }

  @Override
  public void setLiftEnabled(String name, boolean enabled) throws Exception {
    setLiftEnabledForUser(LEGACY_OWNER_USER_ID, name, enabled);
  }

  void setLiftEnabledForUser(String ownerUserId, String name, boolean enabled) throws Exception {
    String sql = "UPDATE lifts SET enabled = ? WHERE owner_user_id = ? AND name = ?";
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setInt(1, enabled ? 1 : 0);
      statement.setString(2, requireOwnerUserId(ownerUserId));
      statement.setString(3, name);
      int rows = statement.executeUpdate();
      if (rows == 0) {
        throw new IllegalArgumentException("Lift not found.");
      }
      logInfo("db.setLiftEnabled name={} enabled={} result=rows:{}", name, enabled, rows);
    }
  }

  @Override
  public boolean isLiftEnabled(String name) throws Exception {
    return isLiftEnabledForUser(LEGACY_OWNER_USER_ID, name);
  }

  boolean isLiftEnabledForUser(String ownerUserId, String name) throws Exception {
    String sql = "SELECT enabled FROM lifts WHERE owner_user_id = ? AND name = ?";
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, requireOwnerUserId(ownerUserId));
      statement.setString(2, name);
      try (ResultSet rs = statement.executeQuery()) {
        if (!rs.next()) {
          logInfo("db.isLiftEnabled name={} result=not_found", name);
          throw new IllegalArgumentException("Lift not found: " + name);
        }
        boolean enabled = rs.getInt("enabled") != 0;
        logInfo("db.isLiftEnabled name={} result={}", name, enabled);
        return enabled;
      }
    }
  }

  @Override
  public Map<String, Boolean> liftEnabledStatuses() throws Exception {
    return liftEnabledStatusesForUser(LEGACY_OWNER_USER_ID);
  }

  Map<String, Boolean> liftEnabledStatusesForUser(String ownerUserId) throws Exception {
    String sql = "SELECT name, enabled FROM lifts WHERE owner_user_id = ?";
    Map<String, Boolean> statuses = new HashMap<>();
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, requireOwnerUserId(ownerUserId));
      try (ResultSet rs = statement.executeQuery()) {
        while (rs.next()) {
          statuses.put(rs.getString("name"), rs.getInt("enabled") != 0);
        }
      }
    }
    logInfo("db.liftEnabledStatuses result=rows:{}", statuses.size());
    return statuses;
  }

  @Override
  public void updateLiftExecution(int execId, LiftExecution execution) throws Exception {
    updateLiftExecutionForUser(LEGACY_OWNER_USER_ID, execId, execution);
  }

  void updateLiftExecutionForUser(String ownerUserId, int execId, LiftExecution execution)
      throws Exception {
    String setsJson = objectMapper.writeValueAsString(execution.sets());
    String sql =
        """
                UPDATE lift_records
                SET date = ?, sets = ?, warmup = ?, deload = ?, notes = ?
                WHERE owner_user_id = ? AND id = ?
                """;
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, execution.date().toString());
      statement.setString(2, setsJson);
      statement.setInt(3, execution.warmup() ? 1 : 0);
      statement.setInt(4, execution.deload() ? 1 : 0);
      statement.setString(5, execution.notes() == null ? "" : execution.notes());
      statement.setString(6, requireOwnerUserId(ownerUserId));
      statement.setInt(7, execId);
      int rows = statement.executeUpdate();
      if (rows == 0) {
        throw new IllegalArgumentException("Execution not found.");
      }
      logInfo(
          "db.updateLiftExecution id={} date={} sets={} result=rows:{}",
          execId,
          execution.date(),
          execution.sets().size(),
          rows);
    }
    saveExecutionSets(execId, execution.sets());
  }

  @Override
  public void deleteLiftExecution(int execId) throws Exception {
    deleteLiftExecutionForUser(LEGACY_OWNER_USER_ID, execId);
  }

  void deleteLiftExecutionForUser(String ownerUserId, int execId) throws Exception {
    String owner = requireOwnerUserId(ownerUserId);
    int setsDeleted;
    try (PreparedStatement statement =
        connection.prepareStatement(
            """
                    DELETE FROM execution_sets
                    WHERE record_id IN (
                        SELECT id FROM lift_records WHERE owner_user_id = ? AND id = ?
                    )
                    """)) {
      statement.setString(1, owner);
      statement.setInt(2, execId);
      setsDeleted = statement.executeUpdate();
    }
    int recordsDeleted;
    try (PreparedStatement statement =
        connection.prepareStatement(
            "DELETE FROM lift_records WHERE owner_user_id = ? AND id = ?")) {
      statement.setString(1, owner);
      statement.setInt(2, execId);
      recordsDeleted = statement.executeUpdate();
    }
    if (recordsDeleted == 0) {
      throw new IllegalArgumentException("Execution not found.");
    }
    logInfo(
        "db.deleteLiftExecution id={} result=records:{},sets:{}",
        execId,
        recordsDeleted,
        setsDeleted);
  }

  @Override
  public LiftStats liftStats(String name) throws Exception {
    return liftStatsForUser(LEGACY_OWNER_USER_ID, name);
  }

  LiftStats liftStatsForUser(String ownerUserId, String name) throws Exception {
    String owner = requireOwnerUserId(ownerUserId);
    Integer liftId = findLiftIdForUser(owner, name);
    if (liftId == null) {
      logInfo("db.liftStats name={} result=not_found", name);
      throw new IllegalArgumentException("Lift not found: " + name);
    }
    LiftExecution last = getLastExecutionForUser(owner, liftId);
    Map<Integer, String> bestByReps = collectBestByRepsForUser(owner, liftId);
    LiftStats stats = new LiftStats(last, bestByReps);
    logInfo("db.liftStats name={} result=ok last={}", name, last == null ? "none" : last.date());
    return stats;
  }

  @Override
  public List<Lift> liftsByType(LiftType liftType) throws Exception {
    return liftsByTypeForUser(LEGACY_OWNER_USER_ID, liftType);
  }

  List<Lift> liftsByTypeForUser(String ownerUserId, LiftType liftType) throws Exception {
    String sql =
        """
                SELECT name, region, main_lift, muscles, notes
                FROM lifts
                WHERE owner_user_id = ? AND main_lift = ? AND enabled = 1
                ORDER BY name
                """;
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, requireOwnerUserId(ownerUserId));
      statement.setString(2, liftType.toDbValue());
      try (ResultSet rs = statement.executeQuery()) {
        List<Lift> lifts = new ArrayList<>();
        while (rs.next()) {
          lifts.add(mapLift(rs));
        }
        logInfo("db.liftsByType type={} result=ok", liftType);
        return lifts;
      }
    }
  }

  @Override
  public List<Lift> getAccessoriesByMuscle(Muscle muscle) throws Exception {
    return getAccessoriesByMuscleForUser(LEGACY_OWNER_USER_ID, muscle);
  }

  List<Lift> getAccessoriesByMuscleForUser(String ownerUserId, Muscle muscle) throws Exception {
    String sql =
        """
                SELECT name, region, main_lift, muscles, notes
                FROM lifts
                WHERE owner_user_id = ? AND main_lift = ? AND enabled = 1 AND muscles LIKE ?
                ORDER BY name
                """;
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, requireOwnerUserId(ownerUserId));
      statement.setString(2, LiftType.ACCESSORY.toDbValue());
      statement.setString(3, "%" + muscle.name() + "%");
      try (ResultSet rs = statement.executeQuery()) {
        List<Lift> lifts = new ArrayList<>();
        while (rs.next()) {
          Lift lift = mapLift(rs);
          if (lift.muscles().contains(muscle)) {
            lifts.add(lift);
          }
        }
        logInfo("db.getAccessoriesByMuscle muscle={} result=ok", muscle);
        return lifts;
      }
    }
  }

  @Override
  public List<Lift> liftsByRegionAndType(LiftRegion region, LiftType liftType) throws Exception {
    return liftsByRegionAndTypeForUser(LEGACY_OWNER_USER_ID, region, liftType);
  }

  List<Lift> liftsByRegionAndTypeForUser(String ownerUserId, LiftRegion region, LiftType liftType)
      throws Exception {
    String sql =
        """
                SELECT name, region, main_lift, muscles, notes
                FROM lifts
                WHERE owner_user_id = ? AND region = ? AND main_lift = ? AND enabled = 1
                ORDER BY name
                """;
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, requireOwnerUserId(ownerUserId));
      statement.setString(2, region.name());
      statement.setString(3, liftType.toDbValue());
      try (ResultSet rs = statement.executeQuery()) {
        List<Lift> lifts = new ArrayList<>();
        while (rs.next()) {
          lifts.add(mapLift(rs));
        }
        logInfo("db.liftsByRegionAndType region={} type={} result=ok", region, liftType);
        return lifts;
      }
    }
  }
}
