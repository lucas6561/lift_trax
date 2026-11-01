package com.lifttrax.cli;

import static com.lifttrax.cli.WebUiRenderer.*;

import com.lifttrax.db.TrainingDataStore;
import com.lifttrax.models.ExecutionSummaryFormatter;
import com.lifttrax.models.Lift;
import com.lifttrax.models.LiftExecution;
import com.lifttrax.models.LiftType;
import com.lifttrax.models.Muscle;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Renders recent execution history grouped by training date. */
final class LastWeekHtml {
  private LastWeekHtml() {}

  static String renderLastWeekContent(
      TrainingDataStore db, List<Lift> lifts, LocalDate start, LocalDate end) {
    return renderLastWeekContent(db, start, end, "");
  }

  static String renderLastWeekContent(
      TrainingDataStore db, LocalDate start, LocalDate end, String search) {
    LocalDate normalizedStart = start.isAfter(end) ? end : start;
    LocalDate normalizedEnd = end.isBefore(start) ? start : end;
    StringBuilder html = new StringBuilder();
    html.append("<p>Showing ")
        .append(WebHtml.escapeHtml(DATE_FORMAT.format(normalizedStart)))
        .append(" through ")
        .append(WebHtml.escapeHtml(DATE_FORMAT.format(normalizedEnd)))
        .append("</p>");

    Map<LocalDate, List<LastWeekExecutionRow>> rowsByDate = new LinkedHashMap<>();
    List<Lift> performedLifts = new ArrayList<>();
    com.lifttrax.db.ExecutionHistorySummary history;
    try {
      history = db.executionHistorySummary(normalizedStart, normalizedEnd);
      collectRows(
          db.getExecutionsBetween(normalizedStart, normalizedEnd),
          performedLifts,
          rowsByDate,
          normalizedStart,
          normalizedEnd);
    } catch (Exception e) {
      return "<p class='status error'>Failed to load last-week view: "
          + WebHtml.escapeHtml(e.getMessage())
          + "</p>";
    }
    html.insert(0, renderFilterControls(performedLifts, search));
    appendHistoryAvailability(html, history);

    if (rowsByDate.isEmpty()) {
      appendEmptyRange(html, history);
      return html.toString();
    }

    appendDays(html, rowsByDate);
    return html.toString();
  }

  private static void collectRows(
      List<com.lifttrax.db.LiftExecutionRow> rows,
      List<Lift> performedLifts,
      Map<LocalDate, List<LastWeekExecutionRow>> rowsByDate,
      LocalDate rangeStart,
      LocalDate rangeEnd) {
    for (com.lifttrax.db.LiftExecutionRow row : rows) {
      Lift lift = row.lift();
      performedLifts.add(lift);
      LiftExecution execution = row.execution();
      if (execution.id() == null) {
        continue;
      }
      rowsByDate
          .computeIfAbsent(execution.date(), ignored -> new ArrayList<>())
          .add(
              new LastWeekExecutionRow(
                  lift.name(),
                  executionSortOrder(lift, execution),
                  renderLastWeekExecutionItem(lift, execution, rangeStart, rangeEnd)));
    }
  }

  private static void appendHistoryAvailability(
      StringBuilder html, com.lifttrax.db.ExecutionHistorySummary history) {
    if (history.minDate() != null && history.maxDate() != null) {
      html.append("<p class='muted'>History available: ")
          .append(history.count())
          .append(" total records from ")
          .append(WebHtml.escapeHtml(DATE_FORMAT.format(history.minDate())))
          .append(" through ")
          .append(WebHtml.escapeHtml(DATE_FORMAT.format(history.maxDate())))
          .append("</p>");
    } else {
      html.append("<p class='muted'>History available: no execution records found</p>");
    }
  }

  private static void appendEmptyRange(
      StringBuilder html, com.lifttrax.db.ExecutionHistorySummary history) {
    html.append("<p>no executions in this range (check the date range and filters)</p>");
    if (history.nearestBefore() != null) {
      html.append("<p class='muted'>Closest earlier record in current filters: ")
          .append(WebHtml.escapeHtml(DATE_FORMAT.format(history.nearestBefore())))
          .append("</p>");
    }
    if (history.nearestAfter() != null) {
      html.append("<p class='muted'>Closest later record in current filters: ")
          .append(WebHtml.escapeHtml(DATE_FORMAT.format(history.nearestAfter())))
          .append("</p>");
    }
  }

  private static void appendDays(
      StringBuilder html, Map<LocalDate, List<LastWeekExecutionRow>> rowsByDate) {
    rowsByDate.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(
            entry -> {
              html.append("<section class='last-week-day'>")
                  .append("<h4>")
                  .append(WebHtml.escapeHtml(DATE_FORMAT.format(entry.getKey())))
                  .append("</h4>")
                  .append("<ul class='execution-list'>");
              entry.getValue().stream()
                  .sorted(
                      Comparator.comparingInt(LastWeekExecutionRow::sortOrder)
                          .thenComparing(LastWeekExecutionRow::liftName)
                          .thenComparing(LastWeekExecutionRow::html))
                  .forEach(row -> html.append(row.html()));
              html.append("</ul></section>");
            });
  }

  private static String renderLastWeekExecutionItem(
      Lift lift, LiftExecution execution, LocalDate rangeStart, LocalDate rangeEnd) {
    String notes = execution.notes() == null ? "" : execution.notes();
    String initialSetsJson = ExecutionListHtml.setsToEditJson(execution.sets());
    return "<li class='execution-item' style='margin:6px 0;' data-filter-item data-name='"
        + WebHtml.escapeHtml(lift.name())
        + "' data-region='"
        + WebHtml.escapeHtml(lift.region().toString())
        + "' data-main='"
        + WebHtml.escapeHtml(formatMainType(lift))
        + "' data-muscles='"
        + WebHtml.escapeHtml(
            lift.muscles().stream().map(Muscle::name).collect(Collectors.joining(",")))
        + "'>"
        + "<div class='js-exec-view' style='display:flex;align-items:center;gap:8px;flex-wrap:nowrap;'>"
        + "<span class='execution-text' style='white-space:nowrap;overflow:hidden;text-overflow:ellipsis;flex:1;'>"
        + WebHtml.escapeHtml(lift.name() + " — " + formatExecutionSummary(execution))
        + "</span>"
        + "<div class='execution-row-actions'>"
        + "<button type='button' class='secondary compact-btn js-exec-edit'>Edit</button>"
        + "<button type='button' class='secondary danger compact-btn js-exec-delete'>Delete</button>"
        + "</div>"
        + "<form method='post' action='/delete-execution' class='query-form execution-delete-form js-exec-delete-form' style='display:none;'>"
        + "<input type='hidden' name='executionId' value='"
        + execution.id()
        + "'/>"
        + "<input type='hidden' name='tab' value='last-week'/>"
        + "<input type='hidden' name='lastWeekStart' value='"
        + WebHtml.escapeHtml(DATE_FORMAT.format(rangeStart))
        + "'/>"
        + "<input type='hidden' name='lastWeekEnd' value='"
        + WebHtml.escapeHtml(DATE_FORMAT.format(rangeEnd))
        + "'/>"
        + "</form>"
        + "</div>"
        + "<form method='post' action='/update-execution' class='query-form execution-edit-form js-exec-form' data-initial-sets='"
        + WebHtml.escapeHtml(initialSetsJson)
        + "' style='display:none;flex-direction:column;align-items:flex-start;gap:8px;'>"
        + "<input type='hidden' name='lift' value='"
        + WebHtml.escapeHtml(lift.name())
        + "'/>"
        + "<input type='hidden' name='executionId' value='"
        + execution.id()
        + "'/>"
        + "<input type='hidden' name='tab' value='last-week'/>"
        + "<input type='hidden' name='lastWeekStart' value='"
        + WebHtml.escapeHtml(DATE_FORMAT.format(rangeStart))
        + "'/>"
        + "<input type='hidden' name='lastWeekEnd' value='"
        + WebHtml.escapeHtml(DATE_FORMAT.format(rangeEnd))
        + "'/>"
        + "<div class='execution-edit-meta' style='display:flex;align-items:center;gap:8px;flex-wrap:nowrap;overflow-x:auto;'>"
        + "<label>Date <input type='date' name='date' disabled value='"
        + WebHtml.escapeHtml(DATE_FORMAT.format(execution.date()))
        + "'/></label>"
        + "<label>Notes <input type='text' name='notes' style='min-width:220px;' disabled value='"
        + WebHtml.escapeHtml(notes)
        + "'/></label>"
        + "<label><input type='checkbox' name='warmup' disabled"
        + (execution.warmup() ? " checked" : "")
        + "/> Warm-up</label>"
        + "<label><input type='checkbox' name='deload' disabled"
        + (execution.deload() ? " checked" : "")
        + "/> Deload</label>"
        + "</div>"
        + "<div class='js-edit-sets' style='display:flex;flex-direction:column;gap:6px;width:100%;'>"
        + ExecutionListHtml.renderSetEditorRows(execution.sets())
        + "</div>"
        + "<button type='button' class='secondary compact-btn js-add-set'>Add Set</button>"
        + "<input type='hidden' name='detailedSets' class='js-detailed-sets' disabled value=''/>"
        + "<button type='submit'>Save</button>"
        + "<button type='button' class='secondary compact-btn js-exec-cancel'>Cancel</button>"
        + "</form>"
        + "</li>";
  }

  private static int executionSortOrder(Lift lift, LiftExecution execution) {
    if (execution.warmup()) {
      return 0;
    }
    return liftSortOrder(lift);
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

  private static String formatExecutionSummary(LiftExecution execution) {
    return ExecutionSummaryFormatter.formatCompactSummary(execution);
  }

  private record LastWeekExecutionRow(String liftName, int sortOrder, String html) {}

  /**
   * Builds the Add Execution form, including weight mode controls and metric controls. Uses {@link
   * WeightInputParser} so rendering code does not need to parse raw weight text.
   */
}
