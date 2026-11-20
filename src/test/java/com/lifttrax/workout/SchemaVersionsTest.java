package com.lifttrax.workout;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SchemaVersionsTest {
  @Test
  void programSchemaCatalogKeepsEveryPublishedSnapshotAvailable() {
    assertEquals(List.of(1, 2, 3), ProgramSchemaVersions.supported());
    assertEquals(3, ProgramSchemaVersions.latest());

    for (int schemaVersion : ProgramSchemaVersions.supported()) {
      assertNotNull(
          SchemaVersionsTest.class.getResource(
              ProgramSchemaVersions.schemaResourcePath(schemaVersion)));
    }
  }

  @Test
  void plannedWorkoutCatalogKeepsEveryPublishedSnapshotAvailable() {
    assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8), PlannedWorkoutSchemaVersions.supported());
    assertEquals(8, PlannedWorkoutSchemaVersions.latest());
    assertTrue(PlannedWorkoutSchemaVersions.supports(1));
    assertTrue(PlannedWorkoutSchemaVersions.supports(8));
    assertFalse(PlannedWorkoutSchemaVersions.supports(0));
    assertFalse(PlannedWorkoutSchemaVersions.supports(99));

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

  @Test
  void schemaCatalogRequiresAtLeastOnePositiveVersion() {
    assertThrows(IllegalArgumentException.class, () -> new SchemaVersionCatalog(Map.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new SchemaVersionCatalog(Map.of(0, "/schema.invalid.json")));
  }

  @Test
  void stableLatestProgramSchemaMatchesCatalogLatestSnapshot() throws IOException {
    assertArrayEquals(
        readResource(ProgramSchemaVersions.schemaResourcePath(ProgramSchemaVersions.latest())),
        readResource(ProgramSchemaVersions.latestSchemaResourcePath()));
  }

  @Test
  void stableLatestPlannedWorkoutSchemaMatchesCatalogLatestSnapshot() throws IOException {
    assertArrayEquals(
        readResource(
            PlannedWorkoutSchemaVersions.schemaResourcePath(PlannedWorkoutSchemaVersions.latest())),
        readResource(PlannedWorkoutSchemaVersions.latestSchemaResourcePath()));
  }

  private static byte[] readResource(String resourcePath) throws IOException {
    try (InputStream stream = SchemaVersionsTest.class.getResourceAsStream(resourcePath)) {
      assertNotNull(stream, resourcePath);
      return stream.readAllBytes();
    }
  }
}
