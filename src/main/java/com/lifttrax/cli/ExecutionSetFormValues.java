package com.lifttrax.cli;

import com.lifttrax.models.ExecutionSet;
import com.lifttrax.models.SetMetric;
import java.util.Locale;

/** Shared execution-set values used by web edit form markup and its JSON payload. */
record ExecutionSetFormValues(
    String metricType,
    String metricValue,
    String metricLeft,
    String metricRight,
    String weight,
    String rpe) {

  static ExecutionSetFormValues from(ExecutionSet set) {
    MetricFields metricFields = MetricFields.from(set.metric());
    return new ExecutionSetFormValues(
        metricFields.metricType(),
        metricFields.metricValue(),
        metricFields.metricLeft(),
        metricFields.metricRight(),
        set.weight() == null ? "" : set.weight(),
        set.rpe() == null ? "" : String.format(Locale.ROOT, "%s", set.rpe()));
  }

  String selectedAttribute(String optionValue) {
    return optionValue.equals(metricType) ? " selected" : "";
  }

  private record MetricFields(
      String metricType, String metricValue, String metricLeft, String metricRight) {
    static MetricFields from(SetMetric metric) {
      if (metric instanceof SetMetric.Reps reps) {
        return new MetricFields("reps", String.valueOf(reps.reps()), "", "");
      }
      if (metric instanceof SetMetric.RepsLr repsLr) {
        return new MetricFields(
            "reps-lr", "", String.valueOf(repsLr.left()), String.valueOf(repsLr.right()));
      }
      if (metric instanceof SetMetric.TimeSecs timeSecs) {
        return new MetricFields("time", String.valueOf(timeSecs.seconds()), "", "");
      }
      if (metric instanceof SetMetric.DistanceFeet distanceFeet) {
        return new MetricFields("distance", String.valueOf(distanceFeet.feet()), "", "");
      }
      return new MetricFields("reps", "", "", "");
    }
  }
}
