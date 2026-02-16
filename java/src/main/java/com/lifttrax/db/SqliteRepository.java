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
                    notes TEXT NOT NULL DEFAULT '',
                    FOREIGN KEY(lift_id) REFERENCES lifts(id) ON DELETE CASCADE
                )
                """);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize database", e);
        }
    }

    public void addExecution(String exercise, LiftType liftType, Set<Muscle> muscles, LiftExecution execution) {
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try {
                long liftId = findOrCreateLift(connection, exercise, liftType, muscles);
                String insertExecution = """
                    INSERT INTO lift_executions(lift_id, performed_on, set_count, rep_count, weight, rpe, notes)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
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
                    stmt.setString(7, execution.notes() == null ? "" : execution.notes());
                    stmt.executeUpdate();
                }
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

    public List<Lift> listLifts(String exerciseFilter, Set<Muscle> muscles) {
        String sql = "SELECT id, name, region, main_type, notes FROM lifts ORDER BY name";
        List<Lift> lifts = new ArrayList<>();

        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {

            while (rs.next()) {
                long id = rs.getLong("id");
                String name = rs.getString("name");
                if (exerciseFilter != null && !exerciseFilter.isBlank()
                    && !name.equalsIgnoreCase(exerciseFilter.trim())) {
                    continue;
                }

                List<Muscle> liftMuscles = getMuscles(connection, id);
                if (!muscles.isEmpty() && liftMuscles.stream().noneMatch(muscles::contains)) {
                    continue;
                }

                LiftRegion region = LiftRegion.valueOf(rs.getString("region"));
                String main = rs.getString("main_type");
                LiftType mainType = main == null ? null : LiftType.valueOf(main);
                List<LiftExecution> recentExecutions = getRecentExecutions(connection, id, 3);

                lifts.add(new Lift(name, region, mainType, liftMuscles, rs.getString("notes"), recentExecutions));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to list lifts", e);
        }

        return lifts;
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

    private List<Muscle> getMuscles(Connection connection, long liftId) throws SQLException {
        String sql = "SELECT muscle FROM lift_muscles WHERE lift_id=? ORDER BY muscle";
        List<Muscle> muscles = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, liftId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    muscles.add(Muscle.valueOf(rs.getString(1)));
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

    private List<LiftExecution> getRecentExecutions(Connection connection, long liftId, int limit) throws SQLException {
        String sql = """
            SELECT performed_on, set_count, rep_count, weight, rpe, notes
            FROM lift_executions
            WHERE lift_id=?
            ORDER BY performed_on DESC, id DESC
            LIMIT ?
            """;
        List<LiftExecution> executions = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, liftId);
            stmt.setInt(2, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Double rpe = rs.getObject("rpe") == null ? null : rs.getDouble("rpe");
                    executions.add(new LiftExecution(
                        LocalDate.parse(rs.getString("performed_on")),
                        rs.getInt("set_count"),
                        rs.getInt("rep_count"),
                        rs.getDouble("weight"),
                        rpe,
                        rs.getString("notes")
                    ));
                }
            }
        }
        return executions;
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

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(dbUrl);
    }
}
