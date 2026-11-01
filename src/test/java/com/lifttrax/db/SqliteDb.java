package com.lifttrax.db;

import com.lifttrax.models.Lift;
import com.lifttrax.models.LiftExecution;
import com.lifttrax.models.LiftRegion;
import com.lifttrax.models.LiftType;
import com.lifttrax.models.WeightText;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Test and legacy-import fixture for the retired SQLite runtime implementation. */
public class SqliteDb extends SqliteDbSchema
    implements TrainingDataStore, TrainingDataStoreProvider {
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
  public DashboardSnapshot dashboardSnapshot(LocalDate today) throws Exception {
    return DashboardFixture.load(this, today, latestExecutionsByLift());
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

  @Override
  public void close() throws Exception {
    connection.close();
    logInfo("db.close result=ok");
  }

  @Override
  protected Integer findLiftIdForUser(String ownerUserId, String name) throws Exception {
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

  @Override
  protected LiftExecution getLastExecutionForUser(String ownerUserId, int liftId) throws Exception {
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

  @Override
  protected Map<Integer, String> collectBestByRepsForUser(String ownerUserId, int liftId)
      throws Exception {
    String sql =
        """
                SELECT es.metric_a AS reps, es.weight
                FROM execution_sets es
                JOIN lift_records lr ON lr.id = es.record_id
                WHERE lr.owner_user_id = ? AND lr.lift_id = ? AND es.metric_kind = 'reps' AND es.missed = 0 AND es.metric_a > 0
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
}
