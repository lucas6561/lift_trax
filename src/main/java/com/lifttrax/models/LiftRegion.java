package com.lifttrax.models;

/** List of allowed LiftRegion values used throughout LiftTrax. */
public enum LiftRegion {
  UPPER,
  LOWER;

  public static LiftRegion fromString(String value) {
    return LiftRegion.valueOf(value.toUpperCase(java.util.Locale.ROOT));
  }
}
