package com.lifttrax.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

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

  @Test
  void loadsSharedMigrationsInVersionOrder() {
    List<Integer> versions =
        SqlSchemaVersion.migrations().stream().map(SqlSchemaVersion.Migration::version).toList();
    assertEquals(List.of(10, 11, 12), versions);
  }
}
