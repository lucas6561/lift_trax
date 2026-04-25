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
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Core SqliteDb component used by LiftTrax.
 */

public class SqliteDb implements Database, AutoCloseable {
    public static final int MAX_BACKUPS = 5;

    private final Connection connection;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SqliteDb(String dbPath) throws Exception {
        createBackupIfExists(dbPath);
        this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        ensureBaseSchema();
        ensureLiftEnabledColumn();
        ensureExecutionSetsTable();
        backfillExecutionSetsFromLegacyJson();
        ensureUserVersion();
    }

    @Override
    public List<Lift> listLifts() throws Exception {
        String sql = "SELECT name, region, main_lift, muscles, notes FROM lifts ORDER BY name";
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            List<Lift> lifts = new ArrayList<>();
            while (rs.next()) {
                lifts.add(mapLift(rs));
            }
            return lifts;
        }
    }

    @Override
    public Lift getLift(String name) throws Exception {
        String sql = "SELECT name, region, main_lift, muscles, notes FROM lifts WHERE name = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("Lift not found: " + name);
                }
                return mapLift(rs);
            }
        }
    }

    @Override
    public List<LiftExecution> getExecutions(String liftName) throws Exception {
        String sql = """
                SELECT lr.id, lr.date, lr.sets, lr.warmup, lr.deload, lr.notes
                FROM lift_records lr
                JOIN lifts l ON l.id = lr.lift_id
                WHERE l.name = ?
                ORDER BY lr.date DESC
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, liftName);
            try (ResultSet rs = statement.executeQuery()) {
                List<LiftExecution> executions = new ArrayList<>();
                while (rs.next()) {
                    int recordId = rs.getInt("id");
                    executions.add(new LiftExecution(
                            recordId,
                            LocalDate.parse(rs.getString("date")),
                            loadExecutionSets(recordId, rs.getString("sets")),
                            rs.getInt("warmup") != 0,
                            rs.getInt("deload") != 0,
                            rs.getString("notes")
                    ));
                }
                return executions;
            }
        }
    }

    private Lift mapLift(ResultSet rs) throws Exception {
        String muscles = rs.getString("muscles");
        List<Muscle> muscleList = parseMuscles(muscles);
        return new Lift(
                rs.getString("name"),
                LiftRegion.fromString(rs.getString("region")),
                LiftType.fromDbValue(rs.getString("main_lift")),
                muscleList,
                rs.getString("notes")
        );
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
            try (PreparedStatement statement = connection.prepareStatement(
                    "ALTER TABLE lifts ADD COLUMN enabled INTEGER NOT NULL DEFAULT 1")) {
                statement.executeUpdate();
            }
        }
    }

    private void ensureExecutionSetsTable() throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
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
        try (PreparedStatement statement = connection.prepareStatement("""
                CREATE UNIQUE INDEX IF NOT EXISTS idx_execution_sets_record_index
                ON execution_sets(record_id, set_index)
                """)) {
            statement.executeUpdate();
        }
    }

    private void backfillExecutionSetsFromLegacyJson() throws Exception {
        String findSql = """
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

    private void ensureBaseSchema() throws Exception {
        String schemaSql = SqlSchemaVersion.schemaSql();
        try (var statement = connection.createStatement()) {
            for (String chunk : schemaSql.split(";")) {
                String sql = chunk.trim();
                if (!sql.isEmpty()) {
                    statement.execute(sql);
                }
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

    private void ensureUserVersion() throws Exception {
        int expected = SqlSchemaVersion.current();
        int current;
        try (PreparedStatement statement = connection.prepareStatement("PRAGMA user_version");
             ResultSet rs = statement.executeQuery()) {
            current = rs.next() ? rs.getInt(1) : 0;
        }
        if (current != expected) {
            try (PreparedStatement statement = connection.prepareStatement("PRAGMA user_version = " + expected)) {
                statement.execute();
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
            Float rpe = node.has("rpe") && !node.get("rpe").isNull() ? (float) node.get("rpe").asDouble() : null;
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

    private List<ExecutionSet> loadExecutionSets(int recordId, String fallbackSetsJson) throws Exception {
        String sql = """
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
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM execution_sets WHERE record_id = ?")) {
            statement.setInt(1, recordId);
            statement.executeUpdate();
        }
        String insert = """
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
    }

    private Integer findLiftId(String name) throws Exception {
        String sql = "SELECT id FROM lifts WHERE name = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
                return null;
            }
        }
    }

    private LiftExecution getLastExecution(int liftId) throws Exception {
        String sql = """
                SELECT id, date, sets, warmup, deload, notes
                FROM lift_records
                WHERE lift_id = ?
                ORDER BY date DESC
                LIMIT 1
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, liftId);
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
                        rs.getString("notes")
                );
            }
        }
    }

    private double weightToLbs(String weight) {
        if (weight == null) {
            return 0.0;
        }
        String trimmed = weight.trim().toLowerCase();
        if (trimmed.isEmpty() || "none".equals(trimmed)) {
            return 0.0;
        }
        if (trimmed.contains("|")) {
            String[] parts = trimmed.split("\\|", 2);
            try {
                return parseSide(parts[0]) + parseSide(parts[1]);
            } catch (IllegalArgumentException e) {
                return 0.0;
            }
        }
        if (trimmed.contains("+")) {
            String[] parts = trimmed.split("\\+");
            try {
                double raw = parseSide(parts[0]);
                if (parts.length == 2 && parts[1].trim().endsWith("c")) {
                    String chain = parts[1].trim().substring(0, parts[1].trim().length() - 1);
                    return raw + parseSide(chain);
                }
                return raw;
            } catch (IllegalArgumentException e) {
                return 0.0;
            }
        }
        try {
            return parseSide(trimmed);
        } catch (IllegalArgumentException e) {
            return 0.0;
        }
    }

    private double parseSide(String value) {
        String trimmed = value.trim();
        if (trimmed.endsWith("kg")) {
            String stripped = trimmed.substring(0, trimmed.length() - 2).trim();
            return Double.parseDouble(stripped) * 2.20462;
        }
        if (trimmed.endsWith("lb")) {
            String stripped = trimmed.substring(0, trimmed.length() - 2).trim();
            return Double.parseDouble(stripped);
        }
        return Double.parseDouble(trimmed);
    }

    private Map<Integer, String> collectBestByReps(int liftId) throws Exception {
        String sql = """
                SELECT es.metric_a AS reps, es.weight
                FROM execution_sets es
                JOIN lift_records lr ON lr.id = es.record_id
                WHERE lr.lift_id = ? AND es.metric_kind = 'reps'
                """;
        Map<Integer, String> bestByReps = new TreeMap<>();
        Map<Integer, Double> bestByRepsWeight = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, liftId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    int repCount = rs.getInt("reps");
                    String weight = rs.getString("weight");
                    if (weight == null || weight.isBlank()) {
                        weight = "none";
                    }
                    double lbs = weightToLbs(weight);
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

        String timestamp = ZonedDateTime.now(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
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
    public void addLift(String name, LiftRegion region, LiftType main, List<Muscle> muscles, String notes) throws Exception {
        String sql = "INSERT INTO lifts (name, region, main_lift, muscles, notes) VALUES (?, ?, ?, ?, ?)";
        String musclesValue = muscles == null || muscles.isEmpty()
                ? ""
                : muscles.stream().map(Enum::name).reduce((a, b) -> a + "," + b).orElse("");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            statement.setString(2, region.name());
            statement.setString(3, main == null ? null : main.toDbValue());
            statement.setString(4, musclesValue);
            statement.setString(5, notes == null ? "" : notes);
            statement.executeUpdate();
        }
    }

    @Override
    public void addLiftExecution(String name, LiftExecution execution) throws Exception {
        Integer liftId = findLiftId(name);
        if (liftId == null) {
            throw new IllegalArgumentException("Lift not found: " + name);
        }
        String setsJson = objectMapper.writeValueAsString(execution.sets());
        String sql = "INSERT INTO lift_records (lift_id, date, sets, warmup, deload, notes) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, liftId);
            statement.setString(2, execution.date().toString());
            statement.setString(3, setsJson);
            statement.setInt(4, execution.warmup() ? 1 : 0);
            statement.setInt(5, execution.deload() ? 1 : 0);
            statement.setString(6, execution.notes() == null ? "" : execution.notes());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    saveExecutionSets(keys.getInt(1), execution.sets());
                }
            }
        }
    }

    @Override
    public void updateLift(String currentName, String newName, LiftRegion region, LiftType main, List<Muscle> muscles, String notes) throws Exception {
        String sql = "UPDATE lifts SET name = ?, region = ?, main_lift = ?, muscles = ?, notes = ? WHERE name = ?";
        String musclesValue = muscles == null || muscles.isEmpty()
                ? ""
                : muscles.stream().map(Enum::name).reduce((a, b) -> a + "," + b).orElse("");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, newName);
            statement.setString(2, region.name());
            statement.setString(3, main == null ? null : main.toDbValue());
            statement.setString(4, musclesValue);
            statement.setString(5, notes == null ? "" : notes);
            statement.setString(6, currentName);
            statement.executeUpdate();
        }
    }

    @Override
    public void deleteLift(String name) throws Exception {
        Integer liftId = findLiftId(name);
        if (liftId == null) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM lift_records WHERE lift_id = ?")) {
            statement.setInt(1, liftId);
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM lifts WHERE id = ?")) {
            statement.setInt(1, liftId);
            statement.executeUpdate();
        }
    }

    @Override
    public void setLiftEnabled(String name, boolean enabled) throws Exception {
        String sql = "UPDATE lifts SET enabled = ? WHERE name = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, enabled ? 1 : 0);
            statement.setString(2, name);
            statement.executeUpdate();
        }
    }

    @Override
    public boolean isLiftEnabled(String name) throws Exception {
        String sql = "SELECT enabled FROM lifts WHERE name = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("Lift not found: " + name);
                }
                return rs.getInt("enabled") != 0;
            }
        }
    }

    @Override
    public void updateLiftExecution(int execId, LiftExecution execution) throws Exception {
        String setsJson = objectMapper.writeValueAsString(execution.sets());
        String sql = "UPDATE lift_records SET date = ?, sets = ?, warmup = ?, deload = ?, notes = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, execution.date().toString());
            statement.setString(2, setsJson);
            statement.setInt(3, execution.warmup() ? 1 : 0);
            statement.setInt(4, execution.deload() ? 1 : 0);
            statement.setString(5, execution.notes() == null ? "" : execution.notes());
            statement.setInt(6, execId);
            statement.executeUpdate();
        }
        saveExecutionSets(execId, execution.sets());
    }

    @Override
    public void deleteLiftExecution(int execId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM execution_sets WHERE record_id = ?")) {
            statement.setInt(1, execId);
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM lift_records WHERE id = ?")) {
            statement.setInt(1, execId);
            statement.executeUpdate();
        }
    }

    @Override
    public LiftStats liftStats(String name) throws Exception {
        Integer liftId = findLiftId(name);
        if (liftId == null) {
            throw new IllegalArgumentException("Lift not found: " + name);
        }
        LiftExecution last = getLastExecution(liftId);
        Map<Integer, String> bestByReps = collectBestByReps(liftId);
        return new LiftStats(last, bestByReps);
    }

    @Override
    public List<Lift> liftsByType(LiftType liftType) throws Exception {
        String sql = "SELECT name, region, main_lift, muscles, notes FROM lifts WHERE main_lift = ? AND enabled = 1 ORDER BY name";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, liftType.toDbValue());
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
    public List<Lift> getAccessoriesByMuscle(Muscle muscle) throws Exception {
        String sql = "SELECT name, region, main_lift, muscles, notes FROM lifts WHERE main_lift = ? AND enabled = 1 AND muscles LIKE ? ORDER BY name";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, LiftType.ACCESSORY.toDbValue());
            statement.setString(2, "%" + muscle.name() + "%");
            try (ResultSet rs = statement.executeQuery()) {
                List<Lift> lifts = new ArrayList<>();
                while (rs.next()) {
                    Lift lift = mapLift(rs);
                    if (lift.muscles().contains(muscle)) {
                        lifts.add(lift);
                    }
                }
                return lifts;
            }
        }
    }

    @Override
    public List<Lift> liftsByRegionAndType(LiftRegion region, LiftType liftType) throws Exception {
        String sql = "SELECT name, region, main_lift, muscles, notes FROM lifts WHERE region = ? AND main_lift = ? AND enabled = 1 ORDER BY name";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, region.name());
            statement.setString(2, liftType.toDbValue());
            try (ResultSet rs = statement.executeQuery()) {
                List<Lift> lifts = new ArrayList<>();
                while (rs.next()) {
                    lifts.add(mapLift(rs));
                }
                return lifts;
            }
        }
    }
}
