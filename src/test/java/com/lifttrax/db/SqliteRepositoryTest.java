package com.lifttrax.db;

import com.lifttrax.model.Lift;
import com.lifttrax.model.LiftType;
import com.lifttrax.model.Muscle;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteRepositoryTest {

    @Test
    void initializeMigratesLegacyLiftColumnsAndMuscles() throws Exception {
        Path db = Files.createTempFile("lift-trax-legacy", ".db");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + db);
             Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE lifts (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL UNIQUE,
                    region TEXT NOT NULL,
                    main_lift TEXT,
                    muscles TEXT NOT NULL DEFAULT '',
                    notes TEXT NOT NULL DEFAULT ''
                )
                """);
            statement.execute("""
                INSERT INTO lifts(name, region, main_lift, muscles, notes)
                VALUES ('Bench Press', 'UPPER', 'BENCH_PRESS', 'CHEST,TRICEP', 'legacy')
                """);
        }

        SqliteRepository repository = new SqliteRepository(db.toString());
        repository.initialize();

        List<Lift> lifts = repository.listLifts(null, null, null, EnumSet.noneOf(Muscle.class));
        assertEquals(1, lifts.size());
        Lift lift = lifts.getFirst();
        assertEquals("Bench Press", lift.name());
        assertEquals(LiftType.BENCH_PRESS, lift.mainType());
        assertTrue(lift.muscles().contains(Muscle.CHEST));
        assertTrue(lift.muscles().contains(Muscle.TRICEP));
    }
}
