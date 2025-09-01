package org.lift.trax;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ExecutionSet {
    @JsonIgnore public Integer reps;
    @JsonIgnore public Integer timeSecs;
    @JsonIgnore public Integer distanceFeet;
    public double weight;
    public Double rpe;

    public ExecutionSet() {}

    public ExecutionSet(int reps, double weight, Double rpe) {
        this.reps = reps;
        this.weight = weight;
        this.rpe = rpe;
    }

    @JsonSetter("reps")
    private void setReps(Integer reps) {
        this.reps = reps;
    }

    @JsonProperty("metric")
    private void unpackMetric(Map<String, Integer> metric) {
        if (metric == null) return;
        if (metric.containsKey("Reps")) {
            this.reps = metric.get("Reps");
        } else if (metric.containsKey("TimeSecs")) {
            this.timeSecs = metric.get("TimeSecs");
        } else if (metric.containsKey("DistanceFeet")) {
            this.distanceFeet = metric.get("DistanceFeet");
        }
    }

    @JsonProperty("metric")
    private Map<String, Integer> packMetric() {
        if (reps != null) return Map.of("Reps", reps);
        if (timeSecs != null) return Map.of("TimeSecs", timeSecs);
        if (distanceFeet != null) return Map.of("DistanceFeet", distanceFeet);
        return null;
    }

    @JsonProperty("weight")
    private void unpackWeight(Object weightVal) {
        if (weightVal == null) return;
        if (weightVal instanceof Number) {
            this.weight = ((Number) weightVal).doubleValue();
        } else {
            String cleaned = weightVal.toString().replaceAll("[^0-9.]", "");
            if (!cleaned.isEmpty()) {
                this.weight = Double.parseDouble(cleaned);
            }
        }
    }
}
