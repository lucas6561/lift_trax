package com.lifttrax.db;

import com.lifttrax.config.LiftTraxConfig;
import com.lifttrax.models.Lift;
import java.util.List;

/** Creates user-scoped training data stores for authenticated web requests. */
public interface TrainingDataStoreProvider extends AutoCloseable {
  TrainingDataStore forUser(String ownerUserId) throws Exception;

  default boolean isLiftCatalogShared(String authUserId) throws Exception {
    return false;
  }

  default void setLiftCatalogShared(String authUserId, boolean shared) throws Exception {
    throw new UnsupportedOperationException("Lift sharing requires Postgres.");
  }

  default List<String> sharedLiftUsers(String authUserId) throws Exception {
    return List.of();
  }

  default List<Lift> sharedLifts(String authUserId, String username) throws Exception {
    throw new IllegalArgumentException("This lift list is no longer shared.");
  }

  default int importSharedLifts(String authUserId, String username, List<String> names)
      throws Exception {
    throw new UnsupportedOperationException("Lift sharing requires Postgres.");
  }

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

  default AccountProfile createLocalAccount(String username, String email) throws Exception {
    throw new UnsupportedOperationException("Local accounts require Postgres.");
  }

  default AccountProfile createLocalAccount(String username, String email, String password)
      throws Exception {
    throw new UnsupportedOperationException("Local passwords require Postgres.");
  }

  default java.util.Optional<LocalAuthentication> authenticateLocal(
      String identifier, String password) throws Exception {
    return java.util.Optional.empty();
  }

  default String localPasswordVersion(String authUserId) throws Exception {
    return "";
  }

  default void changeLocalPassword(String authUserId, String currentPassword, String newPassword)
      throws Exception {
    throw new UnsupportedOperationException("Local passwords require Postgres.");
  }

  /** Operator-only bootstrap/recovery; never expose this operation through an HTTP route. */
  default void resetLocalPassword(String authUserId, String password) throws Exception {
    throw new UnsupportedOperationException("Local passwords require Postgres.");
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
