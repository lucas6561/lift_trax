package com.lifttrax.db;

import com.lifttrax.config.LiftTraxConfig;

/** Creates user-scoped training data stores for authenticated web requests. */
public interface TrainingDataStoreProvider extends AutoCloseable {
  TrainingDataStore forUser(String ownerUserId) throws Exception;

  default TrainingDataStore forUserIdentifier(String identifier) throws Exception {
    return forUser(resolveAuthUserId(identifier));
  }

  default String resolveAuthUserId(String identifier) throws Exception {
    return identifier;
  }

  default AccountProfile accountFor(String authUserId, String email) throws Exception {
    forUser(authUserId);
    return new AccountProfile(authUserId, "", email);
  }

  default AccountProfile updateUsername(String authUserId, String username) throws Exception {
    throw new UnsupportedOperationException("Account usernames require hosted Postgres.");
  }

  @Override
  default void close() throws Exception {}

  static TrainingDataStoreProvider fromEnvironment() throws Exception {
    String mode = LiftTraxConfig.setting("lifttrax.dataStore", "LIFTTRAX_DATA_STORE", "postgres");
    if (!"hosted-postgres".equalsIgnoreCase(mode) && !"postgres".equalsIgnoreCase(mode)) {
      throw new IllegalArgumentException("Unsupported lifttrax.dataStore: " + mode);
    }
    return HostedPostgresTrainingDataStoreProvider.fromEnvironment();
  }
}
