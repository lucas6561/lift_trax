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
        return name();
    }
}
