package org.lift.trax;

import java.util.Map;

public class LiftStats {
    public LiftExecution last;
    public Map<Integer, Double> bestByReps;

    public LiftStats(LiftExecution last, Map<Integer, Double> bestByReps) {
        this.last = last;
        this.bestByReps = bestByReps;
    }
}
