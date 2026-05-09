package com.lifttrax.models;

/** List of allowed LiftType values used throughout LiftTrax. */
public enum LiftType {
  BENCH_PRESS,
  OVERHEAD_PRESS,
  SQUAT,
  DEADLIFT,
  CONDITIONING,
  ACCESSORY,
  MOBILITY;

  public static LiftType fromDbValue(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return LiftType.valueOf(value.replace(' ', '_').toUpperCase(java.util.Locale.ROOT));
  }

  public String toDbValue() {
    return name().replace('_', ' ');
  }
}
