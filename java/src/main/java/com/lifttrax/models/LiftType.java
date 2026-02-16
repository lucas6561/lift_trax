package com.lifttrax.models;

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
        return LiftType.valueOf(value.replace(' ', '_').toUpperCase());
    }

    public String toDbValue() {
        return name().replace('_', ' ');
    }
}
