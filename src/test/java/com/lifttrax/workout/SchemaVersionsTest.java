package com.lifttrax.workout;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.InputStream;
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
    assertEquals(List.of(1, 2, 3), PlannedWorkoutSchemaVersions.supported());
    assertEquals(3, PlannedWorkoutSchemaVersions.latest());

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
