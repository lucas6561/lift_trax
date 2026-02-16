package com.lifttrax.models;

public sealed interface SetMetric permits SetMetric.Reps, SetMetric.RepsLr, SetMetric.RepsRange, SetMetric.TimeSecs, SetMetric.DistanceFeet {
    record Reps(int reps) implements SetMetric {}
    record RepsLr(int left, int right) implements SetMetric {}
    record RepsRange(int min, int max) implements SetMetric {}
    record TimeSecs(int seconds) implements SetMetric {}
    record DistanceFeet(int feet) implements SetMetric {}
}
