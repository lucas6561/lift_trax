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

public class SqliteDb implements Database, AutoCloseable {
    public static final int MAX_BACKUPS = 5;

    private final Connection connection;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SqliteDb(String dbPath) throws Exception {
        createBackupIfExists(dbPath);
        this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
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
                    executions.add(new LiftExecution(
                            rs.getInt("id"),
                            LocalDate.parse(rs.getString("date")),
                            parseSets(rs.getString("sets")),
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

    private SetMetric parseMetric(JsonNode metric) {
        if (metric.has("Reps")) {
            return new SetMetric.Reps(metric.get("Reps").asInt());
        }
        if (metric.has("RepsLr")) {
            JsonNode lr = metric.get("RepsLr");
            return new SetMetric.RepsLr(lr.path("left").asInt(), lr.path("right").asInt());
        }
        if (metric.has("RepsRange")) {
            JsonNode range = metric.get("RepsRange");
            return new SetMetric.RepsRange(range.path("min").asInt(), range.path("max").asInt());
        }
        if (metric.has("TimeSecs")) {
            return new SetMetric.TimeSecs(metric.get("TimeSecs").asInt());
        }
        if (metric.has("DistanceFeet")) {
            return new SetMetric.DistanceFeet(metric.get("DistanceFeet").asInt());
        }
        return new SetMetric.Reps(0);
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

    private UnsupportedOperationException notYet(String methodName) {
        return new UnsupportedOperationException(methodName + " is not implemented yet in the Java port");
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
                return new LiftExecution(
                        rs.getInt("id"),
                        LocalDate.parse(rs.getString("date")),
                        parseSets(rs.getString("sets")),
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
        String sql = "SELECT sets FROM lift_records WHERE lift_id = ?";
        Map<Integer, String> bestByReps = new TreeMap<>();
        Map<Integer, Double> bestByRepsWeight = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, liftId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    List<ExecutionSet> sets = parseSets(rs.getString("sets"));
                    for (ExecutionSet set : sets) {
                        if (set.metric() instanceof SetMetric.Reps reps) {
                            int repCount = reps.reps();
                            String weight = set.weight() == null || set.weight().isBlank()
                                    ? "none"
                                    : set.weight();
                            double lbs = weightToLbs(weight);
                            Double currentBest = bestByRepsWeight.get(repCount);
                            if (currentBest == null || lbs > currentBest) {
                                bestByRepsWeight.put(repCount, lbs);
                                bestByReps.put(repCount, weight);
                            }
                        }
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
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, liftId);
            statement.setString(2, execution.date().toString());
            statement.setString(3, setsJson);
            statement.setInt(4, execution.warmup() ? 1 : 0);
            statement.setInt(5, execution.deload() ? 1 : 0);
            statement.setString(6, execution.notes() == null ? "" : execution.notes());
            statement.executeUpdate();
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
    }

    @Override
    public void deleteLiftExecution(int execId) throws Exception {
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
        String sql = "SELECT name, region, main_lift, muscles, notes FROM lifts WHERE main_lift = ? ORDER BY name";
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
        String sql = "SELECT name, region, main_lift, muscles, notes FROM lifts WHERE main_lift = ? AND muscles LIKE ? ORDER BY name";
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
        String sql = "SELECT name, region, main_lift, muscles, notes FROM lifts WHERE region = ? AND main_lift = ? ORDER BY name";
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
