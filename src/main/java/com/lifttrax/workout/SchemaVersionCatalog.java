package com.lifttrax.workout;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/** Immutable list of the schema snapshots that remain loadable for a document type. */
final class SchemaVersionCatalog {
  private final NavigableMap<Integer, String> schemaResources;

  SchemaVersionCatalog(Map<Integer, String> schemaResources) {
    if (schemaResources.isEmpty()) {
      throw new IllegalArgumentException("At least one schema version is required");
    }

    NavigableMap<Integer, String> resources = new TreeMap<>(schemaResources);
    if (resources.firstKey() < 1) {
      throw new IllegalArgumentException("Schema versions must be positive");
    }
    this.schemaResources = Collections.unmodifiableNavigableMap(resources);
  }

  int latest() {
    return schemaResources.lastKey();
  }

  boolean supports(int schemaVersion) {
    return schemaResources.containsKey(schemaVersion);
  }

  List<Integer> supported() {
    return List.copyOf(schemaResources.navigableKeySet());
  }

  String schemaResourcePath(int schemaVersion) {
    String resourcePath = schemaResources.get(schemaVersion);
    if (resourcePath == null) {
      throw new IllegalArgumentException("Unsupported schemaVersion " + schemaVersion);
    }
    return resourcePath;
  }

  String unsupportedVersionMessage(String documentType, int schemaVersion) {
    return "Unsupported "
        + documentType
        + " schemaVersion "
        + schemaVersion
        + "; supported versions: "
        + supported()
        + ".";
  }
}
