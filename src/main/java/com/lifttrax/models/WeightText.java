package com.lifttrax.models;

import java.util.Locale;

/** Shared parsing helpers for comparing user-entered weight text. */
public final class WeightText {
  private WeightText() {}

  public static double toPounds(String weight) {
    if (weight == null) {
      return 0.0;
    }
    String trimmed = weight.trim().toLowerCase(Locale.ROOT);
    if (trimmed.isEmpty() || "none".equals(trimmed)) {
      return 0.0;
    }
    if (trimmed.contains("|")) {
      String[] parts = trimmed.split("\\|", 2);
      try {
        return parseSide(parts[0]) + parseSide(parts[1]);
      } catch (IllegalArgumentException e) {
        return 0.0;
      }
    }
    if (trimmed.contains("+")) {
      String[] parts = trimmed.split("\\+");
      try {
        double raw = parseSide(parts[0]);
        if (parts.length == 2 && parts[1].trim().endsWith("c")) {
          String chain = parts[1].trim().substring(0, parts[1].trim().length() - 1);
          return raw + parseSide(chain);
        }
        return raw;
      } catch (IllegalArgumentException e) {
        return 0.0;
      }
    }
    try {
      return parseSide(trimmed);
    } catch (IllegalArgumentException e) {
      return 0.0;
    }
  }

  private static double parseSide(String value) {
    String trimmed = value.trim();
    if (trimmed.endsWith("kg")) {
      String stripped = trimmed.substring(0, trimmed.length() - 2).trim();
      return Double.parseDouble(stripped) * 2.20462;
    }
    if (trimmed.endsWith("lb")) {
      String stripped = trimmed.substring(0, trimmed.length() - 2).trim();
      return Double.parseDouble(stripped);
    }
    return Double.parseDouble(trimmed);
  }
}
