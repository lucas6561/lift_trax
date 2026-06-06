package com.lifttrax.workout;

import java.util.List;
import java.util.Map;

/**
 * Supported program-schema snapshots. Existing entries must remain loadable after new versions
 * ship.
 */
public final class ProgramSchemaVersions {
  private static final String LATEST_SCHEMA_RESOURCE_PATH =
      "/programs/schema/program.schema.latest.json";
  private static final SchemaVersionCatalog CATALOG =
      new SchemaVersionCatalog(
          Map.of(
              1, "/programs/schema/program.schema.v1.json",
              2, "/programs/schema/program.schema.v2.json"));

  private ProgramSchemaVersions() {}

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
    return CATALOG.unsupportedVersionMessage("program", schemaVersion);
  }
}
