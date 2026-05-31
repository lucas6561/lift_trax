package com.lifttrax.workout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class SchemaVersionsTest {
  @Test
  void programSchemaCatalogKeepsEveryPublishedSnapshotAvailable() {
    assertEquals(List.of(1, 2), ProgramSchemaVersions.supported());
    assertEquals(2, ProgramSchemaVersions.latest());

    for (int schemaVersion : ProgramSchemaVersions.supported()) {
      assertNotNull(
          SchemaVersionsTest.class.getResource(
              ProgramSchemaVersions.schemaResourcePath(schemaVersion)));
    }
  }

  @Test
  void plannedWorkoutCatalogKeepsEveryPublishedSnapshotAvailable() {
    assertEquals(List.of(1, 2), PlannedWorkoutSchemaVersions.supported());
    assertEquals(2, PlannedWorkoutSchemaVersions.latest());

    for (int schemaVersion : PlannedWorkoutSchemaVersions.supported()) {
      assertNotNull(
          SchemaVersionsTest.class.getResource(
              PlannedWorkoutSchemaVersions.schemaResourcePath(schemaVersion)));
    }
  }

  @Test
  void catalogsRejectUnknownSchemaSnapshots() {
    assertThrows(
        IllegalArgumentException.class, () -> ProgramSchemaVersions.schemaResourcePath(99));
    assertThrows(
        IllegalArgumentException.class, () -> PlannedWorkoutSchemaVersions.schemaResourcePath(99));
  }
}
