package com.lifttrax.cli;

/** Renders and restores device-local planned-workout drafts. */
final class PlannedWorkoutResumeHtml {
  private PlannedWorkoutResumeHtml() {}

  static String renderResumePanel(String ownerUserId) {
    String draftPrefix = PlannedWorkoutSessionHtml.draftPrefix(ownerUserId);
    String legacyDraftPrefix = PlannedWorkoutSessionHtml.legacyDraftPrefix(ownerUserId);
    return ClasspathText.read("planned-workout-resume-panel.html")
            .formatted(WebHtml.escapeHtml(draftPrefix), WebHtml.escapeHtml(legacyDraftPrefix))
        + ClasspathText.script("resume-panel");
  }

  static String renderResumePage(String draftKey, String ownerUserId) {
    if (draftKey == null
        || draftKey.isBlank()
        || (!draftKey.startsWith(PlannedWorkoutSessionHtml.draftPrefix(ownerUserId))
            && !draftKey.startsWith(PlannedWorkoutSessionHtml.legacyDraftPrefix(ownerUserId)))) {
      return """
          <h1>Workout backup unavailable</h1>
          <p class='status error'>This workout backup does not belong to the signed-in account or is no longer available.</p>
          <p><a class='compact-btn' href='/?tab=dashboard'>Back to Dashboard</a></p>
          """;
    }
    return """
        <h1>Resume Workout</h1>
        <p class='status muted js-session-resume-status'>Loading the workout saved on this phone...</p>
        <button type='button' class='secondary js-session-resume-retry is-hidden'>Retry</button>
        <p><a href='/?tab=dashboard'>Back to Dashboard</a></p>
        <div class='js-session-resume' data-draft-key='%s' data-owner-scope='%s'></div>
        %s
        """
        .formatted(
            WebHtml.escapeHtml(draftKey),
            WebHtml.escapeHtml(PlannedWorkoutSessionHtml.draftPrefix(ownerUserId)),
            ClasspathText.script("resume"));
  }
}
