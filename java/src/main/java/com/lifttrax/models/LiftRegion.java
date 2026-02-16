package com.lifttrax.models;

public enum LiftRegion {
    UPPER,
    LOWER;

    public static LiftRegion fromString(String value) {
        return LiftRegion.valueOf(value.toUpperCase());
    }
}
