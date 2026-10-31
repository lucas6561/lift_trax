package com.lifttrax.cli;

import com.lifttrax.models.Muscle;

/** Renders root task context, secondary navigation, and task-specific entry controls. */
final class TaskNavigationHtml {
  private TaskNavigationHtml() {}

  static String renderSectionHeading(String activeTab) {
    ApplicationShellHtml.Section section = ApplicationShellHtml.Section.forTab(activeTab);
    String description =
        switch (section) {
          case TODAY -> "Drafts, recent activity, and a useful next step.";
          case TRAIN -> "Quickly log training or continue a planned workout.";
          case WORKOUTS -> "Use saved workouts, import a plan, or generate a wave.";
          case HISTORY -> "Review executions, date ranges, queries, and lift trends.";
          case EXERCISES -> "Manage the exercise catalog, details, sharing, and imports.";
          case ACCOUNT -> "Manage your LiftTrax account.";
        };
    String action =
        switch (section) {
          case TODAY -> "<a class='compact-btn' href='/?tab=add-execution'>Log training</a>";
          case TRAIN ->
              "<a class='compact-btn secondary' href='/?tab=import-workout'>Choose a workout</a>";
          case WORKOUTS ->
              "<a class='compact-btn' href='/?tab=import-workout#workout-upload'>Import workout</a>";
          case HISTORY ->
              "<a class='compact-btn secondary' href='/?tab=last-week'>View this week</a>";
          case EXERCISES -> "<a class='compact-btn' href='#new-exercise'>Add exercise</a>";
          case ACCOUNT -> "";
        };
    return "<header class='page-heading'><div><h1>"
        + section.label()
        + "</h1><p class='muted'>"
        + description
        + "</p></div><div class='compact-actions'>"
        + action
        + "</div></header>"
        + renderSecondaryNavigation(activeTab, section);
  }

  static String renderTrainIntro(String resumePanel) {
    return resumePanel
        + "<section class='task-callout'><div><h2>Work Along</h2>"
        + "<p class='muted'>Start or resume a saved planned workout with guided set logging.</p></div>"
        + "<a class='compact-btn secondary' href='/?tab=import-workout'>Choose workout</a></section>";
  }

  static String renderStatusMessage(String message, String type) {
    if (message == null || message.isBlank()) {
      return "";
    }
    String statusClass = "error".equalsIgnoreCase(type) ? "error" : "success";
    return "<p class='status "
        + statusClass
        + "' role='status' aria-live='polite'>"
        + WebHtml.escapeHtml(message)
        + "</p>";
  }

  static String renderNewLiftForm() {
    StringBuilder muscleOptions = new StringBuilder();
    for (Muscle muscle : Muscle.values()) {
      muscleOptions
          .append("<option value='")
          .append(WebHtml.escapeHtml(muscle.name()))
          .append("'>")
          .append(WebHtml.escapeHtml(muscle.name()))
          .append("</option>");
    }
    return """
        <details class='new-lift-details' id='new-exercise'>
          <summary>Add exercise</summary>
          <form method='post' action='/add-lift' class='new-lift-form'>
            <input type='hidden' name='tab' value='exercises'/>
            <label>Name <input type='text' name='name' required data-focus-target='add-lift'/></label>
            <label>Region
              <select name='region'><option value='UPPER'>UPPER</option><option value='LOWER'>LOWER</option></select>
            </label>
            <label>Main
              <select name='main'>
                <option value='ACCESSORY'>ACCESSORY</option><option value='BENCH PRESS'>BENCH PRESS</option>
                <option value='CONDITIONING'>CONDITIONING</option><option value='DEADLIFT'>DEADLIFT</option>
                <option value='MOBILITY'>MOBILITY</option><option value='OVERHEAD PRESS'>OVERHEAD PRESS</option>
                <option value='SQUAT'>SQUAT</option>
              </select>
            </label>
            <label>Muscles
              <select class='js-new-lift-muscles' multiple size='6' title='Hold Ctrl/Cmd to select multiple'>%s</select>
              <input type='hidden' name='muscles' class='js-new-lift-muscles-hidden' value=''/>
            </label>
            <label>Notes <input type='text' name='notes' placeholder='Optional notes'/></label>
            <button type='submit'>Create Exercise</button>
          </form>
        </details>
        """
        .formatted(muscleOptions);
  }

  private static String renderSecondaryNavigation(
      String activeTab, ApplicationShellHtml.Section section) {
    if (section == ApplicationShellHtml.Section.WORKOUTS) {
      return "<nav class='secondary-navigation' aria-label='Workouts views'><ul>"
          + secondaryLink(
              "/?tab=import-workout", "Saved & Import", "import-workout".equals(activeTab))
          + secondaryLink("/?tab=waves", "Wave Generation", "waves".equals(activeTab))
          + "</ul></nav>";
    }
    if (section == ApplicationShellHtml.Section.HISTORY) {
      return "<nav class='secondary-navigation' aria-label='History views'><ul>"
          + secondaryLink("/?tab=executions", "Executions", "executions".equals(activeTab))
          + secondaryLink("/?tab=last-week", "Weekly", "last-week".equals(activeTab))
          + secondaryLink("/?tab=query", "Query", "query".equals(activeTab))
          + "</ul></nav>";
    }
    return "";
  }

  private static String secondaryLink(String href, String label, boolean current) {
    return "<li><a href='"
        + href
        + "'"
        + (current ? " aria-current='page'" : "")
        + ">"
        + label
        + "</a></li>";
  }
}
