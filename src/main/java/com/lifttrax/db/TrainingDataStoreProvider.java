package com.lifttrax.db;

import com.lifttrax.config.LiftTraxConfig;

/** Creates user-scoped training data stores for authenticated web requests. */
public interface TrainingDataStoreProvider extends AutoCloseable {
  TrainingDataStore forUser(String ownerUserId) throws Exception;

  @Override
  default void close() throws Exception {}

  static TrainingDataStoreProvider fromEnvironment(String sqliteDbPath) throws Exception {
    String mode = LiftTraxConfig.setting("lifttrax.dataStore", "LIFTTRAX_DATA_STORE", "sqlite");
    if ("hosted-postgres".equalsIgnoreCase(mode) || "postgres".equalsIgnoreCase(mode)) {
      return HostedPostgresTrainingDataStoreProvider.fromEnvironment();
    }
    if (!"sqlite".equalsIgnoreCase(mode)) {
      throw new IllegalArgumentException("Unsupported lifttrax.dataStore: " + mode);
    }
    return new SqliteDb(sqliteDbPath);
  }
}
