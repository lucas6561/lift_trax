package com.lifttrax.cli;

import static com.lifttrax.cli.WebUiRenderer.*;

import com.lifttrax.db.TrainingDataStore;
import com.lifttrax.models.ExecutionSummaryFormatter;
import com.lifttrax.models.Lift;
import com.lifttrax.models.LiftExecution;
import com.lifttrax.models.LiftStats;
import com.lifttrax.models.Muscle;
import com.lifttrax.models.SetMetric;
import com.lifttrax.workout.LiftTrendSummary;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/** Renders query, filtering, and trend-summary views. */
final class QueryHistoryHtml {
  private QueryHistoryHtml() {}

  static String renderQueryControls(List<Lift> lifts, String selectedLift) {
    StringBuilder options = new StringBuilder("<option value=''>Select a lift</option>");
    for (Lift lift : lifts) {
      boolean selected = lift.name().equals(selectedLift);
      options
          .append("<option value='")
          .append(WebHtml.escapeHtml(lift.name()))
          .append("' data-filter-option data-name='")
          .append(WebHtml.escapeHtml(lift.name()))
          .append("' data-region='")
          .append(WebHtml.escapeHtml(lift.region().toString()))
          .append("' data-main='")
          .append(WebHtml.escapeHtml(formatMainType(lift)))
          .append("' data-muscles='")
          .append(
              WebHtml.escapeHtml(
                  lift.muscles().stream().map(Muscle::name).collect(Collectors.joining(","))))
          .append("'")
          .append(selected ? " selected" : "")
          .append(">")
          .append(WebHtml.escapeHtml(lift.name()))
          .append("</option>");
    }

    return """
                <form method='get' action='/' class='query-form'>
                  <input type='hidden' name='tab' value='query'/>
                  <label>Lift
                    <select name='queryLift'>%s</select>
                  </label>
                  <button type='submit'>Run Query</button>
                </form>
                """
        .formatted(options);
  }

  static String renderQueryContent(TrainingDataStore db, String liftName) {
    if (liftName == null || liftName.isBlank()) {
      return "<p>Select a lift and click <strong>Run Query</strong>.</p>";
    }

    StringBuilder text = new StringBuilder();
    text.append("Last Year\n");
    text.append("========\n");
    try {
      LocalDate oneYearAgo = LocalDate.now().minusDays(365);
      List<LiftExecution> recentExecutions =
          db.liftHistory(liftName).stream()
              .map(com.lifttrax.db.LiftExecutionRow::execution)
              .filter(exec -> !exec.date().isBefore(oneYearAgo))
              .toList();

      if (recentExecutions.isEmpty()) {
        text.append("no records\n");
      } else {
        for (LiftExecution execution : recentExecutions) {
          text.append(formatExecution(execution)).append("\n");
        }
      }
    } catch (Exception e) {
      text.append("Failed to load last-year data: ").append(e.getMessage()).append("\n");
    }

    text.append("\nBest by reps\n");
    text.append("============\n");
    try {
      LiftStats stats = db.liftStats(liftName);
      if (stats.bestByReps().isEmpty()) {
        text.append("no records\n");
      } else {
        stats.bestByReps().entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(
                entry ->
                    text.append(entry.getKey())
                        .append(" reps: ")
                        .append(entry.getValue())
                        .append("\n"));
      }
    } catch (UnsupportedOperationException e) {
      text.append("Not available in the Java port yet.\n");
    } catch (Exception e) {
      text.append("Failed to load best-by-reps data: ").append(e.getMessage()).append("\n");
    }

    return "<pre class='query-output'>" + WebHtml.escapeHtml(text.toString()) + "</pre>";
  }

  static String renderFilterControls(List<Lift> lifts, String search) {
    List<String> regions =
        lifts.stream().map(lift -> lift.region().toString()).distinct().sorted().toList();
    List<String> mainTypes =
        lifts.stream().map(WebUiRenderer::formatMainType).distinct().sorted().toList();
    List<String> muscles =
        lifts.stream()
            .flatMap(lift -> lift.muscles().stream())
            .map(Muscle::name)
            .distinct()
            .sorted()
            .toList();

    StringBuilder regionOptions = new StringBuilder("<option value=''>All regions</option>");
    for (String region : regions) {
      regionOptions
          .append("<option value='")
          .append(WebHtml.escapeHtml(region))
          .append("'>")
          .append(WebHtml.escapeHtml(region))
          .append("</option>");
    }

    StringBuilder mainOptions = new StringBuilder("<option value=''>All main types</option>");
    for (String mainType : mainTypes) {
      mainOptions
          .append("<option value='")
          .append(WebHtml.escapeHtml(mainType))
          .append("'>")
          .append(WebHtml.escapeHtml(mainType))
          .append("</option>");
    }

    StringBuilder muscleOptions = new StringBuilder();
    for (String muscle : muscles) {
      muscleOptions
          .append("<option value='")
          .append(WebHtml.escapeHtml(muscle))
          .append("'>")
          .append(WebHtml.escapeHtml(muscle))
          .append("</option>");
    }

    return """
                <div class='tab-filter-bar'>
                  <label>Search <input class='js-filter-name' type='search' value='%s' placeholder='lift name'/></label>
                  <label>Region <select class='js-filter-region'>%s</select></label>
                  <label>Main <select class='js-filter-main'>%s</select></label>
                  <label>Muscle <select class='js-filter-muscle' multiple size='4' title='Hold Ctrl/Cmd to select multiple'>%s</select></label>
                  <button type='button' class='js-clear-filters'>Clear Filters</button>
                </div>
                """
        .formatted(WebHtml.escapeHtml(search), regionOptions, mainOptions, muscleOptions);
  }

  static String renderLiftList(List<Lift> lifts, String search, String label) {
    StringBuilder liftList = new StringBuilder();
    liftList.append("<p>").append(WebHtml.escapeHtml(label)).append("</p>");
    liftList.append("<ul class='lift-list'>");

    for (Lift lift : lifts) {
      if (!search.isEmpty() && !lift.name().toLowerCase(Locale.ROOT).contains(search)) {
        continue;
      }
      liftList
          .append("<li data-filter-item data-name='")
          .append(WebHtml.escapeHtml(lift.name()))
          .append("' data-region='")
          .append(WebHtml.escapeHtml(lift.region().toString()))
          .append("' data-main='")
          .append(WebHtml.escapeHtml(formatMainType(lift)))
          .append("' data-muscles='")
          .append(
              WebHtml.escapeHtml(
                  lift.muscles().stream().map(Muscle::name).collect(Collectors.joining(","))))
          .append("'><a href='/lift?name=")
          .append(urlEncode(lift.name()))
          .append("'>")
          .append(WebHtml.escapeHtml(lift.name()))
          .append("</a> — ")
          .append(WebHtml.escapeHtml(lift.region().toString()))
          .append(" / ")
          .append(WebHtml.escapeHtml(formatMainType(lift)))
          .append("</li>");
    }

    liftList.append("</ul>");
    return liftList.toString();
  }

  static String renderLiftTrendSummary(List<LiftExecution> executions, LocalDate today) {
    LiftTrendSummary trends = LiftTrendSummary.from(executions, today);
    StringBuilder html = new StringBuilder();
    html.append("<section class='lift-trends'>")
        .append("<div class='lift-trends-header'>")
        .append("<h2>Recent Trends</h2>")
        .append("<p class='muted'>Last ")
        .append(LiftTrendSummary.RECENT_WINDOW_DAYS)
        .append(" days, excluding warm-ups and deloads from bests and volume.</p>")
        .append("</div>");

    if (!trends.hasAnyExecutions()) {
      html.append(
              "<p class='muted'>No trend data yet. Log this lift a few times to build recent bests, volume, and frequency.</p>")
          .append("</section>");
      return html.toString();
    }

    if (!trends.hasRecentExecutions()) {
      html.append("<p class='muted'>No executions in the last ")
          .append(LiftTrendSummary.RECENT_WINDOW_DAYS)
          .append(" days. Last trained ")
          .append(WebHtml.escapeHtml(DATE_FORMAT.format(trends.lastExecution().date())))
          .append(".</p>");
    } else if (trends.hasSparseRecentHistory()) {
      html.append("<p class='muted'>Sparse recent history: ")
          .append(trends.recentExecutions())
          .append(" ")
          .append(plural(trends.recentExecutions(), "session", "sessions"))
          .append(" so far. Trends get clearer after three or more sessions.</p>");
    }

    html.append("<div class='lift-trend-grid'>");
    appendTrendCard(
        html,
        "Last trained",
        DATE_FORMAT.format(trends.lastExecution().date()),
        ExecutionSummaryFormatter.formatCompactSummary(trends.lastExecution()));
    appendTrendCard(html, "Recent best set", bestSetValue(trends), bestSetDetail(trends));
    appendTrendCard(
        html,
        "90-day frequency",
        trends.recentExecutions() + " " + plural(trends.recentExecutions(), "session", "sessions"),
        trends.recentTrainingDays()
            + " "
            + plural(trends.recentTrainingDays(), "training day", "training days"));
    appendTrendCard(
        html,
        "90-day volume",
        trends.recentWorkSets() + " " + plural(trends.recentWorkSets(), "work set", "work sets"),
        volumeDetail(trends));
    html.append("</div></section>");
    return html.toString();
  }

  private static void appendTrendCard(
      StringBuilder html, String label, String value, String detail) {
    html.append("<div class='lift-trend-card'>")
        .append("<p class='lift-trend-label'>")
        .append(WebHtml.escapeHtml(label))
        .append("</p>")
        .append("<p class='lift-trend-value'>")
        .append(WebHtml.escapeHtml(value))
        .append("</p>");
    if (detail != null && !detail.isBlank()) {
      html.append("<p class='lift-trend-detail muted'>")
          .append(WebHtml.escapeHtml(detail))
          .append("</p>");
    }
    html.append("</div>");
  }

  private static String bestSetValue(LiftTrendSummary trends) {
    LiftTrendSummary.BestSet bestSet = trends.bestRecentSet();
    if (bestSet == null) {
      return "No weighted rep best yet";
    }
    return DATE_FORMAT.format(bestSet.date())
        + " - "
        + formatTrendMetric(bestSet.metric())
        + " @ "
        + bestSet.weight();
  }

  private static String bestSetDetail(LiftTrendSummary trends) {
    LiftTrendSummary.BestSet bestSet = trends.bestRecentSet();
    if (bestSet == null) {
      return "Add weighted rep sets to unlock recent bests.";
    }
    String rpe =
        bestSet.rpe() == null ? "" : " RPE " + String.format(Locale.ROOT, "%.1f", bestSet.rpe());
    return String.format(Locale.ROOT, "%,d lb comparison%s", bestSet.pounds(), rpe);
  }

  private static String volumeDetail(LiftTrendSummary trends) {
    if (trends.recentWorkSets() == 0) {
      return "No work sets in the recent window.";
    }
    if (trends.recentRepVolume() == 0) {
      return "Timed or distance sets logged without rep volume.";
    }
    String reps = trends.recentRepVolume() + " " + plural(trends.recentRepVolume(), "rep", "reps");
    if (trends.recentTonnageLbs() <= 0) {
      return reps;
    }
    return reps + " / " + String.format(Locale.ROOT, "%,d lb-reps", trends.recentTonnageLbs());
  }

  private static String formatTrendMetric(SetMetric metric) {
    if (metric instanceof SetMetric.Reps reps) {
      return reps.reps() + " " + plural(reps.reps(), "rep", "reps");
    }
    return formatMetric(metric);
  }

  private static String plural(int count, String singular, String plural) {
    return count == 1 ? singular : plural;
  }
}
