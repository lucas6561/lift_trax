package com.lifttrax.db;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;

/** Loads the shared schema version used by both Java and Rust implementations. */
final class SqlSchemaVersion {
  private static final String VERSION_RESOURCE_PATH = "/sql/schema_version.txt";
  private static final String SCHEMA_RESOURCE_PATH = "/sql/schema.sql";
  private static final String MIGRATION_INDEX_RESOURCE_PATH = "/sql/migrations/index.txt";

  private SqlSchemaVersion() {}

  record Migration(int version, String name, String sql) {}

  static int current() {
    try (InputStream stream = SqlSchemaVersion.class.getResourceAsStream(VERSION_RESOURCE_PATH)) {
      if (stream == null) {
        throw new IllegalStateException(
            "Missing schema version resource: " + VERSION_RESOURCE_PATH);
      }
      String value = new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();
      return Integer.parseInt(value);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to load shared schema version", e);
    }
  }

  static String schemaSql() {
    try (InputStream stream = SqlSchemaVersion.class.getResourceAsStream(SCHEMA_RESOURCE_PATH)) {
      if (stream == null) {
        throw new IllegalStateException("Missing schema SQL resource: " + SCHEMA_RESOURCE_PATH);
      }
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to load shared schema SQL", e);
    }
  }

  static List<Migration> migrations() {
    String index = readResource(MIGRATION_INDEX_RESOURCE_PATH, "migration index");
    List<Migration> migrations =
        index
            .lines()
            .map(String::trim)
            .filter(line -> !line.isEmpty() && !line.startsWith("#"))
            .map(SqlSchemaVersion::loadMigration)
            .sorted(Comparator.comparingInt(Migration::version))
            .toList();
    validateMigrations(migrations);
    return migrations;
  }

  private static Migration loadMigration(String name) {
    int separator = name.indexOf("__");
    if (separator <= 0) {
      throw new IllegalStateException("Invalid schema migration filename: " + name);
    }
    try {
      int version = Integer.parseInt(name.substring(0, separator));
      String path = "/sql/migrations/" + name;
      return new Migration(version, name, readResource(path, "schema migration"));
    } catch (NumberFormatException e) {
      throw new IllegalStateException("Invalid schema migration filename: " + name, e);
    }
  }

  private static void validateMigrations(List<Migration> migrations) {
    if (migrations.isEmpty()) {
      throw new IllegalStateException("No schema migrations found");
    }
    int previousVersion = 0;
    for (Migration migration : migrations) {
      if (migration.version() <= previousVersion) {
        throw new IllegalStateException(
            "Duplicate schema migration version: " + migration.version());
      }
      previousVersion = migration.version();
    }
    if (previousVersion != current()) {
      throw new IllegalStateException(
          "Latest schema migration does not match current schema version: " + previousVersion);
    }
  }

  private static String readResource(String path, String label) {
    try (InputStream stream = SqlSchemaVersion.class.getResourceAsStream(path)) {
      if (stream == null) {
        throw new IllegalStateException("Missing " + label + " resource: " + path);
      }
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to load " + label + ": " + path, e);
    }
  }
}
