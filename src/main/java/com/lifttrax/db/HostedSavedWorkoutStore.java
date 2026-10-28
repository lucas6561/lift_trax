package com.lifttrax.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/** Shared account scope and saved-workout library for the Postgres training store. */
abstract class HostedSavedWorkoutStore implements TrainingDataStore {
  protected final HostedPostgresConfig config;
  protected final String lifterProfileId;

  HostedSavedWorkoutStore(HostedPostgresConfig config, String lifterProfileId) {
    this.config = config;
    this.lifterProfileId = lifterProfileId;
  }

  protected final Connection openConnection() throws Exception {
    return HostedPostgresTrainingDataStoreProvider.openConnection(config);
  }

  @Override
  public List<SavedWorkout> listSavedWorkouts() throws Exception {
    try (Connection connection = openConnection();
        PreparedStatement statement =
            connection.prepareStatement(
                """
            SELECT id, name, created_at FROM saved_workouts
            WHERE lifter_profile_id = ? ORDER BY created_at DESC, id DESC
            """)) {
      statement.setString(1, lifterProfileId);
      List<SavedWorkout> workouts = new ArrayList<>();
      try (ResultSet rows = statement.executeQuery()) {
        while (rows.next()) {
          workouts.add(
              new SavedWorkout(
                  rows.getString("id"),
                  rows.getString("name"),
                  rows.getTimestamp("created_at").toLocalDateTime()));
        }
      }
      return workouts;
    }
  }

  @Override
  public String saveWorkout(String name, String workoutJson) throws Exception {
    String cleanedName = SavedWorkout.validateName(name);
    if (workoutJson == null || workoutJson.isBlank()) {
      throw new IllegalArgumentException("Workout document is required.");
    }
    String id = java.util.UUID.randomUUID().toString();
    try (Connection connection = openConnection();
        PreparedStatement statement =
            connection.prepareStatement(
                """
            INSERT INTO saved_workouts (id, lifter_profile_id, name, workout_json)
            VALUES (?, ?, ?, ?)
            """)) {
      statement.setString(1, id);
      statement.setString(2, lifterProfileId);
      statement.setString(3, cleanedName);
      statement.setString(4, workoutJson);
      statement.executeUpdate();
    }
    return id;
  }

  @Override
  public String getSavedWorkoutJson(String id) throws Exception {
    try (Connection connection = openConnection();
        PreparedStatement statement =
            connection.prepareStatement(
                "SELECT workout_json FROM saved_workouts WHERE lifter_profile_id = ? AND id = ?")) {
      statement.setString(1, lifterProfileId);
      statement.setString(2, id);
      try (ResultSet rows = statement.executeQuery()) {
        if (!rows.next()) {
          throw new IllegalArgumentException("Saved workout not found.");
        }
        return rows.getString("workout_json");
      }
    }
  }

  @Override
  public void renameSavedWorkout(String id, String name) throws Exception {
    String cleanedName = SavedWorkout.validateName(name);
    try (Connection connection = openConnection();
        PreparedStatement statement =
            connection.prepareStatement(
                "UPDATE saved_workouts SET name = ? WHERE lifter_profile_id = ? AND id = ?")) {
      statement.setString(1, cleanedName);
      statement.setString(2, lifterProfileId);
      statement.setString(3, id);
      if (statement.executeUpdate() != 1) {
        throw new IllegalArgumentException("Saved workout not found.");
      }
    }
  }

  @Override
  public void deleteSavedWorkout(String id) throws Exception {
    try (Connection connection = openConnection();
        PreparedStatement statement =
            connection.prepareStatement(
                "DELETE FROM saved_workouts WHERE lifter_profile_id = ? AND id = ?")) {
      statement.setString(1, lifterProfileId);
      statement.setString(2, id);
      if (statement.executeUpdate() != 1) {
        throw new IllegalArgumentException("Saved workout not found.");
      }
    }
  }
}
