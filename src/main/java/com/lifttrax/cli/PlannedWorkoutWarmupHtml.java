package com.lifttrax.cli;

/** Renders the warm-up guidance embedded in a planned workout session. */
final class PlannedWorkoutWarmupHtml {
  private PlannedWorkoutWarmupHtml() {}

  static void append(StringBuilder html, PlannedWorkoutWarmups.WarmupPlan warmup) {
    if (warmup == null) {
      return;
    }
    html.append("<aside class='session-warmup' aria-label='Warm-up ramp'><h4>")
        .append(WebHtml.escapeHtml(warmup.title()))
        .append(
            "</h4><p class='muted'>Do these before the first programmed work set. Warm-ups do not count as work sets. All percentages are of the planned working weight.</p>");
    if (!warmup.workingWeight().isBlank()) {
      html.append("<p class='muted'>Planned working weight: <strong>")
          .append(WebHtml.escapeHtml(warmup.workingWeight()))
          .append("</strong>. ")
          .append(WebHtml.escapeHtml(warmup.loadingNote()))
          .append("</p>");
    }
    appendSets(html, warmup);
    appendMissingWorkingWeightGuidance(html, warmup);
    if (!warmup.loadWarning().isBlank()) {
      html.append("<p class='session-warmup-load-warning'>")
          .append(WebHtml.escapeHtml(warmup.loadWarning()))
          .append("</p>");
    }
    html.append("<p class='muted'>")
        .append(warmupRest(warmup.workingReps()))
        .append("</p><p class='muted'>")
        .append(WebHtml.escapeHtml(warmup.intent()))
        .append("</p>");
    if (!warmup.backoffNote().isBlank()) {
      html.append("<p class='session-warmup-backoff'><strong>Back-off work:</strong> ")
          .append(WebHtml.escapeHtml(warmup.backoffNote()))
          .append("</p>");
    }
    html.append(
        "<p class='session-warmup-caution'>The final warm-up should generally feel like RPE 6-6.5 or lower. If it is unexpectedly slow, technique deteriorates, you feel pain, or effort reaches RPE 7 or higher, adjust the working weight instead of adding more heavy warm-up reps.</p></aside>");
  }

  private static void appendSets(StringBuilder html, PlannedWorkoutWarmups.WarmupPlan warmup) {
    if (warmup.sets().isEmpty()) {
      html.append("<p class='muted'>No loaded warm-up sets fit below this working weight.</p>");
      return;
    }
    html.append("<ol>");
    for (PlannedWorkoutWarmups.WarmupSet set : warmup.sets()) {
      html.append("<li>")
          .append(WebHtml.escapeHtml(set.load()))
          .append(" &times; ")
          .append(WebHtml.escapeHtml(set.reps()));
      if (!set.suggestedWeight().isBlank()) {
        html.append(" <strong class='session-warmup-weight'>&mdash; ")
            .append(WebHtml.escapeHtml(set.suggestedWeight()))
            .append("</strong>");
      }
      html.append("</li>");
    }
    html.append("</ol>");
  }

  private static void appendMissingWorkingWeightGuidance(
      StringBuilder html, PlannedWorkoutWarmups.WarmupPlan warmup) {
    if (!warmup.workingWeight().isBlank()) {
      return;
    }
    if (warmup.workingWeightError().isBlank()) {
      html.append(
          "<p class='muted'>A working weight could not be calculated from this account's history and planned target. Apply these percentages to the working weight you choose; round to usable loads and check that each step increases while staying below the working weight.</p>");
      return;
    }
    html.append(
            "<p class='status error planned-load-error'><strong>Working weight error:</strong> ")
        .append(WebHtml.escapeHtml(warmup.workingWeightError()))
        .append(" Do not substitute the performed exercise's max for the referenced lift.</p>");
  }

  private static String warmupRest(int workingReps) {
    String early =
        "Rest 45-90 seconds after early warm-ups and 60-120 seconds after middle stages. ";
    if (workingReps <= 3) {
      return early + "Rest 2-4 minutes after the final bridge before the work set.";
    }
    if (workingReps <= 10) {
      return early + "Rest 1.5-3 minutes after the final bridge before the work set.";
    }
    return early + "Before high-rep work, rest until ready without cooling down.";
  }
}
