package com.lifttrax.db;

import java.time.LocalDateTime;

/** Library entry; the workout document is fetched only when the user opens it. */
public record SavedWorkout(String id, String name, LocalDateTime createdAt) {
  public static String validateName(String name) {
    String cleaned = name == null ? "" : name.trim();
    if (cleaned.isEmpty() || cleaned.length() > 200) {
      throw new IllegalArgumentException("Workout name must be between 1 and 200 characters.");
    }
    return cleaned;
  }
}
