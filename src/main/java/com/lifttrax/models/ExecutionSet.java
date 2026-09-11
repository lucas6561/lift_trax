package com.lifttrax.models;

/** Simple data holder for ExecutionSet values used by LiftTrax. */
public record ExecutionSet(SetMetric metric, String weight, Float rpe, boolean missed) {
  public ExecutionSet(SetMetric metric, String weight, Float rpe) {
    this(metric, weight, rpe, false);
  }
}
