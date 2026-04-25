package com.lifttrax.ui;

import com.lifttrax.models.ExecutionSet;
import com.lifttrax.models.LiftExecution;
import com.lifttrax.models.SetMetric;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Core ExecutionFormatter component used by LiftTrax.
 */

final class ExecutionFormatter {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private ExecutionFormatter() {
    }

    static String formatExecution(LiftExecution execution) {
        return execution.date().format(DATE_FORMAT) + ": " + formatExecutionSummary(execution);
    }

    static String formatExecutionSummary(LiftExecution execution) {
        String sets = formatSets(execution.sets());

        String tags = "";
        if (execution.warmup()) {
            tags += " [warmup]";
        }
        if (execution.deload()) {
            tags += " [deload]";
        }

        String notes = (execution.notes() == null || execution.notes().isBlank()) ? "" : " — " + execution.notes();
        return sets + tags + notes;
    }

    static String formatSets(List<ExecutionSet> sets) {
        List<String> parts = new ArrayList<>();
        int index = 0;
        while (index < sets.size()) {
            ExecutionSet set = sets.get(index);
            int count = 1;
            while (index + count < sets.size() && set.equals(sets.get(index + count))) {
                count++;
            }

            String part = formatSet(set);
            parts.add(count > 1 ? count + "x " + part : part);
            index += count;
        }
        return String.join(", ", parts);
    }

    private static String formatSet(ExecutionSet set) {
        String rpe = set.rpe() == null ? "" : " RPE " + trimTrailingZero(set.rpe());
        String weight = set.weight() == null ? "" : set.weight().trim();
        String weightText = weight.isBlank() || weight.equalsIgnoreCase("none") ? "" : " @ " + weight;
        return formatMetric(set.metric()) + weightText + rpe;
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
