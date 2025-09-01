package org.lift.trax;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

public class SqliteDb implements Database {
    private final Connection conn;
    private final ObjectMapper mapper = new ObjectMapper();

    public SqliteDb(String path) throws Exception {
        File dbFile = new File(path);
        this.conn = DriverManager.getConnection("jdbc:sqlite:" + path);
        if (!dbFile.exists() || dbFile.length() == 0) {
            initDb();
        }
    }

    private void initDb() throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS lifts (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "name TEXT UNIQUE NOT NULL," +
                    "region TEXT NOT NULL," +
                    "main_lift TEXT," +
                    "muscles TEXT," +
                    "notes TEXT" +
                    ")");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS lift_records (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "lift_id INTEGER NOT NULL," +
                    "date TEXT NOT NULL," +
                    "sets TEXT NOT NULL," +
                    "warmup INTEGER NOT NULL," +
                    "notes TEXT," +
                    "FOREIGN KEY(lift_id) REFERENCES lifts(id)" +
                    ")");
        }
    }

    private List<ExecutionSet> parseSets(String json) throws Exception {
        return mapper.readValue(json, new TypeReference<List<ExecutionSet>>() {});
    }

    private String serializeSets(List<ExecutionSet> sets) throws Exception {
        return mapper.writeValueAsString(sets);
    }

    private LiftExecution mapExecution(ResultSet rs) throws Exception {
        int id = rs.getInt("id");
        LocalDate date = LocalDate.parse(rs.getString("date"));
        List<ExecutionSet> sets = parseSets(rs.getString("sets"));
        boolean warmup = rs.getInt("warmup") != 0;
        String notes = rs.getString("notes");
        return new LiftExecution(id, date, sets, warmup, notes);
    }

    private List<LiftExecution> fetchExecutions(int liftId) throws Exception {
        List<LiftExecution> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, date, sets, warmup, notes FROM lift_records WHERE lift_id = ? ORDER BY date DESC")) {
            ps.setInt(1, liftId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapExecution(rs));
                }
            }
        }
        return list;
    }

    private Lift rowToLift(ResultSet rs) throws Exception {
        int id = rs.getInt("id");
        String name = rs.getString("name");
        LiftRegion region = LiftRegion.valueOf(rs.getString("region"));
        String mainStr = rs.getString("main_lift");
        LiftType main = mainStr == null ? null : LiftType.fromString(mainStr);
        String musclesStr = rs.getString("muscles");
        List<Muscle> muscles = new ArrayList<>();
        if (musclesStr != null && !musclesStr.isEmpty()) {
            for (String m : musclesStr.split(",")) {
                muscles.add(Muscle.fromString(m.trim()));
            }
        }
        String notes = rs.getString("notes");
        Lift lift = new Lift(name, region, main, muscles, notes);
        lift.executions = fetchExecutions(id);
        return lift;
    }

    private List<Lift> loadLifts(String sql, Object... params) throws Exception {
        List<Lift> lifts = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lifts.add(rowToLift(rs));
                }
            }
        }
        return lifts;
    }

    private LiftExecution getLastExecution(int liftId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, date, sets, warmup, notes FROM lift_records WHERE lift_id = ? ORDER BY date DESC LIMIT 1")) {
            ps.setInt(1, liftId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapExecution(rs);
                }
            }
        }
        return null;
    }

    private Map<Integer, Double> collectBestByReps(int liftId) throws Exception {
        Map<Integer, Double> best = new TreeMap<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT sets FROM lift_records WHERE lift_id = ?")) {
            ps.setInt(1, liftId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    List<ExecutionSet> sets = parseSets(rs.getString(1));
                    for (ExecutionSet set : sets) {
                        if (set.reps != null) {
                            int reps = set.reps;
                            double weight = set.weight;
                            best.merge(reps, weight, Math::max);
                        }
                    }
                }
            }
        }
        return best;
    }

    @Override
    public void addLift(String name, LiftRegion region, LiftType main, List<Muscle> muscles, String notes) throws Exception {
        String musclesStr = String.join(",", muscles.stream().map(Muscle::toString).toList());
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO lifts (name, region, main_lift, muscles, notes) VALUES (?,?,?,?,?)")) {
            ps.setString(1, name);
            ps.setString(2, region.toString());
            ps.setString(3, main != null ? main.toString() : null);
            ps.setString(4, musclesStr);
            ps.setString(5, notes);
            ps.executeUpdate();
        }
    }

    @Override
    public void addLiftExecution(String name, LiftExecution execution) throws Exception {
        Integer liftId = null;
        try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM lifts WHERE name = ?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    liftId = rs.getInt(1);
                }
            }
        }
        if (liftId == null) throw new Exception("lift not found");
        String setsJson = serializeSets(execution.sets);
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO lift_records (lift_id, date, sets, warmup, notes) VALUES (?,?,?,?,?)")) {
            ps.setInt(1, liftId);
            ps.setString(2, execution.date.toString());
            ps.setString(3, setsJson);
            ps.setInt(4, execution.warmup ? 1 : 0);
            ps.setString(5, execution.notes);
            ps.executeUpdate();
        }
    }

    @Override
    public void updateLift(String currentName, String newName, LiftRegion region, LiftType main, List<Muscle> muscles, String notes) throws Exception {
        String musclesStr = String.join(",", muscles.stream().map(Muscle::toString).toList());
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE lifts SET name=?, region=?, main_lift=?, muscles=?, notes=? WHERE name=?")) {
            ps.setString(1, newName);
            ps.setString(2, region.toString());
            ps.setString(3, main != null ? main.toString() : null);
            ps.setString(4, musclesStr);
            ps.setString(5, notes);
            ps.setString(6, currentName);
            ps.executeUpdate();
        }
    }

    @Override
    public void deleteLift(String name) throws Exception {
        Integer id = null;
        try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM lifts WHERE name = ?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) id = rs.getInt(1);
            }
        }
        if (id != null) {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM lift_records WHERE lift_id = ?")) {
                ps.setInt(1, id);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM lifts WHERE id = ?")) {
                ps.setInt(1, id);
                ps.executeUpdate();
            }
        }
    }

    @Override
    public void updateLiftExecution(int execId, LiftExecution execution) throws Exception {
        String setsJson = serializeSets(execution.sets);
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE lift_records SET date=?, sets=?, warmup=?, notes=? WHERE id=?")) {
            ps.setString(1, execution.date.toString());
            ps.setString(2, setsJson);
            ps.setInt(3, execution.warmup ? 1 : 0);
            ps.setString(4, execution.notes);
            ps.setInt(5, execId);
            ps.executeUpdate();
        }
    }

    @Override
    public void deleteLiftExecution(int execId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM lift_records WHERE id = ?")) {
            ps.setInt(1, execId);
            ps.executeUpdate();
        }
    }

    @Override
    public LiftStats liftStats(String name) throws Exception {
        int liftId;
        try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM lifts WHERE name = ?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new Exception("lift not found");
                liftId = rs.getInt(1);
            }
        }
        LiftExecution last = getLastExecution(liftId);
        Map<Integer, Double> best = collectBestByReps(liftId);
        return new LiftStats(last, best);
    }

    @Override
    public List<Lift> listLifts(String nameFilter) throws Exception {
        if (nameFilter != null) {
            return loadLifts("SELECT id, name, region, main_lift, muscles, notes FROM lifts WHERE name = ? ORDER BY name", nameFilter);
        }
        return loadLifts("SELECT id, name, region, main_lift, muscles, notes FROM lifts ORDER BY name");
    }

    @Override
    public List<Lift> liftsByType(LiftType liftType) throws Exception {
        return loadLifts("SELECT id, name, region, main_lift, muscles, notes FROM lifts WHERE main_lift = ? ORDER BY name", liftType.toString());
    }

    @Override
    public List<Lift> liftsByRegion(LiftRegion region) throws Exception {
        return loadLifts("SELECT id, name, region, main_lift, muscles, notes FROM lifts WHERE region = ? ORDER BY name", region.toString());
    }

    @Override
    public List<Lift> liftsByRegionAndType(LiftRegion region, LiftType liftType) throws Exception {
        return loadLifts("SELECT id, name, region, main_lift, muscles, notes FROM lifts WHERE region = ? AND main_lift = ? ORDER BY name", region.toString(), liftType.toString());
    }
}
