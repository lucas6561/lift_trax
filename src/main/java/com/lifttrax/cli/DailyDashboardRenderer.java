package com.lifttrax.cli;

import com.lifttrax.db.LiftExecutionRow;
import com.lifttrax.db.TrainingDataStore;
import com.lifttrax.models.ExecutionSummaryFormatter;
import com.lifttrax.models.Lift;
import com.lifttrax.models.LiftExecution;
import com.lifttrax.models.LiftType;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Builds the first screen shown by the web UI. */
final class DailyDashboardRenderer {
  private DailyDashboardRenderer() {}

  static String render(TrainingDataStore db, List<Lift> lifts, LocalDate today) {
    DashboardData dashboardData = loadDashboardData(db, lifts, today.minusDays(13), today);
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

    if (lifts.isEmpty()) {
      html.append(renderEmptyState(today));
    } else {
      html.append(renderSuggestedWork(lifts, today, dashboardData));
    }

    html.append(renderRecentHistory(dashboardData)).append("</div>");
    return html.toString();
  }

  private static DashboardData loadDashboardData(
      TrainingDataStore db, List<Lift> lifts, LocalDate start, LocalDate today) {
    Map<String, Boolean> enabledStatuses = loadEnabledStatuses(db);
    Map<String, LiftExecution> latestExecutions = loadLatestExecutions(db);
    try {
      List<RecentExecution> recent = recentExecutions(db, lifts, start, today);
      int todayCount =
          (int) recent.stream().filter(row -> today.equals(row.execution().date())).count();
      return new DashboardData(todayCount, enabledStatuses, latestExecutions, recent, "");
    } catch (Exception e) {
      return new DashboardData(-1, enabledStatuses, latestExecutions, List.of(), e.getMessage());
    }
  }

  private static Map<String, Boolean> loadEnabledStatuses(TrainingDataStore db) {
    try {
      return db.liftEnabledStatuses();
    } catch (Exception ignored) {
      return Map.of();
    }
  }

  private static Map<String, LiftExecution> loadLatestExecutions(TrainingDataStore db) {
    try {
      return db.latestExecutionsByLift();
    } catch (Exception ignored) {
      return Map.of();
    }
  }

  private static String renderSummary(int todayCount) {
    if (todayCount < 0) {
      return "Today is ready.";
    }
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

  private static String renderSuggestedWork(
      List<Lift> lifts, LocalDate today, DashboardData dashboardData) {
    List<LiftSuggestion> suggestions =
        lifts.stream()
            .filter(lift -> dashboardData.enabledStatuses().getOrDefault(lift.name(), true))
            .map(
                lift -> new LiftSuggestion(lift, dashboardData.latestExecutions().get(lift.name())))
            .sorted(
                Comparator.comparingInt(
                        (LiftSuggestion suggestion) -> liftSortOrder(suggestion.lift()))
                    .thenComparing(DailyDashboardRenderer::lastExecutionDate)
                    .thenComparing(suggestion -> suggestion.lift().name()))
            .limit(4)
            .toList();

    StringBuilder html = new StringBuilder();
    html.append("<section class='dashboard-section'><h3>Suggested Work</h3>");
    if (suggestions.isEmpty()) {
      html.append("<p class='muted'>No enabled lifts are available.</p></section>");
      return html.toString();
    }

    html.append("<ul class='dashboard-work-list'>");
    for (LiftSuggestion suggestion : suggestions) {
      html.append(renderSuggestedWorkItem(suggestion, today));
    }
    html.append("</ul></section>");
    return html.toString();
  }

  private static String renderSuggestedWorkItem(LiftSuggestion suggestion, LocalDate today) {
    Lift lift = suggestion.lift();
    String lastText =
        suggestion.lastExecution() == null
            ? "No recorded sets yet"
            : "Last: "
                + WebUiRenderer.DATE_FORMAT.format(suggestion.lastExecution().date())
                + " - "
                + formatExecution(suggestion.lastExecution());
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

  private static String renderRecentHistory(DashboardData dashboardData) {
    if (!dashboardData.recentError().isBlank()) {
      return "<section class='dashboard-section'><h3>Recent History</h3>"
          + "<p class='status error'>Failed to load recent history: "
          + WebHtml.escapeHtml(dashboardData.recentError())
          + "</p></section>";
    }

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

  private static String renderRecentHistoryItem(RecentExecution row) {
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

  private static List<RecentExecution> recentExecutions(
      TrainingDataStore db, List<Lift> lifts, LocalDate start, LocalDate end) throws Exception {
    Map<String, Lift> liftsByName =
        lifts.stream().collect(Collectors.toMap(Lift::name, lift -> lift, (left, right) -> left));
    List<RecentExecution> recent = new ArrayList<>();
    for (LiftExecutionRow row : db.getExecutionsBetween(start, end)) {
      Lift lift = liftsByName.getOrDefault(row.lift().name(), row.lift());
      recent.add(new RecentExecution(lift, row.execution()));
    }
    recent.sort(DailyDashboardRenderer::compareRecentExecution);
    return recent;
  }

  private static int compareRecentExecution(RecentExecution left, RecentExecution right) {
    int dateOrder = right.execution().date().compareTo(left.execution().date());
    if (dateOrder != 0) {
      return dateOrder;
    }
    return Integer.compare(executionId(right.execution()), executionId(left.execution()));
  }

  private static int executionId(LiftExecution execution) {
    return execution.id() == null ? 0 : execution.id();
  }

  private static LocalDate lastExecutionDate(LiftSuggestion suggestion) {
    LiftExecution execution = suggestion.lastExecution();
    return execution == null ? LocalDate.MIN : execution.date();
  }

  private static int liftSortOrder(Lift lift) {
    LiftType mainType = lift.main();
    if (mainType == null) {
      return 50;
    }
    return switch (mainType) {
      case SQUAT -> 10;
      case DEADLIFT -> 20;
      case BENCH_PRESS -> 30;
      case OVERHEAD_PRESS -> 40;
      case ACCESSORY -> 50;
      case CONDITIONING -> 60;
      case MOBILITY -> 70;
    };
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

  private record DashboardData(
      int todayCount,
      Map<String, Boolean> enabledStatuses,
      Map<String, LiftExecution> latestExecutions,
      List<RecentExecution> recentExecutions,
      String recentError) {}

  private record LiftSuggestion(Lift lift, LiftExecution lastExecution) {}

  private record RecentExecution(Lift lift, LiftExecution execution) {}
}
