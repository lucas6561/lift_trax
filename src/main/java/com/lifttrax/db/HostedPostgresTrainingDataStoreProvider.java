package com.lifttrax.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/** Hosted JDBC/Postgres provider for user-scoped web training data. */
public final class HostedPostgresTrainingDataStoreProvider implements TrainingDataStoreProvider {
  private final HostedPostgresConfig config;
  private final Map<String, TrainingDataStore> storesByUser = new HashMap<>();
  private final Lock storesLock = new ReentrantLock();

  public HostedPostgresTrainingDataStoreProvider(HostedPostgresConfig config) throws Exception {
    this.config = config;
    try (Connection connection = openConnection()) {
      PostgresSchemaMigrator.migrate(connection);
    }
  }

  public static HostedPostgresTrainingDataStoreProvider fromEnvironment() throws Exception {
    return new HostedPostgresTrainingDataStoreProvider(HostedPostgresConfig.fromEnvironment());
  }

  @Override
  public TrainingDataStore forUser(String ownerUserId) throws Exception {
    String requiredOwnerUserId = requireUserId(ownerUserId);
    storesLock.lock();
    try {
      TrainingDataStore cached = storesByUser.get(requiredOwnerUserId);
      if (cached != null) {
        return cached;
      }
      String appUserId;
      String lifterProfileId;
      try (Connection connection = openConnection()) {
        appUserId = ensureAppUser(connection, requiredOwnerUserId);
        lifterProfileId = ensureDefaultLifterProfile(connection, appUserId, requiredOwnerUserId);
      }
      TrainingDataStore store =
          new HostedPostgresTrainingDataStore(
              config, requiredOwnerUserId, appUserId, lifterProfileId);
      storesByUser.put(requiredOwnerUserId, store);
      return store;
    } finally {
      storesLock.unlock();
    }
  }

  static String ensureAppUser(Connection connection, String authUserId) throws SQLException {
    String requiredAuthUserId = requireUserId(authUserId);
    String existing = findAppUserId(connection, requiredAuthUserId);
    if (existing != null) {
      return existing;
    }
    String generated = java.util.UUID.randomUUID().toString();
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO app_users (id, auth_user_id, email) VALUES (?, ?, ?)")) {
      statement.setString(1, generated);
      statement.setString(2, requiredAuthUserId);
      statement.setString(3, "");
      statement.executeUpdate();
      return generated;
    }
  }

  static String ensureDefaultLifterProfile(
      Connection connection, String appUserId, String authUserId) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            """
                SELECT id FROM lifter_profiles
                WHERE owner_user_id = ? AND is_default = TRUE
                """)) {
      statement.setString(1, appUserId);
      try (var rs = statement.executeQuery()) {
        if (rs.next()) {
          return rs.getString("id");
        }
      }
    }
    String generated = java.util.UUID.randomUUID().toString();
    try (PreparedStatement statement =
        connection.prepareStatement(
            """
                INSERT INTO lifter_profiles (id, owner_user_id, display_name, is_default)
                VALUES (?, ?, ?, TRUE)
                """)) {
      statement.setString(1, generated);
      statement.setString(2, appUserId);
      statement.setString(3, requireUserId(authUserId));
      statement.executeUpdate();
      return generated;
    }
  }

  static Connection openConnection(HostedPostgresConfig config) throws SQLException {
    Properties properties = new Properties();
    if (!config.username().isBlank()) {
      properties.setProperty("user", config.username());
    }
    if (!config.password().isBlank()) {
      properties.setProperty("password", config.password());
    }
    return DriverManager.getConnection(config.jdbcUrl(), properties);
  }

  private Connection openConnection() throws SQLException {
    return openConnection(config);
  }

  private static String findAppUserId(Connection connection, String authUserId)
      throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement("SELECT id FROM app_users WHERE auth_user_id = ?")) {
      statement.setString(1, authUserId);
      try (var rs = statement.executeQuery()) {
        return rs.next() ? rs.getString("id") : null;
      }
    }
  }

  private static String requireUserId(String userId) {
    if (userId == null || userId.isBlank()) {
      throw new IllegalArgumentException("Authenticated user is required.");
    }
    return userId.trim();
  }
}
