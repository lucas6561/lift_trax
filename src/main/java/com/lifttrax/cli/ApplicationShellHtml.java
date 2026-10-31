package com.lifttrax.cli;

/** Shared authenticated application chrome and task-based primary navigation. */
final class ApplicationShellHtml {
  enum Section {
    TODAY("today", "Today", "/?tab=dashboard"),
    TRAIN("train", "Train", "/?tab=add-execution"),
    WORKOUTS("workouts", "Workouts", "/?tab=import-workout"),
    HISTORY("history", "History", "/?tab=executions"),
    EXERCISES("exercises", "Exercises", "/?tab=exercises"),
    ACCOUNT("account", "Account", "/account");

    private final String sectionKey;
    private final String displayLabel;
    private final String destinationHref;

    Section(String key, String label, String href) {
      this.sectionKey = key;
      this.displayLabel = label;
      this.destinationHref = href;
    }

    String key() {
      return sectionKey;
    }

    String label() {
      return displayLabel;
    }

    String href() {
      return destinationHref;
    }

    static Section forTab(String tab) {
      return switch (WebUiRenderer.normalizeTab(tab)) {
        case "add-execution" -> TRAIN;
        case "waves", "import-workout" -> WORKOUTS;
        case "executions", "query", "last-week" -> HISTORY;
        case "exercises" -> EXERCISES;
        default -> TODAY;
      };
    }
  }

  private static final Section[] PRIMARY_SECTIONS = {
    Section.TODAY, Section.TRAIN, Section.WORKOUTS, Section.HISTORY, Section.EXERCISES
  };

  private ApplicationShellHtml() {}

  static String wrapPage(String title, Section section, String body) {
    return wrapPage(title, section, "", "", body);
  }

  static String wrapPage(
      String title, Section section, String parentLabel, String parentHref, String body) {
    String shell =
        "<div class='app-shell' data-current-section='"
            + section.key()
            + "'>"
            + "<a class='skip-link' href='#main-content'>Skip to main content</a>"
            + "<header class='app-header'>"
            + "<a class='app-brand' href='/?tab=dashboard' aria-label='LiftTrax Today'>"
            + "<span class='app-brand-mark' aria-hidden='true'>LT</span>"
            + "<span>LiftTrax</span></a>"
            + "<div class='app-profile-slot' data-profile-slot></div>"
            + "</header>"
            + "<div class='app-frame'>"
            + "<aside class='app-sidebar'>"
            + renderPrimaryNavigation(section, "Primary")
            + "</aside>"
            + "<section class='app-content' id='main-content' tabindex='-1'>"
            + renderBreadcrumbs(title, section, parentLabel, parentHref)
            + body
            + "</section>"
            + "</div>"
            + renderPrimaryNavigation(section, "Primary mobile")
            + "</div>";
    return WebHtml.wrapPage(title + " | LiftTrax", shell);
  }

  private static String renderPrimaryNavigation(Section current, String label) {
    String className =
        "Primary mobile".equals(label) ? "mobile-primary-navigation" : "primary-navigation";
    StringBuilder html =
        new StringBuilder("<nav class='")
            .append(className)
            .append("' aria-label='")
            .append(label)
            .append("'><ul>");
    for (Section section : PRIMARY_SECTIONS) {
      boolean active = section == current;
      html.append("<li><a href='")
          .append(section.href())
          .append("' data-section='")
          .append(section.key())
          .append("'")
          .append(active ? " class='is-current' aria-current='page'" : "")
          .append("><span class='nav-icon' aria-hidden='true'>")
          .append(icon(section))
          .append("</span><span>")
          .append(section.label())
          .append("</span></a></li>");
    }
    return html.append("</ul></nav>").toString();
  }

  private static String renderBreadcrumbs(
      String title, Section section, String parentLabel, String parentHref) {
    StringBuilder html =
        new StringBuilder("<nav class='breadcrumbs' aria-label='Breadcrumb'><ol><li><a href='")
            .append(section.href())
            .append("'>")
            .append(section.label())
            .append("</a></li>");
    if (!parentLabel.isBlank() && !parentHref.isBlank()) {
      html.append("<li><a href='")
          .append(WebHtml.escapeHtml(parentHref))
          .append("'>")
          .append(WebHtml.escapeHtml(parentLabel))
          .append("</a></li>");
    }
    if (!title.equalsIgnoreCase(section.label()) && !title.equalsIgnoreCase(parentLabel)) {
      html.append("<li aria-current='page'>").append(WebHtml.escapeHtml(title)).append("</li>");
    }
    return html.append("</ol></nav>").toString();
  }

  private static String icon(Section section) {
    return switch (section) {
      case TODAY -> "●";
      case TRAIN -> "+";
      case WORKOUTS -> "▤";
      case HISTORY -> "↗";
      case EXERCISES -> "◇";
      case ACCOUNT -> "";
    };
  }
}
