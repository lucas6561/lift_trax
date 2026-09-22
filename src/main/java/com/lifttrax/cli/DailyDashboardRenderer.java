package com.lifttrax.cli;

import com.lifttrax.db.DashboardSnapshot;
import com.lifttrax.db.LiftExecutionRow;
import com.lifttrax.db.TrainingDataStore;
import com.lifttrax.models.ExecutionSummaryFormatter;
import com.lifttrax.models.Lift;
import com.lifttrax.models.LiftExecution;
import java.time.LocalDate;
import java.util.List;

/** Builds the first screen shown by the web UI. */
final class DailyDashboardRenderer {
  private DailyDashboardRenderer() {}

  static String render(TrainingDataStore db, LocalDate today) {
    DashboardSnapshot dashboardData;
    try {
      dashboardData = db.dashboardSnapshot(today);
    } catch (Exception e) {
      return "<p class='status error'>Failed to load dashboard. Please try again.</p>"
          + "<a href='/?tab=dashboard'>Retry</a>";
    }
    StringBuilder html = new StringBuilder();
    html.append("<div class='daily-dashboard'>")
        .append("<section class='dashboard-hero'>")
        .append("<div><p class='dashboard-date'>")
        .append(WebHtml.escapeHtml(WebUiRenderer.DATE_FORMAT.format(today)))
        .append("</p><h2>Today's Training</h2>")
        .append("<p class='muted'>")
        .append(renderSummary(dashboardData.todayCount()))
        .append("</p></div>")
        .append("<a class='compact-btn dashboard-primary-action' href='")
        .append(WebHtml.escapeHtml(logHref(today)))
        .append("'>Log Set</a>")
        .append("</section>");

    if (!dashboardData.hasLifts()) {
      html.append(renderEmptyState(today));
    } else {
      html.append(renderSuggestedWork(today, dashboardData));
    }

    html.append(renderRecentHistory(dashboardData)).append("</div>");
    return html.toString();
  }

  private static String renderSummary(int todayCount) {
    if (todayCount == 0) {
      return "No sets logged today.";
    }
    if (todayCount == 1) {
      return "1 execution logged today.";
    }
    return todayCount + " executions logged today.";
  }

  private static String renderEmptyState(LocalDate today) {
    return """
                <section class='dashboard-section dashboard-empty'>
                  <h3>No lifts yet</h3>
                  <p class='muted'>Create the first lift, then start logging sets.</p>
                  <a class='compact-btn' href='%s'>Add First Lift</a>
                </section>
                """
        .formatted(WebHtml.escapeHtml(logHref(today)));
  }

  private static String renderSuggestedWork(LocalDate today, DashboardSnapshot dashboardData) {
    List<LiftExecutionRow> suggestions = dashboardData.suggestions();

    StringBuilder html = new StringBuilder();
    html.append("<section class='dashboard-section'><h3>Suggested Work</h3>");
    if (suggestions.isEmpty()) {
      html.append("<p class='muted'>No enabled lifts are available.</p></section>");
      return html.toString();
    }

    html.append("<ul class='dashboard-work-list'>");
    for (LiftExecutionRow suggestion : suggestions) {
      html.append(renderSuggestedWorkItem(suggestion, today));
    }
    html.append("</ul></section>");
    return html.toString();
  }

  private static String renderSuggestedWorkItem(LiftExecutionRow suggestion, LocalDate today) {
    Lift lift = suggestion.lift();
    String lastText =
        suggestion.execution() == null
            ? "No recorded sets yet"
            : "Last: "
                + WebUiRenderer.DATE_FORMAT.format(suggestion.execution().date())
                + " - "
                + formatExecution(suggestion.execution());
    return "<li class='dashboard-work-item'>"
        + "<div><a href='"
        + WebHtml.escapeHtml(liftHref(lift))
        + "'><strong>"
        + WebHtml.escapeHtml(lift.name())
        + "</strong></a><p class='muted'>"
        + WebHtml.escapeHtml(lastText)
        + "</p></div>"
        + "<span class='dashboard-pill'>"
        + WebHtml.escapeHtml(formatLiftCategory(lift))
        + "</span>"
        + "<a class='compact-btn' href='"
        + WebHtml.escapeHtml(logHref(lift, today))
        + "'>Log Set</a></li>";
  }

  private static String renderRecentHistory(DashboardSnapshot dashboardData) {
    StringBuilder html = new StringBuilder();
    html.append("<section class='dashboard-section'><h3>Recent History</h3>");
    if (dashboardData.recentExecutions().isEmpty()) {
      html.append("<p class='muted'>No executions in the last 14 days.</p></section>");
      return html.toString();
    }

    html.append("<ul class='dashboard-history-list'>");
    dashboardData.recentExecutions().stream()
        .limit(6)
        .forEach(row -> html.append(renderRecentHistoryItem(row)));
    html.append("</ul></section>");
    return html.toString();
  }

  private static String renderRecentHistoryItem(LiftExecutionRow row) {
    Lift lift = row.lift();
    LiftExecution execution = row.execution();
    return "<li class='dashboard-history-item'>"
        + "<div><a href='"
        + WebHtml.escapeHtml(liftHref(lift))
        + "'><strong>"
        + WebHtml.escapeHtml(lift.name())
        + "</strong></a><p class='muted'>"
        + WebHtml.escapeHtml(WebUiRenderer.DATE_FORMAT.format(execution.date()))
        + "</p></div>"
        + "<span>"
        + WebHtml.escapeHtml(formatExecution(execution))
        + "</span>"
        + "<a class='compact-btn secondary' href='"
        + WebHtml.escapeHtml(logHref(lift, execution.date()))
        + "'>Log Again</a></li>";
  }

  private static String formatLiftCategory(Lift lift) {
    return (lift.region() == null ? "Unknown" : lift.region().toString())
        + " / "
        + WebUiRenderer.formatMainType(lift);
  }

  private static String formatExecution(LiftExecution execution) {
    return ExecutionSummaryFormatter.formatCompactSummary(execution);
  }

  private static String logHref(LocalDate date) {
    return "/?tab=add-execution&prefillDate="
        + WebUiRenderer.urlEncode(WebUiRenderer.DATE_FORMAT.format(date));
  }

  private static String logHref(Lift lift, LocalDate date) {
    return logHref(date) + "&prefillLift=" + WebUiRenderer.urlEncode(lift.name());
  }

  private static String liftHref(Lift lift) {
    return "/lift?name=" + WebUiRenderer.urlEncode(lift.name());
  }
}
