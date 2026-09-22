package com.lifttrax.db;

import com.lifttrax.models.ExecutionSet;
import com.lifttrax.models.Lift;
import com.lifttrax.models.LiftExecution;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Loads only visible dashboard rows, using one connection and at most four queries. */
final class HostedDashboardReader {
  private final Connection connection;
  private final String owner;
  private final String profile;

  HostedDashboardReader(Connection connection, String owner, String profile) {
    this.connection = connection;
    this.owner = owner;
    this.profile = profile;
  }

  DashboardSnapshot load(LocalDate today) throws Exception {
    boolean hasLifts;
    int todayCount;
    try (PreparedStatement statement =
        connection.prepareStatement(
            """
        SELECT
          EXISTS(SELECT 1 FROM exercise_catalog_entries
            WHERE owner_user_id = ? AND lifter_profile_id = ?) AS has_lifts,
          (SELECT COUNT(*) FROM executions e
            JOIN exercise_catalog_entries c ON c.id = e.catalog_entry_id
            WHERE c.owner_user_id = ? AND c.lifter_profile_id = ?
              AND e.lifter_profile_id = ? AND e.performed_on = ?) AS today_count
        """)) {
      statement.setString(1, owner);
      statement.setString(2, profile);
      statement.setString(3, owner);
      statement.setString(4, profile);
      statement.setString(5, profile);
      statement.setObject(6, today);
      try (ResultSet rs = statement.executeQuery()) {
        if (!rs.next()) {
          throw new java.sql.SQLException("Dashboard summary was not returned.");
        }
        hasLifts = rs.getBoolean("has_lifts");
        todayCount = rs.getInt("today_count");
      }
    }
    List<PendingRow> suggestions =
        readRows(
            """
        SELECT c.name, c.region, c.main_lift, c.muscles, c.notes,
          e.id, e.web_execution_id, e.performed_on, e.warmup, e.deload, e.notes AS execution_notes
        FROM exercise_catalog_entries c
        LEFT JOIN executions e ON e.id = (
          SELECT e2.id FROM executions e2
          WHERE e2.catalog_entry_id = c.id AND e2.lifter_profile_id = ?
          ORDER BY e2.performed_on DESC, e2.web_execution_id DESC LIMIT 1)
        WHERE c.owner_user_id = ? AND c.lifter_profile_id = ? AND c.enabled = TRUE
        ORDER BY CASE c.main_lift
          WHEN 'SQUAT' THEN 10 WHEN 'DEADLIFT' THEN 20 WHEN 'BENCH PRESS' THEN 30
          WHEN 'OVERHEAD PRESS' THEN 40 WHEN 'CONDITIONING' THEN 60
          WHEN 'MOBILITY' THEN 70 ELSE 50 END,
          e.performed_on ASC NULLS FIRST, c.name ASC
        LIMIT 4
        """,
            null);
    List<PendingRow> recent =
        readRows(
            """
        SELECT c.name, c.region, c.main_lift, c.muscles, c.notes,
          e.id, e.web_execution_id, e.performed_on, e.warmup, e.deload, e.notes AS execution_notes
        FROM executions e JOIN exercise_catalog_entries c ON c.id = e.catalog_entry_id
        WHERE e.lifter_profile_id = ? AND c.owner_user_id = ? AND c.lifter_profile_id = ?
          AND e.performed_on BETWEEN ? AND ?
        ORDER BY e.performed_on DESC, e.web_execution_id DESC LIMIT 6
        """,
            today);
    Map<String, List<ExecutionSet>> sets = new LinkedHashMap<>();
    for (PendingRow row : suggestions) {
      if (row.id() != null) {
        sets.put(row.id(), new ArrayList<>());
      }
    }
    for (PendingRow row : recent) {
      sets.putIfAbsent(row.id(), new ArrayList<>());
    }
    loadSets(sets);
    return new DashboardSnapshot(
        hasLifts, todayCount, hydrate(suggestions, sets), hydrate(recent, sets));
  }

  private List<PendingRow> readRows(String sql, LocalDate today) throws Exception {
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, profile);
      statement.setString(2, owner);
      statement.setString(3, profile);
      if (today != null) {
        statement.setObject(4, today.minusDays(13));
        statement.setObject(5, today);
      }
      try (ResultSet rs = statement.executeQuery()) {
        List<PendingRow> rows = new ArrayList<>();
        while (rs.next()) {
          String id = rs.getString("id");
          LiftExecution execution =
              id == null
                  ? null
                  : new LiftExecution(
                      rs.getInt("web_execution_id"),
                      rs.getDate("performed_on").toLocalDate(),
                      List.of(),
                      rs.getBoolean("warmup"),
                      rs.getBoolean("deload"),
                      rs.getString("execution_notes"));
          rows.add(new PendingRow(HostedPostgresTrainingDataStore.mapLift(rs), id, execution));
        }
        return rows;
      }
    }
  }

  private void loadSets(Map<String, List<ExecutionSet>> sets) throws Exception {
    if (sets.isEmpty()) {
      return;
    }
    String placeholders = String.join(",", Collections.nCopies(sets.size(), "?"));
    try (PreparedStatement statement =
        connection.prepareStatement(
            "SELECT s.execution_id, s.metric_kind, s.metric_a, s.metric_b, s.weight, s.rpe, s.missed "
                + "FROM execution_sets s JOIN executions e ON e.id = s.execution_id "
                + "WHERE e.lifter_profile_id = ? AND s.execution_id IN ("
                + placeholders
                + ") "
                + "ORDER BY s.execution_id, s.set_index")) {
      statement.setString(1, profile);
      int index = 2;
      for (String id : sets.keySet()) {
        statement.setString(index++, id);
      }
      try (ResultSet rs = statement.executeQuery()) {
        while (rs.next()) {
          sets.get(rs.getString("execution_id"))
              .add(HostedPostgresTrainingDataStore.mapExecutionSet(rs));
        }
      }
    }
  }

  private static List<LiftExecutionRow> hydrate(
      List<PendingRow> rows, Map<String, List<ExecutionSet>> sets) {
    return rows.stream()
        .map(
            row -> {
              LiftExecution e = row.execution();
              return new LiftExecutionRow(
                  row.lift(),
                  e == null
                      ? null
                      : new LiftExecution(
                          e.id(), e.date(), sets.get(row.id()), e.warmup(), e.deload(), e.notes()));
            })
        .toList();
  }

  private record PendingRow(Lift lift, String id, LiftExecution execution) {}
}
