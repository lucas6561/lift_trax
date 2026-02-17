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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class SqliteDb implements Database, AutoCloseable {
    private final Connection connection;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SqliteDb(String dbPath) throws Exception {
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
    public void updateLift(String currentName, String newName, LiftRegion region, LiftType main, List<Muscle> muscles, String notes) {
        throw notYet("updateLift");
    }

    @Override
    public void deleteLift(String name) {
        throw notYet("deleteLift");
    }

    @Override
    public void updateLiftExecution(int execId, LiftExecution execution) {
        throw notYet("updateLiftExecution");
    }

    @Override
    public void deleteLiftExecution(int execId) {
        throw notYet("deleteLiftExecution");
    }

    @Override
    public LiftStats liftStats(String name) {
        throw notYet("liftStats");
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
