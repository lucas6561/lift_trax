package com.lifttrax.db;

import com.lifttrax.models.ExecutionSet;
import com.lifttrax.models.Lift;
import com.lifttrax.models.LiftExecution;
import com.lifttrax.models.LiftRegion;
import com.lifttrax.models.LiftStats;
import com.lifttrax.models.LiftType;
import com.lifttrax.models.Muscle;
import com.lifttrax.models.SetMetric;
import com.lifttrax.models.WeightText;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** User-scoped hosted JDBC/Postgres implementation for core web training data. */
final class HostedPostgresTrainingDataStore implements TrainingDataStore {
  private final HostedPostgresConfig config;
  private final String appUserId;
  private final String lifterProfileId;

  HostedPostgresTrainingDataStore(
      HostedPostgresConfig config, String authUserId, String appUserId, String lifterProfileId) {
    this.config = config;
    requireUserId(authUserId);
    this.appUserId = appUserId;
    this.lifterProfileId = lifterProfileId;
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
                        muscles, notes, enabled
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, TRUE)
                    """)) {
      statement.setString(1, newId());
      statement.setString(2, appUserId);
      statement.setString(3, lifterProfileId);
      statement.setString(4, name);
      statement.setString(5, region.name());
      statement.setString(6, main == null ? null : main.toDbValue());
      statement.setString(7, serializeMuscles(muscles));
      statement.setString(8, notes == null ? "" : notes);
      statement.executeUpdate();
    }
  }

  @Override
  public void addLiftExecution(String name, LiftExecution execution) throws Exception {
    try (Connection connection = openConnection()) {
      String catalogEntryId = findCatalogEntryId(connection, name);
      if (catalogEntryId == null) {
        throw new IllegalArgumentException("Lift not found: " + name);
      }
      String executionId = newId();
      int webExecutionId;
      try (PreparedStatement statement =
          connection.prepareStatement(
              """
                  INSERT INTO executions (
                      id, lifter_profile_id, catalog_entry_id, performed_on,
                      warmup, deload, notes
                  )
                  VALUES (?, ?, ?, ?, ?, ?, ?)
                  """,
              new String[] {"web_execution_id"})) {
        statement.setString(1, executionId);
        statement.setString(2, lifterProfileId);
        statement.setString(3, catalogEntryId);
        statement.setObject(4, execution.date());
        statement.setBoolean(5, execution.warmup());
        statement.setBoolean(6, execution.deload());
        statement.setString(7, execution.notes() == null ? "" : execution.notes());
        statement.executeUpdate();
        try (ResultSet keys = statement.getGeneratedKeys()) {
          if (!keys.next()) {
            throw new IllegalStateException("Hosted execution ID was not generated.");
          }
          webExecutionId = keys.getInt(1);
        }
      }
      saveExecutionSets(connection, executionId, execution.sets());
      if (webExecutionId <= 0) {
        throw new IllegalStateException("Hosted execution ID was invalid.");
      }
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
      statement.setString(4, serializeMuscles(muscles));
      statement.setString(5, notes == null ? "" : notes);
      statement.setString(6, appUserId);
      statement.setString(7, lifterProfileId);
      statement.setString(8, currentName);
      if (statement.executeUpdate() == 0) {
        throw new IllegalArgumentException("Lift not found.");
      }
    }
  }

  @Override
  public void deleteLift(String name) throws Exception {
    try (Connection connection = openConnection()) {
      String catalogEntryId = findCatalogEntryId(connection, name);
      if (catalogEntryId == null) {
        throw new IllegalArgumentException("Lift not found.");
      }
      try (PreparedStatement statement =
          connection.prepareStatement(
              """
                  DELETE FROM execution_sets
                  WHERE execution_id IN (
                      SELECT id FROM executions
                      WHERE lifter_profile_id = ? AND catalog_entry_id = ?
                  )
                  """)) {
        statement.setString(1, lifterProfileId);
        statement.setString(2, catalogEntryId);
        statement.executeUpdate();
      }
      try (PreparedStatement statement =
          connection.prepareStatement(
              "DELETE FROM executions WHERE lifter_profile_id = ? AND catalog_entry_id = ?")) {
        statement.setString(1, lifterProfileId);
        statement.setString(2, catalogEntryId);
        statement.executeUpdate();
      }
      try (PreparedStatement statement =
          connection.prepareStatement(
              """
                  DELETE FROM exercise_catalog_entries
                  WHERE owner_user_id = ? AND lifter_profile_id = ? AND id = ?
                  """)) {
        statement.setString(1, appUserId);
        statement.setString(2, lifterProfileId);
        statement.setString(3, catalogEntryId);
        statement.executeUpdate();
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
    List<String> requestedNames = new ArrayList<>(new LinkedHashSet<>(liftNames));
    Map<String, List<LiftExecution>> executionsByLift = new LinkedHashMap<>();
    for (String liftName : requestedNames) {
      executionsByLift.put(liftName, new ArrayList<>());
    }
    if (requestedNames.isEmpty()) {
      return executionsByLift;
    }

    String placeholders = String.join(", ", Collections.nCopies(requestedNames.size(), "?"));
    String sql =
        """
            SELECT c.name, e.web_execution_id, e.id, e.performed_on, e.warmup, e.deload,
                e.notes, es.metric_kind, es.metric_a, es.metric_b, es.weight, es.rpe
            FROM executions e
            JOIN exercise_catalog_entries c ON c.id = e.catalog_entry_id
            LEFT JOIN execution_sets es ON es.execution_id = e.id
            WHERE e.lifter_profile_id = ? AND c.lifter_profile_id = ?
                AND c.owner_user_id = ? AND c.name IN (%s)
            ORDER BY c.name, e.performed_on DESC, e.web_execution_id DESC, es.set_index
            """
            .formatted(placeholders);
    try (Connection connection = openConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, lifterProfileId);
      statement.setString(2, lifterProfileId);
      statement.setString(3, appUserId);
      for (int i = 0; i < requestedNames.size(); i++) {
        statement.setString(i + 4, requestedNames.get(i));
      }
      try (ResultSet rs = statement.executeQuery()) {
        Map<String, Map<Integer, ExecutionRows>> rowsByLift = new LinkedHashMap<>();
        while (rs.next()) {
          String liftName = rs.getString("name");
          int webExecutionId = rs.getInt("web_execution_id");
          Map<Integer, ExecutionRows> rowsByExecution =
              rowsByLift.computeIfAbsent(liftName, ignored -> new LinkedHashMap<>());
          ExecutionRows rows = rowsByExecution.get(webExecutionId);
          if (rows == null) {
            rows =
                new ExecutionRows(
                    webExecutionId,
                    parseDate(rs.getObject("performed_on")),
                    rs.getBoolean("warmup"),
                    rs.getBoolean("deload"),
                    rs.getString("notes"),
                    new ArrayList<>());
            rowsByExecution.put(webExecutionId, rows);
          }
          if (rs.getString("metric_kind") != null) {
            rows.sets().add(mapExecutionSet(rs));
          }
        }
        for (Map.Entry<String, Map<Integer, ExecutionRows>> entry : rowsByLift.entrySet()) {
          executionsByLift.put(
              entry.getKey(),
              entry.getValue().values().stream().map(ExecutionRows::toExecution).toList());
        }
        return executionsByLift;
      }
    }
  }

  @Override
  public void updateLiftExecution(int execId, LiftExecution execution) throws Exception {
    try (Connection connection = openConnection()) {
      String hostedExecutionId = findHostedExecutionId(connection, execId);
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
      saveExecutionSets(connection, hostedExecutionId, execution.sets());
    }
  }

  @Override
  public void deleteLiftExecution(int execId) throws Exception {
    try (Connection connection = openConnection()) {
      String hostedExecutionId = findHostedExecutionId(connection, execId);
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
    }
  }

  @Override
  public LiftStats liftStats(String name) throws Exception {
    try (Connection connection = openConnection()) {
      String catalogEntryId = findCatalogEntryId(connection, name);
      if (catalogEntryId == null) {
        throw new IllegalArgumentException("Lift not found: " + name);
      }
      return new LiftStats(
          getLastExecution(connection, catalogEntryId),
          collectBestByReps(connection, catalogEntryId));
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
          lifts.add(mapLift(rs));
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
        return mapLift(rs);
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
    try (Connection connection = openConnection();
        PreparedStatement statement =
            connection.prepareStatement(
                """
                    SELECT
                        c.name, c.region, c.main_lift, c.muscles, c.notes AS lift_notes,
                        e.web_execution_id, e.id, e.performed_on, e.warmup, e.deload, e.notes
                    FROM executions e
                    JOIN exercise_catalog_entries c ON c.id = e.catalog_entry_id
                    WHERE e.lifter_profile_id = ? AND c.lifter_profile_id = ?
                        AND c.owner_user_id = ? AND e.performed_on BETWEEN ? AND ?
                    ORDER BY e.performed_on ASC, c.name ASC, e.web_execution_id ASC
                    """)) {
      statement.setString(1, lifterProfileId);
      statement.setString(2, lifterProfileId);
      statement.setString(3, appUserId);
      statement.setObject(4, start);
      statement.setObject(5, end);
      try (ResultSet rs = statement.executeQuery()) {
        List<LiftExecutionRow> rows = new ArrayList<>();
        while (rs.next()) {
          Lift lift =
              new Lift(
                  rs.getString("name"),
                  LiftRegion.fromString(rs.getString("region")),
                  LiftType.fromDbValue(rs.getString("main_lift")),
                  parseMuscles(rs.getString("muscles")),
                  rs.getString("lift_notes"));
          rows.add(new LiftExecutionRow(lift, mapExecution(connection, rs)));
        }
        return rows;
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
            parseDate(rs.getObject("min_date")),
            parseDate(rs.getObject("max_date")),
            parseDate(rs.getObject("nearest_before")),
            parseDate(rs.getObject("nearest_after")));
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
        return rs.next() ? mapExecution(connection, rs) : null;
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
                    JOIN exercise_catalog_entries c ON c.id = e.catalog_entry_id
                    WHERE e.lifter_profile_id = ? AND c.lifter_profile_id = ?
                        AND c.owner_user_id = ? AND c.name = ? AND e.web_execution_id = ?
                    """)) {
      statement.setString(1, lifterProfileId);
      statement.setString(2, lifterProfileId);
      statement.setString(3, appUserId);
      statement.setString(4, liftName);
      statement.setInt(5, executionId);
      try (ResultSet rs = statement.executeQuery()) {
        return rs.next() ? mapExecution(connection, rs) : null;
      }
    }
  }

  @Override
  public Map<String, LiftExecution> latestExecutionsByLift() throws Exception {
    String sql =
        """
            SELECT c.name, e.web_execution_id, e.id, e.performed_on, e.warmup, e.deload, e.notes
            FROM exercise_catalog_entries c
            JOIN executions e ON e.catalog_entry_id = c.id
            WHERE c.owner_user_id = ? AND c.lifter_profile_id = ? AND e.lifter_profile_id = ?
                AND e.web_execution_id = (
                    SELECT e2.web_execution_id
                    FROM executions e2
                    WHERE e2.lifter_profile_id = ? AND e2.catalog_entry_id = c.id
                    ORDER BY e2.performed_on DESC, e2.web_execution_id DESC
                    LIMIT 1
                )
            ORDER BY c.name
            """;
    Map<String, LiftExecution> latest = new HashMap<>();
    try (Connection connection = openConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, appUserId);
      statement.setString(2, lifterProfileId);
      statement.setString(3, lifterProfileId);
      statement.setString(4, lifterProfileId);
      try (ResultSet rs = statement.executeQuery()) {
        while (rs.next()) {
          latest.put(rs.getString("name"), mapExecution(connection, rs));
        }
      }
    }
    return latest;
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
          lifts.add(mapLift(rs));
        }
        return lifts;
      }
    }
  }

  private LiftExecution getLastExecution(Connection connection, String catalogEntryId)
      throws Exception {
    try (PreparedStatement statement =
        connection.prepareStatement(
            """
                SELECT web_execution_id, id, performed_on, warmup, deload, notes
                FROM executions
                WHERE lifter_profile_id = ? AND catalog_entry_id = ?
                ORDER BY performed_on DESC, web_execution_id DESC
                LIMIT 1
                """)) {
      statement.setString(1, lifterProfileId);
      statement.setString(2, catalogEntryId);
      try (ResultSet rs = statement.executeQuery()) {
        return rs.next() ? mapExecution(connection, rs) : null;
      }
    }
  }

  private Map<Integer, String> collectBestByReps(Connection connection, String catalogEntryId)
      throws Exception {
    String sql =
        """
            SELECT es.metric_a AS reps, es.weight
            FROM execution_sets es
            JOIN executions e ON e.id = es.execution_id
            WHERE e.lifter_profile_id = ? AND e.catalog_entry_id = ?
                AND es.metric_kind = 'reps'
            """;
    Map<Integer, String> bestByReps = new TreeMap<>();
    Map<Integer, Double> bestWeights = new HashMap<>();
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, lifterProfileId);
      statement.setString(2, catalogEntryId);
      try (ResultSet rs = statement.executeQuery()) {
        while (rs.next()) {
          int reps = rs.getInt("reps");
          String weight = normalizeWeight(rs.getString("weight"));
          double pounds = WeightText.toPounds(weight);
          Double current = bestWeights.get(reps);
          if (current == null || pounds > current) {
            bestWeights.put(reps, pounds);
            bestByReps.put(reps, weight);
          }
        }
      }
    }
    return bestByReps;
  }

  private String findCatalogEntryId(Connection connection, String name) throws Exception {
    try (PreparedStatement statement =
        connection.prepareStatement(
            """
                SELECT id FROM exercise_catalog_entries
                WHERE owner_user_id = ? AND lifter_profile_id = ? AND name = ?
                """)) {
      statement.setString(1, appUserId);
      statement.setString(2, lifterProfileId);
      statement.setString(3, name);
      try (ResultSet rs = statement.executeQuery()) {
        return rs.next() ? rs.getString("id") : null;
      }
    }
  }

  private String findHostedExecutionId(Connection connection, int webExecutionId) throws Exception {
    try (PreparedStatement statement =
        connection.prepareStatement(
            "SELECT id FROM executions WHERE lifter_profile_id = ? AND web_execution_id = ?")) {
      statement.setString(1, lifterProfileId);
      statement.setInt(2, webExecutionId);
      try (ResultSet rs = statement.executeQuery()) {
        return rs.next() ? rs.getString("id") : null;
      }
    }
  }

  private LiftExecution mapExecution(Connection connection, ResultSet rs) throws Exception {
    String executionId = rs.getString("id");
    return new LiftExecution(
        rs.getInt("web_execution_id"),
        parseDate(rs.getObject("performed_on")),
        loadExecutionSets(connection, executionId),
        rs.getBoolean("warmup"),
        rs.getBoolean("deload"),
        rs.getString("notes"));
  }

  private List<ExecutionSet> loadExecutionSets(Connection connection, String executionId)
      throws Exception {
    try (PreparedStatement statement =
        connection.prepareStatement(
            """
                SELECT metric_kind, metric_a, metric_b, weight, rpe
                FROM execution_sets
                WHERE execution_id = ?
                ORDER BY set_index
                """)) {
      statement.setString(1, executionId);
      try (ResultSet rs = statement.executeQuery()) {
        List<ExecutionSet> sets = new ArrayList<>();
        while (rs.next()) {
          sets.add(mapExecutionSet(rs));
        }
        return sets;
      }
    }
  }

  private static ExecutionSet mapExecutionSet(ResultSet rs) throws Exception {
    Integer metricB = rs.getObject("metric_b") == null ? null : rs.getInt("metric_b");
    Float rpe = rs.getObject("rpe") == null ? null : rs.getFloat("rpe");
    return new ExecutionSet(
        metricFromRow(rs.getString("metric_kind"), rs.getInt("metric_a"), metricB),
        normalizeWeight(rs.getString("weight")),
        rpe);
  }

  private void saveExecutionSets(Connection connection, String executionId, List<ExecutionSet> sets)
      throws Exception {
    try (PreparedStatement statement =
        connection.prepareStatement("DELETE FROM execution_sets WHERE execution_id = ?")) {
      statement.setString(1, executionId);
      statement.executeUpdate();
    }
    String sql =
        """
            INSERT INTO execution_sets (
                id, execution_id, set_index, metric_kind, metric_a, metric_b, weight, rpe
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      List<ExecutionSet> safeSets = sets == null ? List.of() : sets;
      for (int i = 0; i < safeSets.size(); i++) {
        ExecutionSet set = safeSets.get(i);
        MetricRow metric = metricToRow(set.metric());
        statement.setString(1, newId());
        statement.setString(2, executionId);
        statement.setInt(3, i);
        statement.setString(4, metric.kind());
        statement.setInt(5, metric.a());
        if (metric.b() == null) {
          statement.setNull(6, java.sql.Types.INTEGER);
        } else {
          statement.setInt(6, metric.b());
        }
        statement.setString(7, normalizeWeight(set.weight()));
        if (set.rpe() == null) {
          statement.setNull(8, java.sql.Types.REAL);
        } else {
          statement.setFloat(8, set.rpe());
        }
        statement.addBatch();
      }
      statement.executeBatch();
    }
  }

  private static Lift mapLift(ResultSet rs) throws Exception {
    return new Lift(
        rs.getString("name"),
        LiftRegion.fromString(rs.getString("region")),
        LiftType.fromDbValue(rs.getString("main_lift")),
        parseMuscles(rs.getString("muscles")),
        rs.getString("notes"));
  }

  private static List<Muscle> parseMuscles(String value) {
    if (value == null || value.isBlank()) {
      return Collections.emptyList();
    }
    List<Muscle> muscles = new ArrayList<>();
    for (String item : value.split(",")) {
      if (!item.isBlank()) {
        muscles.add(Muscle.fromString(item.trim()));
      }
    }
    return muscles;
  }

  private static String serializeMuscles(List<Muscle> muscles) {
    if (muscles == null || muscles.isEmpty()) {
      return "";
    }
    return String.join(",", muscles.stream().map(Enum::name).toList());
  }

  private static LocalDate parseDate(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof LocalDate localDate) {
      return localDate;
    }
    if (value instanceof java.sql.Date date) {
      return date.toLocalDate();
    }
    return LocalDate.parse(value.toString());
  }

  private static SetMetric metricFromRow(String kind, int a, Integer b) {
    return switch (kind) {
      case "reps" -> new SetMetric.Reps(a);
      case "reps-lr" -> new SetMetric.RepsLr(a, b == null ? a : b);
      case "reps-range" -> new SetMetric.RepsRange(a, b == null ? a : b);
      case "time" -> new SetMetric.TimeSecs(a);
      case "distance" -> new SetMetric.DistanceFeet(a);
      default -> new SetMetric.Reps(a);
    };
  }

  private static MetricRow metricToRow(SetMetric metric) {
    if (metric instanceof SetMetric.Reps reps) {
      return new MetricRow("reps", reps.reps(), null);
    }
    if (metric instanceof SetMetric.RepsLr repsLr) {
      return new MetricRow("reps-lr", repsLr.left(), repsLr.right());
    }
    if (metric instanceof SetMetric.RepsRange range) {
      return new MetricRow("reps-range", range.min(), range.max());
    }
    if (metric instanceof SetMetric.TimeSecs timeSecs) {
      return new MetricRow("time", timeSecs.seconds(), null);
    }
    if (metric instanceof SetMetric.DistanceFeet distanceFeet) {
      return new MetricRow("distance", distanceFeet.feet(), null);
    }
    return new MetricRow("reps", 0, null);
  }

  private static String normalizeWeight(String weight) {
    return weight == null || weight.isBlank() ? "none" : weight;
  }

  private static String newId() {
    return java.util.UUID.randomUUID().toString();
  }

  private static String requireUserId(String userId) {
    if (userId == null || userId.isBlank()) {
      throw new IllegalArgumentException("Authenticated user is required.");
    }
    return userId.trim();
  }

  private Connection openConnection() throws Exception {
    return HostedPostgresTrainingDataStoreProvider.openConnection(config);
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

  private record MetricRow(String kind, int a, Integer b) {}

  private record ExecutionRows(
      int id,
      LocalDate date,
      boolean warmup,
      boolean deload,
      String notes,
      List<ExecutionSet> sets) {
    private LiftExecution toExecution() {
      return new LiftExecution(id, date, List.copyOf(sets), warmup, deload, notes);
    }
  }
}
