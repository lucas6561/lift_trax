package com.lifttrax.models;

public enum Muscle {
    BICEP,
    TRICEP,
    NECK,
    LAT,
    QUAD,
    HAMSTRING,
    CALF,
    LOWER_BACK,
    CHEST,
    FOREARM,
    REAR_DELT,
    FRONT_DELT,
    SHOULDER,
    CORE,
    GLUTE,
    TRAP;

    public static Muscle fromString(String value) {
        return Muscle.valueOf(value.toUpperCase().replace('-', '_'));
    }
}
