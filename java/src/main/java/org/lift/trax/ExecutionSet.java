package org.lift.trax;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ExecutionSet {
    public int reps;
    public double weight;
    public Double rpe;

    public ExecutionSet() {}

    public ExecutionSet(int reps, double weight, Double rpe) {
        this.reps = reps;
        this.weight = weight;
        this.rpe = rpe;
    }

    @JsonProperty("metric")
    private void unpackMetric(Map<String, Integer> metric) {
        if (metric == null) return;
        if (metric.containsKey("Reps")) {
            this.reps = metric.get("Reps");
        }
        // TimeSecs and DistanceFeet are ignored since Java layer only tracks reps currently
    }
}
