package com.lifttrax.db;

import com.lifttrax.model.Lift;
import com.lifttrax.model.LiftExecution;
import com.lifttrax.model.LiftRegion;
import com.lifttrax.model.LiftType;
import com.lifttrax.model.Muscle;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class SqliteRepository {
    private final String dbUrl;

    public SqliteRepository(String dbPath) {
        this.dbUrl = "jdbc:sqlite:" + dbPath;
    }

    public void initialize() {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE IF NOT EXISTS lifts (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL UNIQUE,
                    region TEXT NOT NULL,
                    main_type TEXT,
                    notes TEXT NOT NULL DEFAULT ''
                )
                """);
            statement.execute("""
                CREATE TABLE IF NOT EXISTS lift_muscles (
                    lift_id INTEGER NOT NULL,
                    muscle TEXT NOT NULL,
                    PRIMARY KEY(lift_id, muscle),
                    FOREIGN KEY(lift_id) REFERENCES lifts(id) ON DELETE CASCADE
                )
                """);
            statement.execute("""
                CREATE TABLE IF NOT EXISTS lift_executions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    lift_id INTEGER NOT NULL,
                    performed_on TEXT NOT NULL,
                    set_count INTEGER NOT NULL,
                    rep_count INTEGER NOT NULL,
                    weight REAL NOT NULL,
                    rpe REAL,
                    warmup INTEGER NOT NULL DEFAULT 0,
                    deload INTEGER NOT NULL DEFAULT 0,
                    notes TEXT NOT NULL DEFAULT '',
                    FOREIGN KEY(lift_id) REFERENCES lifts(id) ON DELETE CASCADE
                )
                """);

            addColumnIfMissing(connection, "lifts", "region", "TEXT NOT NULL DEFAULT 'FULL_BODY'");
            addColumnIfMissing(connection, "lifts", "main_type", "TEXT");
            addColumnIfMissing(connection, "lifts", "notes", "TEXT NOT NULL DEFAULT ''");

            addColumnIfMissing(connection, "lift_executions", "rpe", "REAL");
            addColumnIfMissing(connection, "lift_executions", "notes", "TEXT NOT NULL DEFAULT ''");
            addColumnIfMissing(connection, "lift_executions", "warmup", "INTEGER NOT NULL DEFAULT 0");
            addColumnIfMissing(connection, "lift_executions", "deload", "INTEGER NOT NULL DEFAULT 0");

            migrateLegacyLiftColumns(connection);
            migrateLegacyMuscles(connection);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize database", e);
        }
    }

    public void addExecution(String exercise, LiftType liftType, Set<Muscle> muscles, LiftExecution execution) {
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try {
                long liftId = findOrCreateLift(connection, exercise, liftType, muscles);
                insertExecution(connection, liftId, execution);
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to add execution", e);
        }
    }

    public List<Lift> listLifts(String exerciseFilter, LiftRegion regionFilter, LiftType typeFilter, Set<Muscle> muscles) {
        String sql = "SELECT id, name, region, main_type, notes FROM lifts ORDER BY name";
        List<Lift> lifts = new ArrayList<>();

        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {

            while (rs.next()) {
                long id = rs.getLong("id");
                String name = rs.getString("name");
                LiftRegion region = parseLiftRegion(rs.getString("region"));
                LiftType mainType = parseLiftType(rs.getString("main_type"));

                if (exerciseFilter != null && !exerciseFilter.isBlank() && !name.toLowerCase().contains(exerciseFilter.trim().toLowerCase())) {
                    continue;
                }
                if (regionFilter != null && region != regionFilter) {
                    continue;
                }
                if (typeFilter != null && mainType != typeFilter) {
                    continue;
                }

                List<Muscle> liftMuscles = getMuscles(connection, id);
                if (!muscles.isEmpty() && liftMuscles.stream().noneMatch(muscles::contains)) {
                    continue;
                }

                List<LiftExecution> recentExecutions = getExecutions(connection, id, 3, null, null);
                lifts.add(new Lift(id, name, region, mainType, liftMuscles, rs.getString("notes"), recentExecutions));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to list lifts", e);
        }

        return lifts;
    }

    public List<LiftExecution> listExecutions(long liftId) {
        try (Connection connection = connect()) {
            return getExecutions(connection, liftId, null, null, null);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to list executions", e);
        }
    }

    public List<LiftExecution> listExecutionsForDateRange(LocalDate start, LocalDate end) {
        String sql = """
            SELECT e.id, e.performed_on, e.set_count, e.rep_count, e.weight, e.rpe, e.warmup, e.deload, e.notes
            FROM lift_executions e
            WHERE e.performed_on BETWEEN ? AND ?
            ORDER BY e.performed_on DESC, e.id DESC
            """;
        List<LiftExecution> out = new ArrayList<>();
        try (Connection connection = connect(); PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, start.toString());
            stmt.setString(2, end.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    out.add(fromExecutionRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to list date-range executions", e);
        }
        return out;
    }

    public void updateLift(long liftId, String name, LiftRegion region, LiftType type, Set<Muscle> muscles, String notes) {
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement stmt = connection.prepareStatement("UPDATE lifts SET name=?, region=?, main_type=?, notes=? WHERE id=?")) {
                    stmt.setString(1, name);
                    stmt.setString(2, region.name());
                    if (type == null) {
                        stmt.setNull(3, java.sql.Types.VARCHAR);
                    } else {
                        stmt.setString(3, type.name());
                    }
                    stmt.setString(4, notes == null ? "" : notes);
                    stmt.setLong(5, liftId);
                    stmt.executeUpdate();
                }
                try (PreparedStatement delete = connection.prepareStatement("DELETE FROM lift_muscles WHERE lift_id=?")) {
                    delete.setLong(1, liftId);
                    delete.executeUpdate();
                }
                saveMuscles(connection, liftId, muscles);
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to update lift", e);
        }
    }

    public void deleteLift(long liftId) {
        try (Connection connection = connect(); PreparedStatement stmt = connection.prepareStatement("DELETE FROM lifts WHERE id=?")) {
            stmt.setLong(1, liftId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to delete lift", e);
        }
    }

    public void deleteExecution(long executionId) {
        try (Connection connection = connect(); PreparedStatement stmt = connection.prepareStatement("DELETE FROM lift_executions WHERE id=?")) {
            stmt.setLong(1, executionId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to delete execution", e);
        }
    }

    private long findOrCreateLift(Connection connection, String name, LiftType liftType, Set<Muscle> muscles) throws SQLException {
        String select = "SELECT id FROM lifts WHERE lower(name)=lower(?)";
        try (PreparedStatement stmt = connection.prepareStatement(select)) {
            stmt.setString(1, name);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }

        LiftRegion region = inferRegion(liftType, muscles);
        String insert = "INSERT INTO lifts(name, region, main_type, notes) VALUES(?, ?, ?, '')";
        try (PreparedStatement stmt = connection.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, name);
            stmt.setString(2, region.name());
            if (liftType == null) {
                stmt.setNull(3, java.sql.Types.VARCHAR);
            } else {
                stmt.setString(3, liftType.name());
            }
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    long newId = keys.getLong(1);
                    saveMuscles(connection, newId, muscles);
                    return newId;
                }
            }
        }

        throw new SQLException("Unable to create lift");
    }

    private void insertExecution(Connection connection, long liftId, LiftExecution execution) throws SQLException {
        String insertExecution = """
            INSERT INTO lift_executions(lift_id, performed_on, set_count, rep_count, weight, rpe, warmup, deload, notes)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement stmt = connection.prepareStatement(insertExecution)) {
            stmt.setLong(1, liftId);
            stmt.setString(2, execution.date().toString());
            stmt.setInt(3, execution.sets());
            stmt.setInt(4, execution.reps());
            stmt.setDouble(5, execution.weight());
            if (execution.rpe() == null) {
                stmt.setNull(6, java.sql.Types.REAL);
            } else {
                stmt.setDouble(6, execution.rpe());
            }
            stmt.setInt(7, execution.warmup() ? 1 : 0);
            stmt.setInt(8, execution.deload() ? 1 : 0);
            stmt.setString(9, execution.notes() == null ? "" : execution.notes());
            stmt.executeUpdate();
        }
    }

    private List<Muscle> getMuscles(Connection connection, long liftId) throws SQLException {
        String sql = "SELECT muscle FROM lift_muscles WHERE lift_id=? ORDER BY muscle";
        List<Muscle> muscles = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, liftId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Muscle parsed = parseMuscle(rs.getString(1));
                    if (parsed != null) {
                        muscles.add(parsed);
                    }
                }
            }
        }
        return muscles;
    }

    private void saveMuscles(Connection connection, long liftId, Set<Muscle> muscles) throws SQLException {
        String sql = "INSERT OR IGNORE INTO lift_muscles(lift_id, muscle) VALUES(?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            for (Muscle muscle : muscles) {
                stmt.setLong(1, liftId);
                stmt.setString(2, muscle.name());
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    private List<LiftExecution> getExecutions(Connection connection, long liftId, Integer limit, LocalDate start, LocalDate end) throws SQLException {
        StringBuilder sql = new StringBuilder("""
            SELECT id, performed_on, set_count, rep_count, weight, rpe, warmup, deload, notes
            FROM lift_executions
            WHERE lift_id=?
            """);
        if (start != null && end != null) {
            sql.append(" AND performed_on BETWEEN ? AND ?");
        }
        sql.append(" ORDER BY performed_on DESC, id DESC");
        if (limit != null) {
            sql.append(" LIMIT ?");
        }

        List<LiftExecution> executions = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql.toString())) {
            int i = 1;
            stmt.setLong(i++, liftId);
            if (start != null && end != null) {
                stmt.setString(i++, start.toString());
                stmt.setString(i++, end.toString());
            }
            if (limit != null) {
                stmt.setInt(i, limit);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    executions.add(fromExecutionRow(rs));
                }
            }
        }
        return executions;
    }

    private LiftExecution fromExecutionRow(ResultSet rs) throws SQLException {
        Double rpe = rs.getObject("rpe") == null ? null : rs.getDouble("rpe");
        return new LiftExecution(
            rs.getLong("id"),
            LocalDate.parse(rs.getString("performed_on")),
            rs.getInt("set_count"),
            rs.getInt("rep_count"),
            rs.getDouble("weight"),
            rpe,
            rs.getInt("warmup") == 1,
            rs.getInt("deload") == 1,
            rs.getString("notes")
        );
    }

    private LiftRegion inferRegion(LiftType liftType, Set<Muscle> muscles) {
        if (liftType == LiftType.SQUAT || liftType == LiftType.DEADLIFT) {
            return LiftRegion.LOWER;
        }
        if (liftType == LiftType.BENCH_PRESS || liftType == LiftType.OVERHEAD_PRESS) {
            return LiftRegion.UPPER;
        }

        Set<Muscle> lowerMuscles = EnumSet.of(Muscle.QUAD, Muscle.HAMSTRING, Muscle.CALF, Muscle.GLUTE);
        if (muscles.stream().anyMatch(lowerMuscles::contains)) {
            return LiftRegion.LOWER;
        }
        Set<Muscle> upperMuscles = EnumSet.of(Muscle.CHEST, Muscle.BICEP, Muscle.TRICEP, Muscle.SHOULDER);
        if (muscles.stream().anyMatch(upperMuscles::contains)) {
            return LiftRegion.UPPER;
        }

        return LiftRegion.FULL_BODY;
    }

    private void migrateLegacyLiftColumns(Connection connection) throws SQLException {
        if (hasColumn(connection, "lifts", "main_lift") && hasColumn(connection, "lifts", "main_type")) {
            try (PreparedStatement select = connection.prepareStatement("SELECT id, main_lift, main_type FROM lifts");
                 ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    long id = rs.getLong("id");
                    String source = rs.getString("main_type");
                    if (source == null || source.isBlank()) {
                        source = rs.getString("main_lift");
                    }
                    LiftType parsed = parseLiftType(source);
                    if (parsed == null) {
                        continue;
                    }
                    try (PreparedStatement update = connection.prepareStatement("UPDATE lifts SET main_type=? WHERE id=?")) {
                        update.setString(1, parsed.name());
                        update.setLong(2, id);
                        update.executeUpdate();
                    }
                }
            }
        }

        if (hasColumn(connection, "lifts", "region")) {
            try (PreparedStatement select = connection.prepareStatement("SELECT id, region FROM lifts");
                 ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    long id = rs.getLong("id");
                    LiftRegion parsed = parseLiftRegion(rs.getString("region"));
                    try (PreparedStatement update = connection.prepareStatement("UPDATE lifts SET region=? WHERE id=?")) {
                        update.setString(1, parsed.name());
                        update.setLong(2, id);
                        update.executeUpdate();
                    }
                }
            }
        }
    }

    private void migrateLegacyMuscles(Connection connection) throws SQLException {
        if (!hasColumn(connection, "lifts", "muscles")) {
            return;
        }
        String sql = "SELECT id, muscles FROM lifts";
        try (PreparedStatement stmt = connection.prepareStatement(sql); ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                long id = rs.getLong("id");
                String muscles = rs.getString("muscles");
                if (muscles == null || muscles.isBlank()) {
                    continue;
                }
                for (String token : muscles.split(",")) {
                    Muscle muscle = parseMuscle(token);
                    if (muscle == null) {
                        continue;
                    }
                    try (PreparedStatement insert = connection.prepareStatement("INSERT OR IGNORE INTO lift_muscles(lift_id, muscle) VALUES(?, ?)")) {
                        insert.setLong(1, id);
                        insert.setString(2, muscle.name());
                        insert.executeUpdate();
                    }
                }
            }
        }
    }

    private void addColumnIfMissing(Connection connection, String table, String column, String columnDef) throws SQLException {
        if (!tableExists(connection, table)) {
            return;
        }
        if (hasColumn(connection, table, column)) {
            return;
        }
        try (Statement alter = connection.createStatement()) {
            alter.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + columnDef);
        }
    }

    private boolean tableExists(Connection connection, String table) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement("SELECT name FROM sqlite_master WHERE type='table' AND name=?")) {
            stmt.setString(1, table);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    private boolean hasColumn(Connection connection, String table, String column) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement("PRAGMA table_info(" + table + ")");
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
            return false;
        }
    }

    private LiftType parseLiftType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = normalizeEnumToken(value);
        try {
            return LiftType.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private LiftRegion parseLiftRegion(String value) {
        if (value == null || value.isBlank()) {
            return LiftRegion.FULL_BODY;
        }
        String normalized = normalizeEnumToken(value);
        try {
            return LiftRegion.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return LiftRegion.FULL_BODY;
        }
    }

    private Muscle parseMuscle(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = normalizeEnumToken(value);
        try {
            return Muscle.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String normalizeEnumToken(String value) {
        return value
            .trim()
            .toUpperCase(Locale.ROOT)
            .replaceAll("[^A-Z0-9]+", "_")
            .replaceAll("_+", "_")
            .replaceAll("^_", "")
            .replaceAll("_$", "");
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(dbUrl);
    }
}
