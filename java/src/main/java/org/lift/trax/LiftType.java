package org.lift.trax;

public enum LiftType {
    BENCH_PRESS,
    OVERHEAD_PRESS,
    SQUAT,
    DEADLIFT,
    CONDITIONING,
    ACCESSORY,
    MOBILITY;

    @Override
    public String toString() {
        return name().replace('_', ' ');
    }

    public static LiftType fromString(String s) {
        return valueOf(s.replace(' ', '_'));
    }
}
