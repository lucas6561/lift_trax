package com.lifttrax.db;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifttrax.models.ExecutionSet;
import com.lifttrax.models.LiftRegion;
import com.lifttrax.models.LiftType;
import com.lifttrax.models.Muscle;
import com.lifttrax.models.SetMetric;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Imports a validated local LiftTrax SQLite database into a hosted account. */
public final class HostedLocalDatabaseImportService {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private HostedLocalDatabaseImportService() {}

  public static ImportPreview preview(Path sourceDatabase) throws Exception {
    DatabaseBackupService.ValidationResult validation =
        DatabaseBackupService.validateLiftTraxDatabase(sourceDatabase);
    try (Connection source = DriverManager.getConnection("jdbc:sqlite:" + validation.dbPath())) {
      return new ImportPreview(
          validation.dbPath(),
          fingerprint(validation.dbPath()),
          validation.schemaVersion(),
          countRows(source, "lifts"),
          countRows(source, "lift_records"));
    }
  }

  public static ImportResult importDatabase(Path sourceDatabase, TrainingDataStore targetStore)
      throws Exception {
    if (!(targetStore instanceof HostedPostgresTrainingDataStore hostedStore)) {
      throw new IllegalArgumentException("Hosted import requires the hosted Postgres data store.");
    }
    ImportPreview preview = preview(sourceDatabase);
    try (Connection target =
            HostedPostgresTrainingDataStoreProvider.openConnection(hostedStore.hostedConfig());
        Connection source = DriverManager.getConnection("jdbc:sqlite:" + preview.source())) {
      target.setAutoCommit(false);
      try {
        String existingImportId = findCompletedImport(target, hostedStore, preview.fingerprint());
        if (existingImportId != null) {
          target.rollback();
          return new ImportResult(preview, existingImportId, true, 0, 0, 0);
        }
        String importId = newId();
        createImport(target, hostedStore, preview, importId, "running");
        SourceData sourceData = readSource(source);
        int insertedLifts = 0;
        int insertedExecutions = 0;
        int skippedExecutions = 0;
        for (SourceLift lift : sourceData.lifts()) {
          CatalogEntry catalogEntry = ensureCatalogEntry(target, hostedStore, lift);
          if (catalogEntry.created()) {
            insertedLifts++;
          }
          for (SourceExecution execution :
              sourceData.executionsByLiftId().getOrDefault(lift.id(), List.of())) {
            if (importRecordExists(
                target, hostedStore, "lift_records", Integer.toString(execution.id()))) {
              skippedExecutions++;
              continue;
            }
            String executionId = insertExecution(target, hostedStore, catalogEntry.id(), execution);
            insertExecutionSets(target, executionId, execution.sets());
            createImportRecord(
                target,
                hostedStore,
                importId,
                "lift_records",
                Integer.toString(execution.id()),
                "executions",
                executionId);
            insertedExecutions++;
          }
        }
        markImportCompleted(target, importId);
        target.commit();
        return new ImportResult(
            preview, importId, false, insertedLifts, insertedExecutions, skippedExecutions);
      } catch (Exception e) {
        target.rollback();
        throw e;
      } finally {
        target.setAutoCommit(true);
      }
    }
  }

  private static SourceData readSource(Connection connection) throws Exception {
    Map<Integer, SourceLift> lifts = new TreeMap<>();
    String ownerUserIdColumn =
        hasColumn(connection, "lifts", "owner_user_id")
            ? "owner_user_id"
            : "'local-user' AS owner_user_id";
    try (PreparedStatement statement =
            connection.prepareStatement(
                """
                    SELECT id, %s, name, region, main_lift, muscles, notes
                    FROM lifts
                    ORDER BY id
                    """
                    .formatted(ownerUserIdColumn));
        ResultSet rs = statement.executeQuery()) {
      while (rs.next()) {
        String name = rs.getString("name");
        String owner = rs.getString("owner_user_id");
        if (owner != null && !owner.isBlank() && !"local-user".equals(owner)) {
          name = name + " (" + owner + ")";
        }
        lifts.put(
            rs.getInt("id"),
            new SourceLift(
                rs.getInt("id"),
                name,
                LiftRegion.fromString(rs.getString("region")),
                LiftType.fromDbValue(rs.getString("main_lift")),
                parseMuscles(rs.getString("muscles")),
                rs.getString("notes")));
      }
    }
    Map<Integer, List<SourceExecution>> executionsByLiftId = new TreeMap<>();
    boolean hasExecutionSets = hasTable(connection, "execution_sets");
    try (PreparedStatement statement =
            connection.prepareStatement(
                """
                    SELECT id, lift_id, date, sets, warmup, deload, notes
                    FROM lift_records
                    ORDER BY id
                    """);
        ResultSet rs = statement.executeQuery()) {
      while (rs.next()) {
        int recordId = rs.getInt("id");
        int liftId = rs.getInt("lift_id");
        List<ExecutionSet> sets =
            hasExecutionSets
                ? readExecutionSets(connection, recordId)
                : parseLegacySets(rs.getString("sets"));
        executionsByLiftId
            .computeIfAbsent(liftId, ignored -> new ArrayList<>())
            .add(
                new SourceExecution(
                    recordId,
                    LocalDate.parse(rs.getString("date")),
                    sets,
                    rs.getInt("warmup") != 0,
                    rs.getInt("deload") != 0,
                    rs.getString("notes")));
      }
    }
    return new SourceData(List.copyOf(lifts.values()), executionsByLiftId);
  }

  private static CatalogEntry ensureCatalogEntry(
      Connection connection, HostedPostgresTrainingDataStore store, SourceLift lift)
      throws Exception {
    try (PreparedStatement statement =
        connection.prepareStatement(
            """
                SELECT id FROM exercise_catalog_entries
                WHERE owner_user_id = ? AND lifter_profile_id = ? AND name = ?
                """)) {
      statement.setString(1, store.ownerAppUserId());
      statement.setString(2, store.defaultLifterProfileId());
      statement.setString(3, lift.name());
      try (ResultSet rs = statement.executeQuery()) {
        if (rs.next()) {
          return new CatalogEntry(rs.getString("id"), false);
        }
      }
    }
    String catalogEntryId = newId();
    try (PreparedStatement statement =
        connection.prepareStatement(
            """
                INSERT INTO exercise_catalog_entries (
                    id, owner_user_id, lifter_profile_id, name, region, main_lift,
                    muscles, notes, enabled
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, TRUE)
                """)) {
      statement.setString(1, catalogEntryId);
      statement.setString(2, store.ownerAppUserId());
      statement.setString(3, store.defaultLifterProfileId());
      statement.setString(4, lift.name());
      statement.setString(5, lift.region().name());
      statement.setString(6, lift.main() == null ? null : lift.main().toDbValue());
      statement.setString(7, serializeMuscles(lift.muscles()));
      statement.setString(8, lift.notes() == null ? "" : lift.notes());
      statement.executeUpdate();
      return new CatalogEntry(catalogEntryId, true);
    }
  }

  private static String insertExecution(
      Connection connection,
      HostedPostgresTrainingDataStore store,
      String catalogEntryId,
      SourceExecution execution)
      throws Exception {
    String executionId = newId();
    try (PreparedStatement statement =
        connection.prepareStatement(
            """
                INSERT INTO executions (
                    id, lifter_profile_id, catalog_entry_id, performed_on, warmup, deload, notes
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """)) {
      statement.setString(1, executionId);
      statement.setString(2, store.defaultLifterProfileId());
      statement.setString(3, catalogEntryId);
      statement.setObject(4, execution.date());
      statement.setBoolean(5, execution.warmup());
      statement.setBoolean(6, execution.deload());
      statement.setString(7, execution.notes() == null ? "" : execution.notes());
      statement.executeUpdate();
      return executionId;
    }
  }

  private static void insertExecutionSets(
      Connection connection, String executionId, List<ExecutionSet> sets) throws Exception {
    try (PreparedStatement statement =
        connection.prepareStatement(
            """
                INSERT INTO execution_sets (
                    id, execution_id, set_index, metric_kind, metric_a, metric_b, weight, rpe
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
      for (int i = 0; i < sets.size(); i++) {
        ExecutionSet set = sets.get(i);
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

  private static boolean importRecordExists(
      Connection connection, HostedPostgresTrainingDataStore store, String table, String sourceId)
      throws Exception {
    try (PreparedStatement statement =
        connection.prepareStatement(
            """
                SELECT id FROM local_import_records
                WHERE target_lifter_profile_id = ? AND source_table = ? AND source_id = ?
                """)) {
      statement.setString(1, store.defaultLifterProfileId());
      statement.setString(2, table);
      statement.setString(3, sourceId);
      try (ResultSet rs = statement.executeQuery()) {
        return rs.next();
      }
    }
  }

  private static String findCompletedImport(
      Connection connection, HostedPostgresTrainingDataStore store, String sourceFingerprint)
      throws Exception {
    try (PreparedStatement statement =
        connection.prepareStatement(
            """
                SELECT id FROM local_imports
                WHERE target_lifter_profile_id = ? AND source_fingerprint = ? AND status = 'completed'
                """)) {
      statement.setString(1, store.defaultLifterProfileId());
      statement.setString(2, sourceFingerprint);
      try (ResultSet rs = statement.executeQuery()) {
        return rs.next() ? rs.getString("id") : null;
      }
    }
  }

  private static void createImport(
      Connection connection,
      HostedPostgresTrainingDataStore store,
      ImportPreview preview,
      String importId,
      String status)
      throws Exception {
    try (PreparedStatement statement =
        connection.prepareStatement(
            """
                INSERT INTO local_imports (
                    id, target_app_user_id, target_lifter_profile_id, source_kind,
                    source_fingerprint, source_schema_version, lift_count, execution_count, status
                )
                VALUES (?, ?, ?, 'sqlite_db', ?, ?, ?, ?, ?)
                """)) {
      statement.setString(1, importId);
      statement.setString(2, store.ownerAppUserId());
      statement.setString(3, store.defaultLifterProfileId());
      statement.setString(4, preview.fingerprint());
      statement.setInt(5, preview.schemaVersion());
      statement.setInt(6, preview.liftCount());
      statement.setInt(7, preview.executionCount());
      statement.setString(8, status);
      statement.executeUpdate();
    }
  }

  private static void markImportCompleted(Connection connection, String importId) throws Exception {
    try (PreparedStatement statement =
        connection.prepareStatement("UPDATE local_imports SET status = 'completed' WHERE id = ?")) {
      statement.setString(1, importId);
      statement.executeUpdate();
    }
  }

  private static void createImportRecord(
      Connection connection,
      HostedPostgresTrainingDataStore store,
      String importId,
      String sourceTable,
      String sourceId,
      String targetTable,
      String targetId)
      throws Exception {
    try (PreparedStatement statement =
        connection.prepareStatement(
            """
                INSERT INTO local_import_records (
                    id, target_lifter_profile_id, import_id, source_table, source_id,
                    target_table, target_id
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """)) {
      statement.setString(1, newId());
      statement.setString(2, store.defaultLifterProfileId());
      statement.setString(3, importId);
      statement.setString(4, sourceTable);
      statement.setString(5, sourceId);
      statement.setString(6, targetTable);
      statement.setString(7, targetId);
      statement.executeUpdate();
    }
  }

  private static int countRows(Connection connection, String table) throws Exception {
    try (Statement statement = connection.createStatement();
        ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
      return rs.next() ? rs.getInt(1) : 0;
    }
  }

  private static boolean hasTable(Connection connection, String table) throws Exception {
    try (PreparedStatement statement =
        connection.prepareStatement(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?"); ) {
      statement.setString(1, table);
      try (ResultSet rs = statement.executeQuery()) {
        return rs.next();
      }
    }
  }

  private static boolean hasColumn(Connection connection, String table, String column)
      throws Exception {
    try (ResultSet columns = connection.getMetaData().getColumns(null, null, table, column)) {
      return columns.next();
    }
  }

  private static List<ExecutionSet> readExecutionSets(Connection connection, int recordId)
      throws Exception {
    try (PreparedStatement statement =
        connection.prepareStatement(
            """
                    SELECT metric_kind, metric_a, metric_b, weight, rpe
                    FROM execution_sets
                    WHERE record_id = ?
                    ORDER BY set_index
                    """); ) {
      statement.setInt(1, recordId);
      try (ResultSet rs = statement.executeQuery()) {
        List<ExecutionSet> sets = new ArrayList<>();
        while (rs.next()) {
          Integer metricB = rs.getObject("metric_b") == null ? null : rs.getInt("metric_b");
          Float rpe = rs.getObject("rpe") == null ? null : rs.getFloat("rpe");
          sets.add(
              new ExecutionSet(
                  metricFromRow(rs.getString("metric_kind"), rs.getInt("metric_a"), metricB),
                  normalizeWeight(rs.getString("weight")),
                  rpe));
        }
        return sets;
      }
    }
  }

  private static List<ExecutionSet> parseLegacySets(String setsJson) throws Exception {
    if (setsJson == null || setsJson.isBlank()) {
      return List.of();
    }
    JsonNode array = OBJECT_MAPPER.readTree(setsJson);
    List<ExecutionSet> sets = new ArrayList<>();
    for (JsonNode node : array) {
      SetMetric metric =
          node.has("metric")
              ? parseMetric(node.get("metric"))
              : new SetMetric.Reps(node.path("reps").asInt(0));
      Float rpe =
          node.has("rpe") && !node.get("rpe").isNull() ? (float) node.get("rpe").asDouble() : null;
      sets.add(new ExecutionSet(metric, normalizeWeight(node.path("weight").asText("none")), rpe));
    }
    return sets;
  }

  private static SetMetric parseMetric(JsonNode metric) {
    String type = metric.path("type").asText("reps");
    return switch (type) {
      case "reps-lr" ->
          new SetMetric.RepsLr(metric.path("left").asInt(0), metric.path("right").asInt(0));
      case "reps-range" ->
          new SetMetric.RepsRange(metric.path("min").asInt(0), metric.path("max").asInt(0));
      case "time" -> new SetMetric.TimeSecs(metric.path("seconds").asInt(0));
      case "distance" -> new SetMetric.DistanceFeet(metric.path("feet").asInt(0));
      default -> new SetMetric.Reps(metric.path("reps").asInt(0));
    };
  }

  private static SetMetric metricFromRow(String kind, int a, Integer b) {
    return switch (kind) {
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

  private static List<Muscle> parseMuscles(String value) {
    if (value == null || value.isBlank()) {
      return List.of();
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

  private static String fingerprint(Path source) throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(source)));
  }

  private static String normalizeWeight(String weight) {
    return weight == null || weight.isBlank() ? "none" : weight;
  }

  private static String newId() {
    return java.util.UUID.randomUUID().toString();
  }

  public record ImportPreview(
      Path source, String fingerprint, int schemaVersion, int liftCount, int executionCount) {}

  public record ImportResult(
      ImportPreview preview,
      String importId,
      boolean duplicate,
      int insertedLifts,
      int insertedExecutions,
      int skippedExecutions) {}

  private record SourceData(
      List<SourceLift> lifts, Map<Integer, List<SourceExecution>> executionsByLiftId) {}

  private record SourceLift(
      int id, String name, LiftRegion region, LiftType main, List<Muscle> muscles, String notes) {}

  private record SourceExecution(
      int id,
      LocalDate date,
      List<ExecutionSet> sets,
      boolean warmup,
      boolean deload,
      String notes) {}

  private record CatalogEntry(String id, boolean created) {}

  private record MetricRow(String kind, int a, Integer b) {}
}
