package com.lifttrax.db;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifttrax.models.ExecutionSet;
import com.lifttrax.models.Lift;
import com.lifttrax.models.LiftExecution;
import com.lifttrax.models.LiftRegion;
import com.lifttrax.models.LiftStats;
import com.lifttrax.models.LiftType;
import com.lifttrax.models.Muscle;
import com.lifttrax.models.SetMetric;
import com.lifttrax.models.WeightText;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Test and legacy-import fixture for the retired SQLite runtime implementation. */
public class SqliteDb implements TrainingDataStore, TrainingDataStoreProvider {
  public static final int MAX_BACKUPS = 5;
  public static final String LEGACY_OWNER_USER_ID = "local-user";
  private static final Logger LOGGER = LoggerFactory.getLogger(SqliteDb.class);

  private final Connection connection;
  private final ObjectMapper objectMapper = new ObjectMapper();

  private static void logInfo(String message, Object... args) {
    if (LOGGER.isInfoEnabled()) {
      LOGGER.info(message, args);
    }
  }

  public SqliteDb(String dbPath) throws Exception {
    Path dbFile = Paths.get(dbPath);
    Path parent = dbFile.getParent();
    if (parent != null && !parent.toString().isBlank()) {
      Files.createDirectories(parent);
    }
    createBackupIfExists(dbPath);
    this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
    ensureLiftEnabledColumn();
    SqlSchemaMigrator.migrate(connection);
    ensureLiftEnabledColumn();
    ensureExecutionSetsTable();
    backfillExecutionSetsFromLegacyJson();
    logInfo(
        "db.open path={} schemaVersion={} result=ok",
        dbPath,
        SqlSchemaMigrator.activeVersion(connection));
  }

  public int schemaVersion() throws Exception {
    return SqlSchemaMigrator.activeVersion(connection);
  }

  @Override
  public TrainingDataStore forUser(String ownerUserId) {
    return new UserScopedDatabase(this, requireOwnerUserId(ownerUserId));
  }

  @Override
  public List<Lift> listLifts() throws Exception {
    return listLiftsForUser(LEGACY_OWNER_USER_ID);
  }

  List<Lift> listLiftsForUser(String ownerUserId) throws Exception {
    String sql =
        "SELECT name, region, main_lift, muscles, notes FROM lifts WHERE owner_user_id = ? ORDER BY name";
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, requireOwnerUserId(ownerUserId));
      try (ResultSet rs = statement.executeQuery()) {
        List<Lift> lifts = new ArrayList<>();
        while (rs.next()) {
          lifts.add(mapLift(rs));
        }
        logInfo("db.listLifts result=ok");
        return lifts;
      }
    }
  }

  @Override
  public Lift getLift(String name) throws Exception {
    return getLiftForUser(LEGACY_OWNER_USER_ID, name);
  }

  Lift getLiftForUser(String ownerUserId, String name) throws Exception {
    String sql =
        """
                SELECT name, region, main_lift, muscles, notes
                FROM lifts
                WHERE owner_user_id = ? AND name = ?
                """;
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, requireOwnerUserId(ownerUserId));
      statement.setString(2, name);
      try (ResultSet rs = statement.executeQuery()) {
        if (!rs.next()) {
          logInfo("db.getLift name={} result=not_found", name);
          throw new IllegalArgumentException("Lift not found: " + name);
        }
        Lift lift = mapLift(rs);
        logInfo(
            "db.getLift name={} result=ok region={} main={}",
            name,
            lift.region(),
            lift.main() == null ? "" : lift.main().toDbValue());
        return lift;
      }
    }
  }

  @Override
  public List<LiftExecution> getExecutions(String liftName) throws Exception {
    return getExecutionsForUser(LEGACY_OWNER_USER_ID, liftName);
  }

  List<LiftExecution> getExecutionsForUser(String ownerUserId, String liftName) throws Exception {
    String sql =
        """
                SELECT lr.id, lr.date, lr.sets, lr.warmup, lr.deload, lr.notes
                FROM lift_records lr
                JOIN lifts l ON l.id = lr.lift_id
                WHERE lr.owner_user_id = ? AND l.owner_user_id = ? AND l.name = ?
                ORDER BY lr.date DESC
                """;
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      String owner = requireOwnerUserId(ownerUserId);
      statement.setString(1, owner);
      statement.setString(2, owner);
      statement.setString(3, liftName);
      try (ResultSet rs = statement.executeQuery()) {
        List<LiftExecution> executions = new ArrayList<>();
        while (rs.next()) {
          int recordId = rs.getInt("id");
          executions.add(
              new LiftExecution(
                  recordId,
                  LocalDate.parse(rs.getString("date")),
                  loadExecutionSets(recordId, rs.getString("sets")),
                  rs.getInt("warmup") != 0,
                  rs.getInt("deload") != 0,
                  rs.getString("notes")));
        }
        logInfo("db.getExecutions lift={} result=ok", liftName);
        return executions;
      }
    }
  }

  @Override
  public List<LiftExecutionRow> getExecutionsBetween(LocalDate start, LocalDate end)
      throws Exception {
    return getExecutionsBetweenForUser(LEGACY_OWNER_USER_ID, start, end);
  }

  List<LiftExecutionRow> getExecutionsBetweenForUser(
      String ownerUserId, LocalDate start, LocalDate end) throws Exception {
    String sql =
        """
                SELECT
                    l.name, l.region, l.main_lift, l.muscles, l.notes AS lift_notes,
                    lr.id, lr.date, lr.sets, lr.warmup, lr.deload, lr.notes AS record_notes
                FROM lift_records lr
                JOIN lifts l ON l.id = lr.lift_id
                WHERE lr.owner_user_id = ? AND l.owner_user_id = ? AND lr.date BETWEEN ? AND ?
                ORDER BY lr.date ASC, l.name ASC, lr.id ASC
                """;
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      String owner = requireOwnerUserId(ownerUserId);
      statement.setString(1, owner);
      statement.setString(2, owner);
      statement.setString(3, start.toString());
      statement.setString(4, end.toString());
      try (ResultSet rs = statement.executeQuery()) {
        List<LiftExecutionRow> rows = new ArrayList<>();
        while (rs.next()) {
          int recordId = rs.getInt("id");
          Lift lift =
              new Lift(
                  rs.getString("name"),
                  LiftRegion.fromString(rs.getString("region")),
                  LiftType.fromDbValue(rs.getString("main_lift")),
                  parseMuscles(rs.getString("muscles")),
                  rs.getString("lift_notes"));
          LiftExecution execution =
              new LiftExecution(
                  recordId,
                  LocalDate.parse(rs.getString("date")),
                  loadExecutionSets(recordId, rs.getString("sets")),
                  rs.getInt("warmup") != 0,
                  rs.getInt("deload") != 0,
                  rs.getString("record_notes"));
          rows.add(new LiftExecutionRow(lift, execution));
        }
        logInfo("db.getExecutionsBetween start={} end={} result=ok", start, end);
        return rows;
      }
    }
  }

  @Override
  public ExecutionHistorySummary executionHistorySummary(LocalDate start, LocalDate end)
      throws Exception {
    return executionHistorySummaryForUser(LEGACY_OWNER_USER_ID, start, end);
  }

  ExecutionHistorySummary executionHistorySummaryForUser(
      String ownerUserId, LocalDate start, LocalDate end) throws Exception {
    String sql =
        """
                SELECT
                    COUNT(*) AS record_count,
                    MIN(date) AS min_date,
                    MAX(date) AS max_date,
                    MAX(CASE WHEN date < ? THEN date END) AS nearest_before,
                    MIN(CASE WHEN date > ? THEN date END) AS nearest_after
                FROM lift_records
                WHERE owner_user_id = ?
                """;
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, start.toString());
      statement.setString(2, end.toString());
      statement.setString(3, requireOwnerUserId(ownerUserId));
      try (ResultSet rs = statement.executeQuery()) {
        if (!rs.next()) {
          logInfo("db.executionHistorySummary start={} end={} result=empty", start, end);
          return new ExecutionHistorySummary(0, null, null, null, null);
        }
        ExecutionHistorySummary summary =
            new ExecutionHistorySummary(
                rs.getInt("record_count"),
                parseNullableDate(rs.getString("min_date")),
                parseNullableDate(rs.getString("max_date")),
                parseNullableDate(rs.getString("nearest_before")),
                parseNullableDate(rs.getString("nearest_after")));
        logInfo("db.executionHistorySummary start={} end={} result=ok", start, end);
        return summary;
      }
    }
  }

  @Override
  public LiftExecution getLastExecution(String liftName, boolean warmup, boolean deload)
      throws Exception {
    return getLastExecutionForUser(LEGACY_OWNER_USER_ID, liftName, warmup, deload);
  }

  LiftExecution getLastExecutionForUser(
      String ownerUserId, String liftName, boolean warmup, boolean deload) throws Exception {
    String sql =
        """
                SELECT lr.id, lr.date, lr.sets, lr.warmup, lr.deload, lr.notes
                FROM lift_records lr
                JOIN lifts l ON l.id = lr.lift_id
                WHERE lr.owner_user_id = ?
                    AND l.owner_user_id = ?
                    AND l.name = ?
                    AND lr.warmup = ?
                    AND lr.deload = ?
                ORDER BY lr.date DESC, lr.id DESC
                LIMIT 1
                """;
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      String owner = requireOwnerUserId(ownerUserId);
      statement.setString(1, owner);
      statement.setString(2, owner);
      statement.setString(3, liftName);
      statement.setInt(4, warmup ? 1 : 0);
      statement.setInt(5, deload ? 1 : 0);
      try (ResultSet rs = statement.executeQuery()) {
        if (!rs.next()) {
          logInfo(
              "db.getLastExecution lift={} warmup={} deload={} result=none",
              liftName,
              warmup,
              deload);
          return null;
        }
        LiftExecution execution = mapExecution(rs);
        logInfo(
            "db.getLastExecution lift={} warmup={} deload={} result=id:{}",
            liftName,
            warmup,
            deload,
            execution.id());
        return execution;
      }
    }
  }

  @Override
  public LiftExecution getExecution(String liftName, int executionId) throws Exception {
    return getExecutionForUser(LEGACY_OWNER_USER_ID, liftName, executionId);
  }

  LiftExecution getExecutionForUser(String ownerUserId, String liftName, int executionId)
      throws Exception {
    String sql =
        """
                SELECT lr.id, lr.date, lr.sets, lr.warmup, lr.deload, lr.notes
                FROM lift_records lr
                JOIN lifts l ON l.id = lr.lift_id
                WHERE lr.owner_user_id = ? AND l.owner_user_id = ? AND l.name = ? AND lr.id = ?
                """;
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      String owner = requireOwnerUserId(ownerUserId);
      statement.setString(1, owner);
      statement.setString(2, owner);
      statement.setString(3, liftName);
      statement.setInt(4, executionId);
      try (ResultSet rs = statement.executeQuery()) {
        if (!rs.next()) {
          logInfo("db.getExecution lift={} id={} result=not_found", liftName, executionId);
          return null;
        }
        LiftExecution execution = mapExecution(rs);
        logInfo("db.getExecution lift={} id={} result=ok", liftName, executionId);
        return execution;
      }
    }
  }

  @Override
  public Map<String, LiftExecution> latestExecutionsByLift() throws Exception {
    return latestExecutionsByLiftForUser(LEGACY_OWNER_USER_ID);
  }

  Map<String, LiftExecution> latestExecutionsByLiftForUser(String ownerUserId) throws Exception {
    String sql =
        """
                SELECT l.name, lr.id, lr.date, lr.sets, lr.warmup, lr.deload, lr.notes
                FROM lifts l
                JOIN lift_records lr ON lr.lift_id = l.id
                WHERE l.owner_user_id = ? AND lr.owner_user_id = ? AND lr.id = (
                    SELECT lr2.id
                    FROM lift_records lr2
                    WHERE lr2.owner_user_id = ? AND lr2.lift_id = l.id
                    ORDER BY lr2.date DESC, lr2.id DESC
                    LIMIT 1
                )
                ORDER BY l.name
                """;
    Map<String, LiftExecution> latestByLift = new HashMap<>();
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      String owner = requireOwnerUserId(ownerUserId);
      statement.setString(1, owner);
      statement.setString(2, owner);
      statement.setString(3, owner);
      try (ResultSet rs = statement.executeQuery()) {
        while (rs.next()) {
          latestByLift.put(rs.getString("name"), mapExecution(rs));
        }
      }
    }
    logInfo("db.latestExecutionsByLift result=rows:{}", latestByLift.size());
    return latestByLift;
  }

  private LiftExecution mapExecution(ResultSet rs) throws Exception {
    int recordId = rs.getInt("id");
    return new LiftExecution(
        recordId,
        LocalDate.parse(rs.getString("date")),
        loadExecutionSets(recordId, rs.getString("sets")),
        rs.getInt("warmup") != 0,
        rs.getInt("deload") != 0,
        rs.getString("notes"));
  }

  private static LocalDate parseNullableDate(String value) {
    return value == null || value.isBlank() ? null : LocalDate.parse(value);
  }

  private static String requireOwnerUserId(String ownerUserId) {
    if (ownerUserId == null || ownerUserId.isBlank()) {
      throw new IllegalArgumentException("Authenticated user is required.");
    }
    return ownerUserId;
  }

  private Lift mapLift(ResultSet rs) throws Exception {
    String muscles = rs.getString("muscles");
    List<Muscle> muscleList = parseMuscles(muscles);
    return new Lift(
        rs.getString("name"),
        LiftRegion.fromString(rs.getString("region")),
        LiftType.fromDbValue(rs.getString("main_lift")),
        muscleList,
        rs.getString("notes"));
  }

  private void ensureLiftEnabledColumn() throws Exception {
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

  private void ensureExecutionSetsTable() throws Exception {
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

  private void backfillExecutionSetsFromLegacyJson() throws Exception {
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

  private List<Muscle> parseMuscles(String muscles) {
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
      sets.add(new ExecutionSet(metric, weight, rpe));
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

  private List<ExecutionSet> loadExecutionSets(int recordId, String fallbackSetsJson)
      throws Exception {
    String sql =
        """
                SELECT metric_kind, metric_a, metric_b, weight, rpe
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
          sets.add(new ExecutionSet(metricFromRow(metricKind, metricA, metricB), weight, rpe));
        }
      }
    }
    if (!sets.isEmpty()) {
      return sets;
    }
    return parseSets(fallbackSetsJson);
  }

  private void saveExecutionSets(int recordId, List<ExecutionSet> sets) throws Exception {
    try (PreparedStatement statement =
        connection.prepareStatement("DELETE FROM execution_sets WHERE record_id = ?")) {
      statement.setInt(1, recordId);
      statement.executeUpdate();
    }
    String insert =
        """
                INSERT INTO execution_sets (record_id, set_index, metric_kind, metric_a, metric_b, weight, rpe)
                VALUES (?, ?, ?, ?, ?, ?, ?)
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
        statement.addBatch();
      }
      statement.executeBatch();
    }
  }

  private SetMetric parseMetric(JsonNode metric) {
    if (metric.has("Reps")) {
      JsonNode reps = metric.get("Reps");
      return new SetMetric.Reps(readIntValue(reps, "reps"));
    }
    if (metric.has("RepsLr")) {
      JsonNode lr = metric.get("RepsLr");
      return new SetMetric.RepsLr(readIntValue(lr, "left"), readIntValue(lr, "right"));
    }
    if (metric.has("RepsRange")) {
      JsonNode range = metric.get("RepsRange");
      return new SetMetric.RepsRange(readIntValue(range, "min"), readIntValue(range, "max"));
    }
    if (metric.has("TimeSecs")) {
      JsonNode seconds = metric.get("TimeSecs");
      return new SetMetric.TimeSecs(readIntValue(seconds, "seconds"));
    }
    if (metric.has("DistanceFeet")) {
      JsonNode feet = metric.get("DistanceFeet");
      return new SetMetric.DistanceFeet(readIntValue(feet, "feet"));
    }
    if (metric.has("reps")) {
      return new SetMetric.Reps(metric.path("reps").asInt());
    }
    if (metric.has("left") || metric.has("right")) {
      return new SetMetric.RepsLr(metric.path("left").asInt(), metric.path("right").asInt());
    }
    if (metric.has("min") || metric.has("max")) {
      return new SetMetric.RepsRange(metric.path("min").asInt(), metric.path("max").asInt());
    }
    if (metric.has("seconds")) {
      return new SetMetric.TimeSecs(metric.path("seconds").asInt());
    }
    if (metric.has("feet")) {
      return new SetMetric.DistanceFeet(metric.path("feet").asInt());
    }
    return new SetMetric.Reps(0);
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

  @Override
  public void close() throws Exception {
    connection.close();
    logInfo("db.close result=ok");
  }

  private Integer findLiftIdForUser(String ownerUserId, String name) throws Exception {
    String sql = "SELECT id FROM lifts WHERE owner_user_id = ? AND name = ?";
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, requireOwnerUserId(ownerUserId));
      statement.setString(2, name);
      try (ResultSet rs = statement.executeQuery()) {
        if (rs.next()) {
          return rs.getInt("id");
        }
        return null;
      }
    }
  }

  private LiftExecution getLastExecutionForUser(String ownerUserId, int liftId) throws Exception {
    String sql =
        """
                SELECT id, date, sets, warmup, deload, notes
                FROM lift_records
                WHERE owner_user_id = ? AND lift_id = ?
                ORDER BY date DESC
                LIMIT 1
                """;
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, requireOwnerUserId(ownerUserId));
      statement.setInt(2, liftId);
      try (ResultSet rs = statement.executeQuery()) {
        if (!rs.next()) {
          return null;
        }
        int recordId = rs.getInt("id");
        return new LiftExecution(
            recordId,
            LocalDate.parse(rs.getString("date")),
            loadExecutionSets(recordId, rs.getString("sets")),
            rs.getInt("warmup") != 0,
            rs.getInt("deload") != 0,
            rs.getString("notes"));
      }
    }
  }

  private Map<Integer, String> collectBestByRepsForUser(String ownerUserId, int liftId)
      throws Exception {
    String sql =
        """
                SELECT es.metric_a AS reps, es.weight
                FROM execution_sets es
                JOIN lift_records lr ON lr.id = es.record_id
                WHERE lr.owner_user_id = ? AND lr.lift_id = ? AND es.metric_kind = 'reps'
                """;
    Map<Integer, String> bestByReps = new TreeMap<>();
    Map<Integer, Double> bestByRepsWeight = new HashMap<>();
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, requireOwnerUserId(ownerUserId));
      statement.setInt(2, liftId);
      try (ResultSet rs = statement.executeQuery()) {
        while (rs.next()) {
          int repCount = rs.getInt("reps");
          String weight = rs.getString("weight");
          if (weight == null || weight.isBlank()) {
            weight = "none";
          }
          double lbs = WeightText.toPounds(weight);
          Double currentBest = bestByRepsWeight.get(repCount);
          if (currentBest == null || lbs > currentBest) {
            bestByRepsWeight.put(repCount, lbs);
            bestByReps.put(repCount, weight);
          }
        }
      }
    }
    return bestByReps;
  }

  private static void createBackupIfExists(String dbPath) throws Exception {
    Path dbFile = Paths.get(dbPath);
    if (!Files.exists(dbFile)) {
      return;
    }

    String timestamp =
        ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    Path backupPath = Paths.get(dbPath + ".backup-" + timestamp);
    Files.copy(dbFile, backupPath, StandardCopyOption.COPY_ATTRIBUTES);

    Path dir = dbFile.getParent();
    if (dir == null || dir.toString().isBlank()) {
      dir = Paths.get(".");
    }
    String prefix = dbFile.getFileName().toString() + ".backup-";
    List<Path> backups = new ArrayList<>();
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
      for (Path entry : stream) {
        String name = entry.getFileName().toString();
        if (name.startsWith(prefix)) {
          backups.add(entry);
        }
      }
    }

    backups.sort((a, b) -> a.getFileName().toString().compareTo(b.getFileName().toString()));
    while (backups.size() > MAX_BACKUPS) {
      Path oldest = backups.remove(0);
      Files.deleteIfExists(oldest);
    }
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
