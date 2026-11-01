package com.lifttrax.db;

import com.lifttrax.models.ExecutionSet;
import com.lifttrax.models.Lift;
import com.lifttrax.models.LiftExecution;
import com.lifttrax.models.LiftRegion;
import com.lifttrax.models.LiftType;
import com.lifttrax.models.Muscle;
import com.lifttrax.models.SetMetric;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Maps hosted JDBC rows and persists execution-set details. */
final class HostedExecutionJdbc {
  private HostedExecutionJdbc() {}

  static LiftExecution mapExecution(Connection connection, ResultSet rs) throws Exception {
    String executionId = rs.getString("id");
    return new LiftExecution(
        rs.getInt("web_execution_id"),
        parseDate(rs.getObject("performed_on")),
        loadExecutionSets(connection, executionId),
        rs.getBoolean("warmup"),
        rs.getBoolean("deload"),
        rs.getString("notes"));
  }

  private static List<ExecutionSet> loadExecutionSets(Connection connection, String executionId)
      throws Exception {
    try (PreparedStatement statement =
        connection.prepareStatement(
            """
                SELECT metric_kind, metric_a, metric_b, weight, rpe, missed
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

  static ExecutionSet mapExecutionSet(ResultSet rs) throws Exception {
    Integer metricB = rs.getObject("metric_b") == null ? null : rs.getInt("metric_b");
    Float rpe = rs.getObject("rpe") == null ? null : rs.getFloat("rpe");
    return new ExecutionSet(
        metricFromRow(rs.getString("metric_kind"), rs.getInt("metric_a"), metricB),
        normalizeWeight(rs.getString("weight")),
        rpe,
        rs.getBoolean("missed"));
  }

  static void saveExecutionSets(Connection connection, String executionId, List<ExecutionSet> sets)
      throws Exception {
    try (PreparedStatement statement =
        connection.prepareStatement("DELETE FROM execution_sets WHERE execution_id = ?")) {
      statement.setString(1, executionId);
      statement.executeUpdate();
    }
    String sql =
        """
            INSERT INTO execution_sets (
                id, execution_id, set_index, metric_kind, metric_a, metric_b, weight, rpe, missed
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
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
        statement.setBoolean(9, set.missed());
        statement.addBatch();
      }
      statement.executeBatch();
    }
  }

  static Lift mapLift(ResultSet rs) throws Exception {
    return new Lift(
        rs.getString("name"),
        LiftRegion.fromString(rs.getString("region")),
        LiftType.fromDbValue(rs.getString("main_lift")),
        parseMuscles(rs.getString("muscles")),
        rs.getString("notes"));
  }

  static List<Muscle> parseMuscles(String value) {
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

  static String serializeMuscles(List<Muscle> muscles) {
    if (muscles == null || muscles.isEmpty()) {
      return "";
    }
    return String.join(",", muscles.stream().map(Enum::name).toList());
  }

  static LocalDate parseDate(Object value) {
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

  static String normalizeWeight(String weight) {
    return weight == null || weight.isBlank() ? "none" : weight;
  }

  static String newId() {
    return java.util.UUID.randomUUID().toString();
  }

  static String requireUserId(String userId) {
    if (userId == null || userId.isBlank()) {
      throw new IllegalArgumentException("Authenticated user is required.");
    }
    return userId.trim();
  }

  private record MetricRow(String kind, int a, Integer b) {}
}
