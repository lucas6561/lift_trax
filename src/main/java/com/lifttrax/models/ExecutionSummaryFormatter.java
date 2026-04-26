package com.lifttrax.models;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared execution summary formatting used by multiple surfaces.
 */
public final class ExecutionSummaryFormatter {
    private ExecutionSummaryFormatter() {
    }

    public static String formatCompactSummary(LiftExecution execution) {
        String notes = execution.notes() == null ? "" : execution.notes();
        if (execution.sets().isEmpty()) {
            return notes.isBlank() ? "no sets recorded" : "no sets recorded - " + notes;
        }
        ExecutionSet first = execution.sets().get(0);
        String rpe = first.rpe() == null ? "" : " RPE " + first.rpe();
        String weight = first.weight() == null || first.weight().isBlank() || first.weight().equalsIgnoreCase("none")
                ? ""
                : " @ " + first.weight();
        String tags = formatTags(execution);
        String notesText = notes.isBlank() ? "" : " - " + notes;
        return execution.sets().size() + " sets x " + formatMetric(first.metric()) + weight + rpe + tags + notesText;
    }

    private static String formatTags(LiftExecution execution) {
        List<String> tags = new ArrayList<>();
        if (execution.warmup()) {
            tags.add("warm-up");
        }
        if (execution.deload()) {
            tags.add("deload");
        }
        if (tags.isEmpty()) {
            return "";
        }
        return " (" + String.join(", ", tags) + ")";
    }

    private static String formatMetric(SetMetric metric) {
        if (metric instanceof SetMetric.Reps reps) {
            return reps.reps() + " reps";
        }
        if (metric instanceof SetMetric.RepsLr repsLr) {
            return repsLr.left() + "|" + repsLr.right() + " reps";
        }
        if (metric instanceof SetMetric.RepsRange range) {
            return range.min() + "-" + range.max() + " reps";
        }
        if (metric instanceof SetMetric.TimeSecs time) {
            return time.seconds() + " sec";
        }
        if (metric instanceof SetMetric.DistanceFeet dist) {
            return dist.feet() + " ft";
        }
        return "";
    }
}
