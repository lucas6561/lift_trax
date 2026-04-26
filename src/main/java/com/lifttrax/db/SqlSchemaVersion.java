package com.lifttrax.db;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Loads the shared schema version used by both Java and Rust implementations.
 */
final class SqlSchemaVersion {
    private static final String VERSION_RESOURCE_PATH = "/sql/schema_version.txt";
    private static final String SCHEMA_RESOURCE_PATH = "/sql/schema.sql";

    private SqlSchemaVersion() {
    }

    static int current() {
        try (InputStream stream = SqlSchemaVersion.class.getResourceAsStream(VERSION_RESOURCE_PATH)) {
            if (stream == null) {
                throw new IllegalStateException("Missing schema version resource: " + VERSION_RESOURCE_PATH);
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
}
