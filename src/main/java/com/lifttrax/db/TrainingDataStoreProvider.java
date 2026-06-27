package com.lifttrax.db;

/** Creates user-scoped training data stores for authenticated web requests. */
public interface TrainingDataStoreProvider extends AutoCloseable {
  TrainingDataStore forUser(String ownerUserId) throws Exception;

  @Override
  default void close() throws Exception {}

  static TrainingDataStoreProvider fromEnvironment(String sqliteDbPath) throws Exception {
    String mode = setting("lifttrax.dataStore", "LIFTTRAX_DATA_STORE", "sqlite");
    if ("hosted-postgres".equalsIgnoreCase(mode) || "postgres".equalsIgnoreCase(mode)) {
      return HostedPostgresTrainingDataStoreProvider.fromEnvironment();
    }
    if (!"sqlite".equalsIgnoreCase(mode)) {
      throw new IllegalArgumentException("Unsupported LIFTTRAX_DATA_STORE: " + mode);
    }
    return new SqliteDb(sqliteDbPath);
  }

  private static String setting(String property, String environment, String fallback) {
    String value = System.getProperty(property);
    if (value == null || value.isBlank()) {
      value = System.getenv(environment);
    }
    if (value == null || value.isBlank()) {
      return fallback;
    }
    return value.trim();
  }
}
