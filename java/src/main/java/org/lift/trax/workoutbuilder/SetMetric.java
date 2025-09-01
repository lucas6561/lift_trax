package org.lift.trax.workoutbuilder;

public class SetMetric {
    public final Integer reps;
    public final Integer timeSecs;
    public final Integer distanceM;

    private SetMetric(Integer reps, Integer timeSecs, Integer distanceM) {
        this.reps = reps;
        this.timeSecs = timeSecs;
        this.distanceM = distanceM;
    }

    public static SetMetric reps(int reps) {
        return new SetMetric(reps, null, null);
    }

    public static SetMetric timeSecs(int secs) {
        return new SetMetric(null, secs, null);
    }

    public static SetMetric distanceM(int meters) {
        return new SetMetric(null, null, meters);
    }
}
