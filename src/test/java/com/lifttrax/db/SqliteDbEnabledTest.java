package com.lifttrax.db;

import com.lifttrax.models.Lift;
import com.lifttrax.models.LiftRegion;
import com.lifttrax.models.LiftType;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteDbEnabledTest {

    @Test
    void createsSchemaFromSharedSqlOnEmptyDatabase() throws Exception {
        Path dbPath = Files.createTempFile("lifttrax-empty", ".db");
        try (SqliteDb db = new SqliteDb(dbPath.toString())) {
            db.addLift("Schema Seed Lift", LiftRegion.UPPER, LiftType.BENCH_PRESS, List.of(), "");
            List<Lift> lifts = db.listLifts();
            assertEquals(1, lifts.size());
            assertEquals("Schema Seed Lift", lifts.get(0).name());
        }
    }

    @Test
    void disabledLiftsAreExcludedFromWaveQueries() throws Exception {
        Path dbPath = Files.createTempFile("lifttrax-enabled", ".db");
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            conn.createStatement().execute("""
                    CREATE TABLE IF NOT EXISTS lifts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL UNIQUE,
                        region TEXT NOT NULL,
                        main_lift TEXT,
                        muscles TEXT NOT NULL,
                        notes TEXT NOT NULL DEFAULT ''
                    )
                    """);
            conn.createStatement().execute("""
                    CREATE TABLE IF NOT EXISTS lift_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        lift_id INTEGER NOT NULL,
                        date TEXT NOT NULL,
                        sets TEXT NOT NULL,
                        warmup INTEGER NOT NULL DEFAULT 0,
                        deload INTEGER NOT NULL DEFAULT 0,
                        notes TEXT NOT NULL DEFAULT '',
                        FOREIGN KEY(lift_id) REFERENCES lifts(id)
                    )
                    """);
        }

        try (SqliteDb db = new SqliteDb(dbPath.toString())) {
            db.addLift("Enabled Bench", LiftRegion.UPPER, LiftType.BENCH_PRESS, List.of(), "");
            db.addLift("Disabled Bench", LiftRegion.UPPER, LiftType.BENCH_PRESS, List.of(), "");
            db.setLiftEnabled("Disabled Bench", false);

            assertTrue(db.isLiftEnabled("Enabled Bench"));
            assertFalse(db.isLiftEnabled("Disabled Bench"));

            List<Lift> allLifts = db.listLifts();
            assertEquals(2, allLifts.size());

            List<Lift> waveLifts = db.liftsByType(LiftType.BENCH_PRESS);
            assertEquals(1, waveLifts.size());
            assertEquals("Enabled Bench", waveLifts.get(0).name());
        }
    }
}
