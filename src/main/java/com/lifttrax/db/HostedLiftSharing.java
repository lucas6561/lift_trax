package com.lifttrax.db;

import com.lifttrax.models.Lift;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Explicit, catalog-only sharing across the otherwise private account boundary. */
final class HostedLiftSharing {
  private final HostedPostgresConfig config;

  HostedLiftSharing(HostedPostgresConfig config) {
    this.config = config;
  }

  boolean isShared(String authUserId) throws Exception {
    try (Connection connection = open();
        var statement =
            connection.prepareStatement(
                "SELECT share_lift_catalog FROM app_users WHERE auth_user_id = ?")) {
      statement.setString(1, authUserId);
      try (var rows = statement.executeQuery()) {
        return rows.next() && rows.getBoolean(1);
      }
    }
  }

  void setShared(String authUserId, boolean shared) throws Exception {
    try (Connection connection = open();
        var statement =
            connection.prepareStatement(
                "UPDATE app_users SET share_lift_catalog = ? WHERE auth_user_id = ?"
                    + " AND (? = FALSE OR (username IS NOT NULL AND username <> ''))")) {
      statement.setBoolean(1, shared);
      statement.setString(2, authUserId);
      statement.setBoolean(3, shared);
      if (statement.executeUpdate() != 1) {
        throw new IllegalArgumentException("Save a username before sharing your lifts.");
      }
    }
  }

  List<String> users(String authUserId) throws Exception {
    try (Connection connection = open();
        var statement =
            connection.prepareStatement(
                "SELECT username FROM app_users WHERE share_lift_catalog = TRUE"
                    + " AND auth_user_id <> ? AND username IS NOT NULL AND username <> '' ORDER BY username")) {
      statement.setString(1, authUserId);
      try (var rows = statement.executeQuery()) {
        List<String> result = new ArrayList<>();
        while (rows.next()) {
          result.add(rows.getString(1));
        }
        return result;
      }
    }
  }

  List<Lift> lifts(String authUserId, String username) throws Exception {
    try (Connection connection = open()) {
      return readLifts(connection, sharedOwner(connection, authUserId, username));
    }
  }

  int copy(String authUserId, String username, List<String> names) throws Exception {
    Set<String> selected = new LinkedHashSet<>(names);
    if (selected.isEmpty()) {
      throw new IllegalArgumentException("Choose at least one lift to import.");
    }
    try (Connection connection = open()) {
      connection.setAutoCommit(false);
      try {
        // Serialize imports and sharing changes, with a stable lock order for reciprocal imports.
        try (var lock =
            connection.prepareStatement(
                "SELECT id FROM app_users WHERE auth_user_id = ? OR username = ? ORDER BY id FOR UPDATE")) {
          lock.setString(1, authUserId);
          lock.setString(2, username.trim().toLowerCase(Locale.ROOT));
          try (var rows = lock.executeQuery()) {
            while (rows.next()) {
              rows.getString(1);
            }
          }
        }
        String sourceOwner = sharedOwner(connection, authUserId, username);
        List<Lift> available = readLifts(connection, sourceOwner);
        if (!available.stream().map(Lift::name).toList().containsAll(selected)) {
          throw new IllegalArgumentException(
              "A selected lift is no longer available. Reload the list.");
        }
        String targetOwner =
            HostedPostgresTrainingDataStoreProvider.ensureAppUser(connection, authUserId);
        String targetProfile =
            HostedPostgresTrainingDataStoreProvider.ensureDefaultLifterProfile(
                connection, targetOwner, authUserId);
        Set<String> existing = new HashSet<>();
        try (var statement =
            connection.prepareStatement(
                "SELECT name FROM exercise_catalog_entries WHERE owner_user_id = ? AND lifter_profile_id = ?")) {
          statement.setString(1, targetOwner);
          statement.setString(2, targetProfile);
          try (var rows = statement.executeQuery()) {
            while (rows.next()) {
              existing.add(normalizedName(rows.getString(1)));
            }
          }
        }
        int imported = 0;
        for (Lift lift : available) {
          if (selected.contains(lift.name()) && existing.add(normalizedName(lift.name()))) {
            insertLift(connection, targetOwner, targetProfile, lift);
            imported++;
          }
        }
        connection.commit();
        return imported;
      } catch (Exception e) {
        connection.rollback();
        throw e;
      }
    }
  }

  private static void insertLift(Connection connection, String owner, String profile, Lift lift)
      throws Exception {
    try (var statement =
        connection.prepareStatement(
            "INSERT INTO exercise_catalog_entries"
                + " (id, owner_user_id, lifter_profile_id, name, region, main_lift, muscles, notes, enabled)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, TRUE)")) {
      statement.setString(1, UUID.randomUUID().toString());
      statement.setString(2, owner);
      statement.setString(3, profile);
      statement.setString(4, lift.name());
      statement.setString(5, lift.region().name());
      statement.setString(6, lift.main() == null ? null : lift.main().toDbValue());
      statement.setString(7, String.join(",", lift.muscles().stream().map(Enum::name).toList()));
      statement.setString(8, lift.notes());
      statement.executeUpdate();
    }
  }

  private static String sharedOwner(Connection connection, String requester, String username)
      throws Exception {
    try (var statement =
        connection.prepareStatement(
            "SELECT id FROM app_users WHERE username = ? AND share_lift_catalog = TRUE AND auth_user_id <> ?")) {
      statement.setString(1, username.trim().toLowerCase(Locale.ROOT));
      statement.setString(2, requester);
      try (var rows = statement.executeQuery()) {
        if (!rows.next()) {
          throw new IllegalArgumentException("This lift list is no longer shared.");
        }
        return rows.getString(1);
      }
    }
  }

  private static List<Lift> readLifts(Connection connection, String owner) throws Exception {
    try (var statement =
        connection.prepareStatement(
            "SELECT e.name, e.region, e.main_lift, e.muscles, e.notes"
                + " FROM exercise_catalog_entries e JOIN lifter_profiles p ON p.id = e.lifter_profile_id"
                + " WHERE e.owner_user_id = ? AND p.owner_user_id = e.owner_user_id"
                + " AND p.is_default = TRUE AND e.enabled = TRUE ORDER BY e.name")) {
      statement.setString(1, owner);
      try (var rows = statement.executeQuery()) {
        List<Lift> result = new ArrayList<>();
        while (rows.next()) {
          result.add(HostedPostgresTrainingDataStore.mapLift(rows));
        }
        return result;
      }
    }
  }

  private static String normalizedName(String name) {
    return name.trim().toLowerCase(Locale.ROOT);
  }

  private Connection open() throws Exception {
    return HostedPostgresTrainingDataStoreProvider.openConnection(config);
  }
}
