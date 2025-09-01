package org.lift.trax;

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

    @Override
    public String toString() {
        return name();
    }
}
