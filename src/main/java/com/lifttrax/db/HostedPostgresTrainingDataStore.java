package com.lifttrax.db;

import com.lifttrax.models.CatalogEntry;
import com.lifttrax.models.Lift;
import com.lifttrax.models.LiftExecution;
import com.lifttrax.models.LiftRegion;
import com.lifttrax.models.LiftStats;
import com.lifttrax.models.LiftType;
import com.lifttrax.models.Muscle;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** User-scoped hosted JDBC/Postgres implementation for core web training data. */
final class HostedPostgresTrainingDataStore extends HostedSavedWorkoutStore {
  private final String appUserId;

  HostedPostgresTrainingDataStore(
      HostedPostgresConfig config, String authUserId, String appUserId, String lifterProfileId) {
    super(config, lifterProfileId);
    HostedExecutionJdbc.requireUserId(authUserId);
    this.appUserId = appUserId;
  }

  @Override
  public List<CatalogEntry> listCatalogEntries() throws Exception {
    try (Connection connection = openConnection()) {
      return new HostedCatalogImport(connection, appUserId, lifterProfileId).list(false);
    }
  }

  @Override
  public int importCatalog(List<CatalogEntry> entries, String expectedFingerprint)
      throws Exception {
    try (Connection connection = openConnection()) {
      return new HostedCatalogImport(connection, appUserId, lifterProfileId)
          .save(entries, expectedFingerprint);
    }
  }

  @Override
  public DashboardSnapshot dashboardSnapshot(LocalDate today) throws Exception {
    try (Connection connection = openConnection()) {
      return new HostedDashboardReader(connection, appUserId, lifterProfileId).load(today);
    }
  }

  @Override
  public void addLift(
      String name, LiftRegion region, LiftType main, List<Muscle> muscles, String notes)
      throws Exception {
    try (Connection connection = openConnection();
        PreparedStatement statement =
            connection.prepareStatement(
                """
                    INSERT INTO exercise_catalog_entries (
                        id, owner_user_id, lifter_profile_id, name, region, main_lift,
                        muscles, notes, enabled, origin
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, TRUE, 'user_created')
                    """)) {
      statement.setString(1, HostedExecutionJdbc.newId());
      statement.setString(2, appUserId);
      statement.setString(3, lifterProfileId);
      statement.setString(4, name);
      statement.setString(5, region.name());
      statement.setString(6, main == null ? null : main.toDbValue());
      statement.setString(7, HostedExecutionJdbc.serializeMuscles(muscles));
      statement.setString(8, notes == null ? "" : notes);
      statement.executeUpdate();
    }
  }

  @Override
  public void addLiftExecution(String name, LiftExecution execution) throws Exception {
    try (Connection connection = openConnection()) {
      connection.setAutoCommit(false);
      try {
        String catalogEntryId = queries().lockEnabledCatalogEntry(connection, name);
        String executionId = HostedExecutionJdbc.newId();
        int webExecutionId;
        try (PreparedStatement statement =
            connection.prepareStatement(
                """
                  INSERT INTO executions (
                      id, lifter_profile_id, catalog_entry_id, performed_on,
                      warmup, deload, notes, performed_catalog_id, performed_exercise_name,
                      performed_region, performed_main_lift, performed_muscles, performed_catalog_notes
                  )
                  SELECT ?, ?, id, ?, ?, ?, ?, id, name, region, main_lift, muscles, notes
                  FROM exercise_catalog_entries WHERE id = ?
                  """,
                new String[] {"web_execution_id"})) {
          statement.setString(1, executionId);
          statement.setString(2, lifterProfileId);
          statement.setObject(3, execution.date());
          statement.setBoolean(4, execution.warmup());
          statement.setBoolean(5, execution.deload());
          statement.setString(6, execution.notes() == null ? "" : execution.notes());
          statement.setString(7, catalogEntryId);
          statement.executeUpdate();
          try (ResultSet keys = statement.getGeneratedKeys()) {
            if (!keys.next()) {
              throw new IllegalStateException("Hosted execution ID was not generated.");
            }
            webExecutionId = keys.getInt(1);
          }
        }
        HostedExecutionJdbc.saveExecutionSets(connection, executionId, execution.sets());
        if (webExecutionId <= 0) {
          throw new IllegalStateException("Hosted execution ID was invalid.");
        }
        connection.commit();
      } catch (Exception e) {
        connection.rollback();
        throw e;
      }
    }
  }

  @Override
  public WorkoutSubmissionReceipt getWorkoutSubmission(String submissionId) throws Exception {
    try (Connection connection = openConnection();
        PreparedStatement statement =
            connection.prepareStatement(
                """
                    SELECT payload_fingerprint, logged_execution_count,
                           skipped_exercises, skipped_sets
                    FROM workout_submission_receipts
                    WHERE lifter_profile_id = ? AND submission_id = ?
                    """)) {
      statement.setString(1, lifterProfileId);
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
    try (Connection connection = openConnection();
        PreparedStatement statement =
            connection.prepareStatement(
                """
                    INSERT INTO workout_submission_receipts (
                        lifter_profile_id, submission_id, payload_fingerprint,
                        logged_execution_count, skipped_exercises, skipped_sets
                    )
                    VALUES (?, ?, ?, ?, ?, ?)
                    """)) {
      statement.setString(1, lifterProfileId);
      statement.setString(2, submissionId);
      statement.setString(3, receipt.fingerprint());
      statement.setInt(4, receipt.loggedExecutionCount());
      statement.setInt(5, receipt.skippedExercises());
      statement.setInt(6, receipt.skippedSets());
      try {
        statement.executeUpdate();
      } catch (SQLException e) {
        if (!"23505".equals(e.getSQLState())) {
          throw e;
        }
      }
    }
    WorkoutSubmissionReceipt stored = getWorkoutSubmission(submissionId);
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
    try (Connection connection = openConnection();
        PreparedStatement statement =
            connection.prepareStatement(
                """
                    UPDATE exercise_catalog_entries
                    SET name = ?, region = ?, main_lift = ?, muscles = ?, notes = ?
                    WHERE owner_user_id = ? AND lifter_profile_id = ? AND name = ?
                    """)) {
      statement.setString(1, newName);
      statement.setString(2, region.name());
      statement.setString(3, main == null ? null : main.toDbValue());
      statement.setString(4, HostedExecutionJdbc.serializeMuscles(muscles));
      statement.setString(5, notes == null ? "" : notes);
      statement.setString(6, appUserId);
      statement.setString(7, lifterProfileId);
      statement.setString(8, currentName);
      if (statement.executeUpdate() == 0) {
        throw new IllegalArgumentException("Lift not found.");
      }
    } catch (SQLException e) {
      if ("23505".equals(e.getSQLState())) {
        throw new IllegalArgumentException(
            "A catalog entry already uses that name. Choose a distinct name or disable the duplicate; completed histories cannot be merged by renaming.",
            e);
      }
      throw e;
    }
  }

  @Override
  public void deleteLift(String name) throws Exception {
    try (Connection connection = openConnection()) {
      connection.setAutoCommit(false);
      try {
        String catalogEntryId = queries().findCatalogEntryId(connection, name);
        if (catalogEntryId == null) {
          throw new IllegalArgumentException("Lift not found.");
        }
        try (PreparedStatement lock =
            connection.prepareStatement(
                "SELECT id FROM exercise_catalog_entries WHERE id = ? FOR UPDATE")) {
          lock.setString(1, catalogEntryId);
          try (ResultSet rows = lock.executeQuery()) {
            if (!rows.next()) {
              throw new IllegalArgumentException("Lift not found.");
            }
          }
        }
        try (PreparedStatement statement =
            connection.prepareStatement(
                "UPDATE executions SET catalog_entry_id = NULL WHERE lifter_profile_id = ? AND catalog_entry_id = ?")) {
          statement.setString(1, lifterProfileId);
          statement.setString(2, catalogEntryId);
          statement.executeUpdate();
        }
        try (PreparedStatement statement =
            connection.prepareStatement(
                "DELETE FROM exercise_catalog_entries WHERE owner_user_id = ? AND lifter_profile_id = ? AND id = ?")) {
          statement.setString(1, appUserId);
          statement.setString(2, lifterProfileId);
          statement.setString(3, catalogEntryId);
          statement.executeUpdate();
        }
        connection.commit();
      } catch (Exception e) {
        connection.rollback();
        throw e;
      }
    }
  }

  @Override
  public void setLiftEnabled(String name, boolean enabled) throws Exception {
    try (Connection connection = openConnection();
        PreparedStatement statement =
            connection.prepareStatement(
                """
                    UPDATE exercise_catalog_entries
                    SET enabled = ?
                    WHERE owner_user_id = ? AND lifter_profile_id = ? AND name = ?
                    """)) {
      statement.setBoolean(1, enabled);
      statement.setString(2, appUserId);
      statement.setString(3, lifterProfileId);
      statement.setString(4, name);
      if (statement.executeUpdate() == 0) {
        throw new IllegalArgumentException("Lift not found.");
      }
    }
  }

  @Override
  public boolean isLiftEnabled(String name) throws Exception {
    try (Connection connection = openConnection();
        PreparedStatement statement =
            connection.prepareStatement(
                """
                    SELECT enabled FROM exercise_catalog_entries
                    WHERE owner_user_id = ? AND lifter_profile_id = ? AND name = ?
                    """)) {
      statement.setString(1, appUserId);
      statement.setString(2, lifterProfileId);
      statement.setString(3, name);
      try (ResultSet rs = statement.executeQuery()) {
        if (!rs.next()) {
          throw new IllegalArgumentException("Lift not found: " + name);
        }
        return rs.getBoolean("enabled");
      }
    }
  }

  @Override
  public List<LiftExecution> getExecutions(String liftName) throws Exception {
    return getExecutionsByLift(List.of(liftName)).getOrDefault(liftName, List.of());
  }

  @Override
  public Map<String, List<LiftExecution>> getExecutionsByLift(Collection<String> liftNames)
      throws Exception {
    try (Connection connection = openConnection()) {
      return HostedExecutionBatchReader.load(connection, appUserId, lifterProfileId, liftNames);
    }
  }

  @Override
  public void updateLiftExecution(int execId, LiftExecution execution) throws Exception {
    try (Connection connection = openConnection()) {
      connection.setAutoCommit(false);
      try {
        String hostedExecutionId = queries().findHostedExecutionId(connection, execId);
        if (hostedExecutionId == null) {
          throw new IllegalArgumentException("Execution not found.");
        }
        try (PreparedStatement statement =
            connection.prepareStatement(
                """
                  UPDATE executions
                  SET performed_on = ?, warmup = ?, deload = ?, notes = ?
                  WHERE lifter_profile_id = ? AND web_execution_id = ?
                  """)) {
          statement.setObject(1, execution.date());
          statement.setBoolean(2, execution.warmup());
          statement.setBoolean(3, execution.deload());
          statement.setString(4, execution.notes() == null ? "" : execution.notes());
          statement.setString(5, lifterProfileId);
          statement.setInt(6, execId);
          if (statement.executeUpdate() == 0) {
            throw new IllegalArgumentException("Execution not found.");
          }
        }
        HostedExecutionJdbc.saveExecutionSets(connection, hostedExecutionId, execution.sets());
        connection.commit();
      } catch (Exception e) {
        connection.rollback();
        throw e;
      }
    }
  }

  @Override
  public void deleteLiftExecution(int execId) throws Exception {
    try (Connection connection = openConnection()) {
      connection.setAutoCommit(false);
      try {
        String hostedExecutionId = queries().findHostedExecutionId(connection, execId);
        if (hostedExecutionId == null) {
          throw new IllegalArgumentException("Execution not found.");
        }
        try (PreparedStatement statement =
            connection.prepareStatement("DELETE FROM execution_sets WHERE execution_id = ?")) {
          statement.setString(1, hostedExecutionId);
          statement.executeUpdate();
        }
        try (PreparedStatement statement =
            connection.prepareStatement(
                "DELETE FROM executions WHERE lifter_profile_id = ? AND web_execution_id = ?")) {
          statement.setString(1, lifterProfileId);
          statement.setInt(2, execId);
          if (statement.executeUpdate() == 0) {
            throw new IllegalArgumentException("Execution not found.");
          }
        }
        connection.commit();
      } catch (Exception e) {
        connection.rollback();
        throw e;
      }
    }
  }

  @Override
  public LiftStats liftStats(String name) throws Exception {
    try (Connection connection = openConnection()) {
      String catalogEntryId = queries().findCatalogEntryId(connection, name);
      if (catalogEntryId == null) {
        try (PreparedStatement statement =
            connection.prepareStatement(
                "SELECT DISTINCT performed_catalog_id FROM executions WHERE lifter_profile_id = ? AND performed_exercise_name = ?")) {
          statement.setString(1, lifterProfileId);
          statement.setString(2, name);
          try (ResultSet rows = statement.executeQuery()) {
            if (!rows.next()) {
              throw new IllegalArgumentException("Lift not found: " + name);
            }
            catalogEntryId = rows.getString(1);
            if (rows.next()) {
              throw new IllegalArgumentException(
                  "Multiple historical exercises share this name. Use the dated History view to inspect their records.");
            }
          }
        }
      }
      return new LiftStats(
          queries().getLastExecution(connection, catalogEntryId),
          queries().collectBestByReps(connection, catalogEntryId));
    }
  }

  @Override
  public List<Lift> listLifts() throws Exception {
    try (Connection connection = openConnection();
        PreparedStatement statement =
            connection.prepareStatement(
                """
                    SELECT name, region, main_lift, muscles, notes
                    FROM exercise_catalog_entries
                    WHERE owner_user_id = ? AND lifter_profile_id = ?
                    ORDER BY name
                    """)) {
      statement.setString(1, appUserId);
      statement.setString(2, lifterProfileId);
      try (ResultSet rs = statement.executeQuery()) {
        List<Lift> lifts = new ArrayList<>();
        while (rs.next()) {
          lifts.add(HostedExecutionJdbc.mapLift(rs));
        }
        return lifts;
      }
    }
  }

  @Override
  public Lift getLift(String name) throws Exception {
    try (Connection connection = openConnection();
        PreparedStatement statement =
            connection.prepareStatement(
                """
                    SELECT name, region, main_lift, muscles, notes
                    FROM exercise_catalog_entries
                    WHERE owner_user_id = ? AND lifter_profile_id = ? AND name = ?
                    """)) {
      statement.setString(1, appUserId);
      statement.setString(2, lifterProfileId);
      statement.setString(3, name);
      try (ResultSet rs = statement.executeQuery()) {
        if (!rs.next()) {
          throw new IllegalArgumentException("Lift not found: " + name);
        }
        return HostedExecutionJdbc.mapLift(rs);
      }
    }
  }

  @Override
  public List<Lift> liftsByType(LiftType liftType) throws Exception {
    return listLiftsWhere("main_lift = ?", liftType.toDbValue());
  }

  @Override
  public List<Lift> getAccessoriesByMuscle(Muscle muscle) throws Exception {
    return listLiftsWhere(
            "main_lift = ? AND muscles LIKE ?",
            LiftType.ACCESSORY.toDbValue(),
            "%" + muscle.name() + "%")
        .stream()
        .filter(lift -> lift.muscles().contains(muscle))
        .toList();
  }

  @Override
  public List<Lift> liftsByRegionAndType(LiftRegion region, LiftType liftType) throws Exception {
    return listLiftsWhere("region = ? AND main_lift = ?", region.name(), liftType.toDbValue());
  }

  @Override
  public List<LiftExecutionRow> getExecutionsBetween(LocalDate start, LocalDate end)
      throws Exception {
    try (Connection connection = openConnection()) {
      return queries().readSnapshots(connection, start, end, null, null);
    }
  }

  @Override
  public List<LiftExecutionRow> liftHistory(String name) throws Exception {
    try (Connection connection = openConnection()) {
      String identity = queries().findCatalogEntryId(connection, name);
      return queries()
          .readSnapshots(
              connection,
              LocalDate.of(1, 1, 1),
              LocalDate.of(9999, 12, 31),
              identity == null ? "performed_exercise_name" : "performed_catalog_id",
              identity == null ? name : identity);
    }
  }

  @Override
  public List<LiftExecutionRow> historyForExecution(int executionId) throws Exception {
    try (Connection connection = openConnection();
        PreparedStatement statement =
            connection.prepareStatement(
                "SELECT performed_catalog_id FROM executions WHERE lifter_profile_id = ? AND web_execution_id = ?")) {
      statement.setString(1, lifterProfileId);
      statement.setInt(2, executionId);
      try (ResultSet rows = statement.executeQuery()) {
        return rows.next()
            ? queries()
                .readSnapshots(
                    connection,
                    LocalDate.of(1, 1, 1),
                    LocalDate.of(9999, 12, 31),
                    "performed_catalog_id",
                    rows.getString(1))
            : List.of();
      }
    }
  }

  @Override
  public ExecutionHistorySummary executionHistorySummary(LocalDate start, LocalDate end)
      throws Exception {
    try (Connection connection = openConnection();
        PreparedStatement statement =
            connection.prepareStatement(
                """
                    SELECT
                        COUNT(*) AS record_count,
                        MIN(performed_on) AS min_date,
                        MAX(performed_on) AS max_date,
                        MAX(CASE WHEN performed_on < ? THEN performed_on END) AS nearest_before,
                        MIN(CASE WHEN performed_on > ? THEN performed_on END) AS nearest_after
                    FROM executions
                    WHERE lifter_profile_id = ?
                    """)) {
      statement.setObject(1, start);
      statement.setObject(2, end);
      statement.setString(3, lifterProfileId);
      try (ResultSet rs = statement.executeQuery()) {
        if (!rs.next()) {
          return new ExecutionHistorySummary(0, null, null, null, null);
        }
        return new ExecutionHistorySummary(
            rs.getInt("record_count"),
            HostedExecutionJdbc.parseDate(rs.getObject("min_date")),
            HostedExecutionJdbc.parseDate(rs.getObject("max_date")),
            HostedExecutionJdbc.parseDate(rs.getObject("nearest_before")),
            HostedExecutionJdbc.parseDate(rs.getObject("nearest_after")));
      }
    }
  }

  @Override
  public LiftExecution getLastExecution(String liftName, boolean warmup, boolean deload)
      throws Exception {
    try (Connection connection = openConnection();
        PreparedStatement statement =
            connection.prepareStatement(
                """
                    SELECT e.web_execution_id, e.id, e.performed_on, e.warmup, e.deload, e.notes
                    FROM executions e
                    JOIN exercise_catalog_entries c ON c.id = e.catalog_entry_id
                    WHERE e.lifter_profile_id = ? AND c.lifter_profile_id = ?
                        AND c.owner_user_id = ? AND c.name = ?
                        AND e.warmup = ? AND e.deload = ?
                    ORDER BY e.performed_on DESC, e.web_execution_id DESC
                    LIMIT 1
                    """)) {
      statement.setString(1, lifterProfileId);
      statement.setString(2, lifterProfileId);
      statement.setString(3, appUserId);
      statement.setString(4, liftName);
      statement.setBoolean(5, warmup);
      statement.setBoolean(6, deload);
      try (ResultSet rs = statement.executeQuery()) {
        return rs.next() ? HostedExecutionJdbc.mapExecution(connection, rs) : null;
      }
    }
  }

  @Override
  public LiftExecution getExecution(String liftName, int executionId) throws Exception {
    try (Connection connection = openConnection();
        PreparedStatement statement =
            connection.prepareStatement(
                """
                    SELECT e.web_execution_id, e.id, e.performed_on, e.warmup, e.deload, e.notes
                    FROM executions e
                    LEFT JOIN exercise_catalog_entries c ON c.id = e.catalog_entry_id
                        AND c.lifter_profile_id = e.lifter_profile_id
                    WHERE e.lifter_profile_id = ?
                        AND (e.performed_exercise_name = ? OR c.name = ?)
                        AND e.web_execution_id = ?
                    """)) {
      statement.setString(1, lifterProfileId);
      statement.setString(2, liftName);
      statement.setString(3, liftName);
      statement.setInt(4, executionId);
      try (ResultSet rs = statement.executeQuery()) {
        return rs.next() ? HostedExecutionJdbc.mapExecution(connection, rs) : null;
      }
    }
  }

  @Override
  public Map<String, Boolean> liftEnabledStatuses() throws Exception {
    try (Connection connection = openConnection();
        PreparedStatement statement =
            connection.prepareStatement(
                """
                    SELECT name, enabled FROM exercise_catalog_entries
                    WHERE owner_user_id = ? AND lifter_profile_id = ?
                    """)) {
      statement.setString(1, appUserId);
      statement.setString(2, lifterProfileId);
      Map<String, Boolean> statuses = new HashMap<>();
      try (ResultSet rs = statement.executeQuery()) {
        while (rs.next()) {
          statuses.put(rs.getString("name"), rs.getBoolean("enabled"));
        }
      }
      return statuses;
    }
  }

  private List<Lift> listLiftsWhere(String predicate, String... values) throws Exception {
    String sql =
        """
            SELECT name, region, main_lift, muscles, notes
            FROM exercise_catalog_entries
            WHERE owner_user_id = ? AND lifter_profile_id = ? AND enabled = TRUE AND
            """
            + predicate
            + " ORDER BY name";
    try (Connection connection = openConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, appUserId);
      statement.setString(2, lifterProfileId);
      for (int i = 0; i < values.length; i++) {
        statement.setString(i + 3, values[i]);
      }
      try (ResultSet rs = statement.executeQuery()) {
        List<Lift> lifts = new ArrayList<>();
        while (rs.next()) {
          lifts.add(HostedExecutionJdbc.mapLift(rs));
        }
        return lifts;
      }
    }
  }

  private HostedTrainingQueries queries() {
    return new HostedTrainingQueries(appUserId, lifterProfileId);
  }

  HostedPostgresConfig hostedConfig() {
    return config;
  }

  String ownerAppUserId() {
    return appUserId;
  }

  String defaultLifterProfileId() {
    return lifterProfileId;
  }
}
