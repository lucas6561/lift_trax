package com.lifttrax.db;

import com.lifttrax.models.Lift;
import com.lifttrax.models.LiftExecution;
import com.lifttrax.models.LiftRegion;
import com.lifttrax.models.LiftType;
import com.lifttrax.models.WeightText;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Runs hosted execution-history and catalog lookup queries for one profile. */
final class HostedTrainingQueries {
  private final String appUserId;
  private final String lifterProfileId;

  HostedTrainingQueries(String appUserId, String lifterProfileId) {
    this.appUserId = appUserId;
    this.lifterProfileId = lifterProfileId;
  }

  List<LiftExecutionRow> readSnapshots(
      Connection connection,
      LocalDate start,
      LocalDate end,
      String filterColumn,
      String filterValue)
      throws Exception {
    String sql =
        """
        SELECT e.performed_exercise_name AS name, e.performed_region AS region,
          e.performed_main_lift AS main_lift, e.performed_muscles AS muscles,
          e.performed_catalog_notes AS lift_notes,
          e.web_execution_id, e.id, e.performed_on, e.warmup, e.deload, e.notes
        FROM executions e WHERE e.lifter_profile_id = ? AND e.performed_on BETWEEN ? AND ?
        """
            + (filterColumn == null ? "" : " AND e." + filterColumn + " = ?")
            + " ORDER BY e.performed_on ASC, e.performed_exercise_name ASC, e.web_execution_id ASC";
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, lifterProfileId);
      statement.setObject(2, start);
      statement.setObject(3, end);
      if (filterColumn != null) {
        statement.setString(4, filterValue);
      }
      try (ResultSet rs = statement.executeQuery()) {
        List<LiftExecutionRow> rows = new ArrayList<>();
        while (rs.next()) {
          Lift lift =
              new Lift(
                  rs.getString("name"),
                  LiftRegion.fromString(rs.getString("region")),
                  LiftType.fromDbValue(rs.getString("main_lift")),
                  HostedExecutionJdbc.parseMuscles(rs.getString("muscles")),
                  rs.getString("lift_notes"));
          rows.add(new LiftExecutionRow(lift, HostedExecutionJdbc.mapExecution(connection, rs)));
        }
        return rows;
      }
    }
  }

  LiftExecution getLastExecution(Connection connection, String catalogEntryId) throws Exception {
    try (PreparedStatement statement =
        connection.prepareStatement(
            """
                SELECT web_execution_id, id, performed_on, warmup, deload, notes
                FROM executions
                WHERE lifter_profile_id = ? AND performed_catalog_id = ?
                ORDER BY performed_on DESC, web_execution_id DESC
                LIMIT 1
                """)) {
      statement.setString(1, lifterProfileId);
      statement.setString(2, catalogEntryId);
      try (ResultSet rs = statement.executeQuery()) {
        return rs.next() ? HostedExecutionJdbc.mapExecution(connection, rs) : null;
      }
    }
  }

  Map<Integer, String> collectBestByReps(Connection connection, String catalogEntryId)
      throws Exception {
    String sql =
        """
            SELECT es.metric_a AS reps, es.weight
            FROM execution_sets es
            JOIN executions e ON e.id = es.execution_id
            WHERE e.lifter_profile_id = ? AND e.performed_catalog_id = ?
                AND es.metric_kind = 'reps' AND es.missed = FALSE AND es.metric_a > 0
            """;
    Map<Integer, String> bestByReps = new TreeMap<>();
    Map<Integer, Double> bestWeights = new HashMap<>();
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, lifterProfileId);
      statement.setString(2, catalogEntryId);
      try (ResultSet rs = statement.executeQuery()) {
        while (rs.next()) {
          int reps = rs.getInt("reps");
          String weight = HostedExecutionJdbc.normalizeWeight(rs.getString("weight"));
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

  String lockEnabledCatalogEntry(Connection connection, String name) throws Exception {
    try (PreparedStatement statement =
        connection.prepareStatement(
            "SELECT id, enabled FROM exercise_catalog_entries WHERE owner_user_id = ? AND lifter_profile_id = ? AND name = ? FOR UPDATE")) {
      statement.setString(1, appUserId);
      statement.setString(2, lifterProfileId);
      statement.setString(3, name);
      try (ResultSet rows = statement.executeQuery()) {
        if (!rows.next()) {
          throw new IllegalArgumentException("Lift not found: " + name);
        }
        if (!rows.getBoolean("enabled")) {
          throw new IllegalArgumentException(
              "Lift is disabled: " + name + ". Enable it before logging new executions.");
        }
        return rows.getString("id");
      }
    }
  }

  String findCatalogEntryId(Connection connection, String name) throws Exception {
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

  String findHostedExecutionId(Connection connection, int webExecutionId) throws Exception {
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
}
