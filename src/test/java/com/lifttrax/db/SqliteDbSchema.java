package com.lifttrax.db;

import com.fasterxml.jackson.databind.JsonNode;
import com.lifttrax.models.ExecutionSet;
import com.lifttrax.models.Lift;
import com.lifttrax.models.LiftRegion;
import com.lifttrax.models.LiftType;
import com.lifttrax.models.Muscle;
import com.lifttrax.models.SetMetric;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Schema compatibility and set serialization for the SQLite test fixture. */
abstract class SqliteDbSchema extends SqliteDbWriter {
  @Override
  protected Lift mapLift(ResultSet rs) throws Exception {
    String muscles = rs.getString("muscles");
    List<Muscle> muscleList = parseMuscles(muscles);
    return new Lift(
        rs.getString("name"),
        LiftRegion.fromString(rs.getString("region")),
        LiftType.fromDbValue(rs.getString("main_lift")),
        muscleList,
        rs.getString("notes"));
  }

  protected final void ensureLiftEnabledColumn() throws Exception {
    if (!tableExists("lifts")) {
      return;
    }
    boolean hasEnabled = false;
    try (PreparedStatement statement = connection.prepareStatement("PRAGMA table_info(lifts)");
        ResultSet rs = statement.executeQuery()) {
      while (rs.next()) {
        if ("enabled".equalsIgnoreCase(rs.getString("name"))) {
          hasEnabled = true;
          break;
        }
      }
    }
    if (!hasEnabled) {
      try (PreparedStatement statement =
          connection.prepareStatement(
              "ALTER TABLE lifts ADD COLUMN enabled INTEGER NOT NULL DEFAULT 1")) {
        statement.executeUpdate();
      }
    }
  }

  protected final void ensureExecutionSetsTable() throws Exception {
    try (PreparedStatement statement =
        connection.prepareStatement(
            """
                CREATE TABLE IF NOT EXISTS execution_sets (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    record_id INTEGER NOT NULL,
                    set_index INTEGER NOT NULL,
                    metric_kind TEXT NOT NULL,
                    metric_a INTEGER NOT NULL DEFAULT 0,
                    metric_b INTEGER,
                    weight TEXT NOT NULL DEFAULT 'none',
                    rpe REAL,
                    missed INTEGER NOT NULL DEFAULT 0,
                    FOREIGN KEY(record_id) REFERENCES lift_records(id) ON DELETE CASCADE
                )
                """)) {
      statement.executeUpdate();
    }
    try (PreparedStatement statement =
        connection.prepareStatement(
            """
                CREATE UNIQUE INDEX IF NOT EXISTS idx_execution_sets_record_index
                ON execution_sets(record_id, set_index)
                """)) {
      statement.executeUpdate();
    }
  }

  protected final void backfillExecutionSetsFromLegacyJson() throws Exception {
    String findSql =
        """
                SELECT lr.id, lr.sets
                FROM lift_records lr
                LEFT JOIN execution_sets es ON es.record_id = lr.id
                WHERE es.id IS NULL
                """;
    try (PreparedStatement statement = connection.prepareStatement(findSql);
        ResultSet rs = statement.executeQuery()) {
      while (rs.next()) {
        saveExecutionSets(rs.getInt("id"), parseSets(rs.getString("sets")));
      }
    }
  }

  private boolean tableExists(String tableName) throws Exception {
    String sql = "SELECT name FROM sqlite_master WHERE type='table' AND name = ?";
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, tableName);
      try (ResultSet rs = statement.executeQuery()) {
        return rs.next();
      }
    }
  }

  protected final List<Muscle> parseMuscles(String muscles) {
    if (muscles == null || muscles.isBlank()) {
      return Collections.emptyList();
    }
    return Arrays.stream(muscles.split(","))
        .filter(value -> !value.isBlank())
        .map(Muscle::fromString)
        .toList();
  }

  private List<ExecutionSet> parseSets(String setsJson) throws Exception {
    if (setsJson == null || setsJson.isBlank()) {
      return Collections.emptyList();
    }
    JsonNode array = objectMapper.readTree(setsJson);
    List<ExecutionSet> sets = new ArrayList<>();
    for (JsonNode node : array) {
      SetMetric metric;
      if (node.has("metric")) {
        metric = parseMetric(node.get("metric"));
      } else {
        metric = new SetMetric.Reps(node.path("reps").asInt(0));
      }
      Float rpe =
          node.has("rpe") && !node.get("rpe").isNull() ? (float) node.get("rpe").asDouble() : null;
      String weight = node.path("weight").asText("none");
      sets.add(new ExecutionSet(metric, weight, rpe, node.path("missed").asBoolean(false)));
    }
    return sets;
  }

  private SetMetric metricFromRow(String kind, int a, Integer b) {
    return switch (kind) {
      case "reps" -> new SetMetric.Reps(a);
      case "reps_lr" -> new SetMetric.RepsLr(a, b == null ? 0 : b);
      case "reps_range" -> new SetMetric.RepsRange(a, b == null ? 0 : b);
      case "time_secs" -> new SetMetric.TimeSecs(a);
      case "distance_feet" -> new SetMetric.DistanceFeet(a);
      default -> new SetMetric.Reps(a);
    };
  }

  private String metricKind(SetMetric metric) {
    if (metric instanceof SetMetric.Reps) {
      return "reps";
    }
    if (metric instanceof SetMetric.RepsLr) {
      return "reps_lr";
    }
    if (metric instanceof SetMetric.RepsRange) {
      return "reps_range";
    }
    if (metric instanceof SetMetric.TimeSecs) {
      return "time_secs";
    }
    if (metric instanceof SetMetric.DistanceFeet) {
      return "distance_feet";
    }
    return "reps";
  }

  private int metricA(SetMetric metric) {
    if (metric instanceof SetMetric.Reps reps) {
      return reps.reps();
    }
    if (metric instanceof SetMetric.RepsLr repsLr) {
      return repsLr.left();
    }
    if (metric instanceof SetMetric.RepsRange repsRange) {
      return repsRange.min();
    }
    if (metric instanceof SetMetric.TimeSecs secs) {
      return secs.seconds();
    }
    if (metric instanceof SetMetric.DistanceFeet feet) {
      return feet.feet();
    }
    return 0;
  }

  private Integer metricB(SetMetric metric) {
    if (metric instanceof SetMetric.RepsLr repsLr) {
      return repsLr.right();
    }
    if (metric instanceof SetMetric.RepsRange repsRange) {
      return repsRange.max();
    }
    return null;
  }

  protected final List<ExecutionSet> loadExecutionSets(int recordId, String fallbackSetsJson)
      throws Exception {
    String sql =
        """
                SELECT metric_kind, metric_a, metric_b, weight, rpe, missed
                FROM execution_sets
                WHERE record_id = ?
                ORDER BY set_index
                """;
    List<ExecutionSet> sets = new ArrayList<>();
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setInt(1, recordId);
      try (ResultSet rs = statement.executeQuery()) {
        while (rs.next()) {
          String metricKind = rs.getString("metric_kind");
          int metricA = rs.getInt("metric_a");
          Integer metricB = rs.getObject("metric_b") == null ? null : rs.getInt("metric_b");
          String weight = rs.getString("weight");
          Float rpe = rs.getObject("rpe") == null ? null : rs.getFloat("rpe");
          sets.add(
              new ExecutionSet(
                  metricFromRow(metricKind, metricA, metricB),
                  weight,
                  rpe,
                  rs.getBoolean("missed")));
        }
      }
    }
    if (!sets.isEmpty()) {
      return sets;
    }
    return parseSets(fallbackSetsJson);
  }

  @Override
  protected void saveExecutionSets(int recordId, List<ExecutionSet> sets) throws Exception {
    try (PreparedStatement statement =
        connection.prepareStatement("DELETE FROM execution_sets WHERE record_id = ?")) {
      statement.setInt(1, recordId);
      statement.executeUpdate();
    }
    String insert =
        """
                INSERT INTO execution_sets (record_id, set_index, metric_kind, metric_a, metric_b, weight, rpe, missed)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
    try (PreparedStatement statement = connection.prepareStatement(insert)) {
      for (int i = 0; i < sets.size(); i++) {
        ExecutionSet set = sets.get(i);
        statement.setInt(1, recordId);
        statement.setInt(2, i);
        statement.setString(3, metricKind(set.metric()));
        statement.setInt(4, metricA(set.metric()));
        Integer metricB = metricB(set.metric());
        if (metricB == null) {
          statement.setNull(5, java.sql.Types.INTEGER);
        } else {
          statement.setInt(5, metricB);
        }
        String weight = set.weight() == null || set.weight().isBlank() ? "none" : set.weight();
        statement.setString(6, weight);
        if (set.rpe() == null) {
          statement.setNull(7, java.sql.Types.REAL);
        } else {
          statement.setFloat(7, set.rpe());
        }
        statement.setBoolean(8, set.missed());
        statement.addBatch();
      }
      statement.executeBatch();
    }
  }

  private SetMetric parseMetric(JsonNode metric) {
    String type =
        List.of("Reps", "RepsLr", "RepsRange", "TimeSecs", "DistanceFeet").stream()
            .filter(metric::has)
            .findFirst()
            .orElseGet(() -> inferredMetricType(metric));
    JsonNode value = metric.has(type) ? metric.path(type) : metric;
    return switch (type) {
      case "Reps", "reps" -> new SetMetric.Reps(readIntValue(value, "reps"));
      case "RepsLr", "left-right" ->
          new SetMetric.RepsLr(readIntValue(value, "left"), readIntValue(value, "right"));
      case "RepsRange", "range" ->
          new SetMetric.RepsRange(readIntValue(value, "min"), readIntValue(value, "max"));
      case "TimeSecs", "seconds" -> new SetMetric.TimeSecs(readIntValue(value, "seconds"));
      case "DistanceFeet", "feet" -> new SetMetric.DistanceFeet(readIntValue(value, "feet"));
      default -> new SetMetric.Reps(0);
    };
  }

  private static String inferredMetricType(JsonNode metric) {
    if (metric.has("reps")) {
      return "reps";
    }
    if (metric.has("left") || metric.has("right")) {
      return "left-right";
    }
    if (metric.has("min") || metric.has("max")) {
      return "range";
    }
    if (metric.has("seconds")) {
      return "seconds";
    }
    return metric.has("feet") ? "feet" : "unknown";
  }

  private int readIntValue(JsonNode node, String field) {
    if (node == null || node.isNull()) {
      return 0;
    }
    if (node.isInt() || node.isLong()) {
      return node.asInt();
    }
    return node.path(field).asInt();
  }
}
