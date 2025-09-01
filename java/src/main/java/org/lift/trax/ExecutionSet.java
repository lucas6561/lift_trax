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
    @JsonIgnore public String weightText;
    @JsonIgnore public double weight;
    public Double rpe;

    public ExecutionSet() {}

    public ExecutionSet(int reps, double weight, Double rpe) {
        this.reps = reps;
        this.weight = weight;
        this.weightText = Double.toString(weight);
        this.rpe = rpe;
    }

    public ExecutionSet(int reps, String weightText, Double rpe) {
        this.reps = reps;
        this.weightText = weightText;
        this.weight = WeightParser.toLbs(weightText);
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
        this.weightText = weightVal.toString();
        if (weightVal instanceof Number) {
            this.weight = ((Number) weightVal).doubleValue();
        } else {
            this.weight = WeightParser.toLbs(weightText);
        }
    }

    @JsonProperty("weight")
    private Object packWeight() {
        return weightText != null ? weightText : weight;
    }

    public String displayWeight() {
        return weightText != null ? weightText : Double.toString(weight);
    }
}
