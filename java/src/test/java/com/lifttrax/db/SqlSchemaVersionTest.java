package com.lifttrax.db;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlSchemaVersionTest {

    @Test
    void loadsSharedSchemaVersion() {
        int version = SqlSchemaVersion.current();
        assertTrue(version > 0);
    }

    @Test
    void loadsSharedSchemaSql() {
        String sql = SqlSchemaVersion.schemaSql();
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS lifts"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS lift_records"));
    }
}
