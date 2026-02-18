package com.lifttrax.ui;

import com.lifttrax.models.ExecutionSet;
import com.lifttrax.models.LiftExecution;
import com.lifttrax.models.SetMetric;

import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.stream.Collectors;

final class ExecutionFormatter {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private ExecutionFormatter() {
    }

    static String formatExecution(LiftExecution execution) {
        String sets = execution.sets().stream()
                .map(ExecutionFormatter::formatSet)
                .collect(Collectors.joining(" | "));

        String tags = "";
        if (execution.warmup()) {
            tags += " [warmup]";
        }
        if (execution.deload()) {
            tags += " [deload]";
        }

        String notes = (execution.notes() == null || execution.notes().isBlank()) ? "" : " — " + execution.notes();
        return execution.date().format(DATE_FORMAT) + ": " + sets + tags + notes;
    }

    private static String formatSet(ExecutionSet set) {
        String rpe = set.rpe() == null ? "" : " RPE " + trimTrailingZero(set.rpe());
        return formatMetric(set.metric()) + " @ " + set.weight() + rpe;
    }

    private static String formatMetric(SetMetric metric) {
        if (metric instanceof SetMetric.Reps reps) {
            return reps.reps() + " reps";
        }
        if (metric instanceof SetMetric.RepsLr repsLr) {
            return repsLr.left() + "L/" + repsLr.right() + "R reps";
        }
        if (metric instanceof SetMetric.RepsRange range) {
            return range.min() + "-" + range.max() + " reps";
        }
        if (metric instanceof SetMetric.TimeSecs timeSecs) {
            return timeSecs.seconds() + " sec";
        }
        if (metric instanceof SetMetric.DistanceFeet distanceFeet) {
            return distanceFeet.feet() + " ft";
        }
        return "unknown";
    }

    private static String trimTrailingZero(float value) {
        if (value == (long) value) {
            return String.format(Locale.ROOT, "%d", (long) value);
        }
        return String.format(Locale.ROOT, "%s", value);
    }
}
