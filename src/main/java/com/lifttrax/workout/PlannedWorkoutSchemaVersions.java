package com.lifttrax.workout;

import java.util.List;
import java.util.Map;

/**
 * Supported planned-workout snapshots. New exports use latest while old snapshots stay importable.
 */
public final class PlannedWorkoutSchemaVersions {
  private static final String LATEST_SCHEMA_RESOURCE_PATH =
      "/workouts/schema/workout.schema.latest.json";
  private static final SchemaVersionCatalog CATALOG =
      new SchemaVersionCatalog(
          Map.of(
              1, "/workouts/schema/workout.schema.v1.json",
              2, "/workouts/schema/workout.schema.v2.json",
              3, "/workouts/schema/workout.schema.v3.json"));

  private PlannedWorkoutSchemaVersions() {}

  public static int latest() {
    return CATALOG.latest();
  }

  public static boolean supports(int schemaVersion) {
    return CATALOG.supports(schemaVersion);
  }

  public static List<Integer> supported() {
    return CATALOG.supported();
  }

  public static String schemaResourcePath(int schemaVersion) {
    return CATALOG.schemaResourcePath(schemaVersion);
  }

  public static String latestSchemaResourcePath() {
    return LATEST_SCHEMA_RESOURCE_PATH;
  }

  static String unsupportedVersionMessage(int schemaVersion) {
    return CATALOG.unsupportedVersionMessage("workout", schemaVersion);
  }
}
