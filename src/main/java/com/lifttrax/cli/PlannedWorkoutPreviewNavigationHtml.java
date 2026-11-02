package com.lifttrax.cli;

import com.lifttrax.workout.PlannedWorkoutFile;

/** Week targets and progressive enhancement for App Preview only. */
final class PlannedWorkoutPreviewNavigationHtml {
  private PlannedWorkoutPreviewNavigationHtml() {}

  static String render(PlannedWorkoutFile workout) {
    if (workout.weeks().size() < 2) {
      return "";
    }
    StringBuilder html =
        new StringBuilder("<nav class='preview-week-navigation' aria-label='Preview weeks'>");
    html.append("<div class='preview-week-controls' hidden>")
        .append(
            "<button type='button' class='secondary preview-week-previous' disabled>Previous Week</button>")
        .append("<label for='preview-week-select'>Preview week<select id='preview-week-select'>");
    for (var week : workout.weeks()) {
      html.append("<option value='preview-week-")
          .append(week.weekNumber())
          .append("'>Week ")
          .append(week.weekNumber())
          .append("</option>");
    }
    html.append("</select></label>")
        .append("<button type='button' class='secondary preview-week-next'>Next Week</button>")
        .append("<span class='preview-week-current' role='status'></span></div>")
        .append("<div class='preview-week-links'>Jump to week: ");
    for (var week : workout.weeks()) {
      html.append("<a href='#preview-week-")
          .append(week.weekNumber())
          .append("'>Week ")
          .append(week.weekNumber())
          .append("</a> ");
    }
    return html.append("</div></nav>").toString();
  }

  static String script() {
    return ClasspathText.script("preview-weeks");
  }
}
