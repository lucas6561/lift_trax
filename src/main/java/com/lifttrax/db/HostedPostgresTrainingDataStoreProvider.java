package com.lifttrax.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/** Hosted JDBC/Postgres provider for user-scoped web training data. */
public final class HostedPostgresTrainingDataStoreProvider implements TrainingDataStoreProvider {
  private static final String USERNAME_PATTERN = "[a-z0-9][a-z0-9_-]{2,29}";
  private final HostedPostgresConfig config;
  private final Map<String, TrainingDataStore> storesByUser = new HashMap<>();
  private final Map<String, AccountProfile> accountsByUser = new ConcurrentHashMap<>();
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
        accountsByUser.put(requiredOwnerUserId, findAccount(connection, requiredOwnerUserId));
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

  @Override
  public TrainingDataStore forUserIdentifier(String identifier) throws Exception {
    return forUser(resolveAuthUserId(identifier));
  }

  @Override
  public AccountProfile accountFor(String authUserId, String email) throws Exception {
    String requiredAuthUserId = requireUserId(authUserId);
    String normalizedEmail = email == null ? "" : email.trim();
    AccountProfile cached = accountsByUser.get(requiredAuthUserId);
    if (cached != null && (normalizedEmail.isBlank() || normalizedEmail.equals(cached.email()))) {
      return cached;
    }
    try (Connection connection = openConnection()) {
      ensureAppUser(connection, requiredAuthUserId);
      if (!normalizedEmail.isBlank()) {
        try (PreparedStatement statement =
            connection.prepareStatement("UPDATE app_users SET email = ? WHERE auth_user_id = ?")) {
          statement.setString(1, normalizedEmail);
          statement.setString(2, requiredAuthUserId);
          statement.executeUpdate();
        }
      }
      AccountProfile account = findAccount(connection, requiredAuthUserId);
      accountsByUser.put(requiredAuthUserId, account);
      return account;
    }
  }

  @Override
  public AccountProfile updateUsername(String authUserId, String username) throws Exception {
    String requiredAuthUserId = requireUserId(authUserId);
    String normalized = normalizeUsername(username);
    try (Connection connection = openConnection()) {
      ensureAppUser(connection, requiredAuthUserId);
      try (PreparedStatement statement =
          connection.prepareStatement("UPDATE app_users SET username = ? WHERE auth_user_id = ?")) {
        statement.setString(1, normalized);
        statement.setString(2, requiredAuthUserId);
        statement.executeUpdate();
      } catch (SQLException e) {
        if ("23505".equals(e.getSQLState())) {
          throw new IllegalArgumentException("That username is already in use.", e);
        }
        throw e;
      }
      AccountProfile account = findAccount(connection, requiredAuthUserId);
      accountsByUser.put(requiredAuthUserId, account);
      return account;
    }
  }

  @Override
  public String resolveAuthUserId(String identifier) throws SQLException {
    String required = requireUserId(identifier);
    String normalized = required.toLowerCase(Locale.ROOT);
    try (Connection connection = openConnection()) {
      String byUsername = findAuthUserIdByUsername(connection, normalized);
      if (byUsername != null) {
        return byUsername;
      }
      if (findAppUserId(connection, required) != null) {
        return required;
      }
    }
    throw new IllegalArgumentException(
        "No LiftTrax account matches '" + required + "'. Sign in once, then set a username.");
  }

  private static String findAuthUserIdByUsername(Connection connection, String username)
      throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement("SELECT auth_user_id FROM app_users WHERE username = ?")) {
      statement.setString(1, username);
      try (var rs = statement.executeQuery()) {
        return rs.next() ? rs.getString("auth_user_id") : null;
      }
    }
  }

  private static AccountProfile findAccount(Connection connection, String authUserId)
      throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            "SELECT auth_user_id, username, email FROM app_users WHERE auth_user_id = ?")) {
      statement.setString(1, authUserId);
      try (var rs = statement.executeQuery()) {
        if (!rs.next()) {
          throw new IllegalStateException("LiftTrax account was not created.");
        }
        return new AccountProfile(
            rs.getString("auth_user_id"), rs.getString("username"), rs.getString("email"));
      }
    }
  }

  static String normalizeUsername(String username) {
    String normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    if (!normalized.matches(USERNAME_PATTERN)) {
      throw new IllegalArgumentException(
          "Username must be 3-30 characters using lowercase letters, numbers, _ or -.");
    }
    return normalized;
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
