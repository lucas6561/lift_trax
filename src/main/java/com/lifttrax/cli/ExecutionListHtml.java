package com.lifttrax.cli;

import static com.lifttrax.cli.WebUiRenderer.*;

import com.lifttrax.db.TrainingDataStore;
import com.lifttrax.models.ExecutionSet;
import com.lifttrax.models.ExecutionSummaryFormatter;
import com.lifttrax.models.Lift;
import com.lifttrax.models.LiftExecution;
import com.lifttrax.models.LiftRegion;
import com.lifttrax.models.LiftType;
import com.lifttrax.models.Muscle;
import com.lifttrax.models.SetMetric;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/** Renders execution lists, editors, and metric summaries. */
final class ExecutionListHtml {
  private ExecutionListHtml() {}

  static String renderExecutionList(
      TrainingDataStore db, List<Lift> lifts, String search, String label) {
    return renderExecutionList(db, lifts, search, label, "executions");
  }

  static String renderExecutionList(
      TrainingDataStore db, List<Lift> lifts, String search, String label, String returnTab) {
    StringBuilder html = new StringBuilder();
    html.append("<p>").append(WebHtml.escapeHtml(label)).append("</p>");

    Map<String, Boolean> enabledStatuses;
    try {
      enabledStatuses = db.liftEnabledStatuses();
    } catch (Exception ignored) {
      enabledStatuses = Map.of();
    }

    List<Lift> visibleLifts = lifts.stream().filter(lift -> matchesSearch(lift, search)).toList();
    for (Lift lift : visibleLifts) {
      appendLift(html, lift, enabledStatuses.getOrDefault(lift.name(), true), returnTab);
    }
    if (visibleLifts.isEmpty()) {
      html.append("<p>No lifts found for current filters.</p>");
    }
    return html.toString();
  }

  private static boolean matchesSearch(Lift lift, String search) {
    return search.isEmpty() || lift.name().toLowerCase(Locale.ROOT).contains(search);
  }

  private static void appendLift(StringBuilder html, Lift lift, boolean enabled, String returnTab) {
    html.append("<details class='execution-lift-group' data-filter-item data-name='")
        .append(WebHtml.escapeHtml(lift.name()))
        .append("' data-region='")
        .append(WebHtml.escapeHtml(lift.region().toString()))
        .append("' data-main='")
        .append(WebHtml.escapeHtml(formatMainType(lift)))
        .append("' data-muscles='")
        .append(
            WebHtml.escapeHtml(
                lift.muscles().stream().map(Muscle::name).collect(Collectors.joining(","))))
        .append("'>");
    appendLiftSummary(html, lift, enabled);
    appendLiftActions(html, lift, enabled, returnTab);
    appendLiftEditor(html, lift, returnTab);
    appendLiftDelete(html, lift, returnTab);
    html.append("<div class='js-exec-body' data-lift='")
        .append(WebHtml.escapeHtml(lift.name()))
        .append("' data-loaded='false'><p>Expand to load executions...</p></div>")
        .append("</details>");
  }

  private static void appendLiftSummary(StringBuilder html, Lift lift, boolean enabled) {
    html.append("<summary class='execution-lift-toggle'>")
        .append(WebHtml.escapeHtml(lift.name()))
        .append(" - ")
        .append(WebHtml.escapeHtml(lift.region().toString()))
        .append(" / ")
        .append(WebHtml.escapeHtml(formatMainType(lift)));
    if (!enabled) {
      html.append(" <span class='status error' style='margin-left:8px;'>Disabled</span>");
    }
    html.append("</summary>");
  }

  private static void appendLiftActions(
      StringBuilder html, Lift lift, boolean enabled, String returnTab) {
    html.append("<div class='compact-actions' style='margin:8px 0;'>")
        .append("<a class='compact-btn secondary' href='/lift?name=")
        .append(urlEncode(lift.name()))
        .append("'>Details</a></div>")
        .append(
            "<form method='post' action='/set-lift-enabled' class='query-form compact-actions' style='margin:8px 0;'>")
        .append("<input type='hidden' name='lift' value='")
        .append(WebHtml.escapeHtml(lift.name()))
        .append("'/><input type='hidden' name='enabled' value='")
        .append(enabled ? "0" : "1")
        .append("'/><input type='hidden' name='tab' value='")
        .append(WebHtml.escapeHtml(returnTab))
        .append("'/><button type='submit' class='compact-btn'>")
        .append(enabled ? "Disable for wave" : "Enable for wave")
        .append("</button></form>");
  }

  private static void appendLiftEditor(StringBuilder html, Lift lift, String returnTab) {
    html.append("<details style='margin:8px 0;'><summary>Edit lift</summary>")
        .append(
            "<form method='post' action='/update-lift' class='query-form compact-actions' style='margin:8px 0;'>")
        .append("<input type='hidden' name='tab' value='")
        .append(WebHtml.escapeHtml(returnTab))
        .append("'/><input type='hidden' name='currentName' value='")
        .append(WebHtml.escapeHtml(lift.name()))
        .append("'/><label>Name <input type='text' name='name' required value='")
        .append(WebHtml.escapeHtml(lift.name()))
        .append("'/></label><label>Region <select name='region'>")
        .append(renderLiftRegionOptions(lift.region()))
        .append("</select></label><label>Main <select name='main'>")
        .append(renderLiftMainOptions(lift.main()))
        .append("</select></label><label>Muscles")
        .append("<select multiple size='6' ")
        .append("class='js-catalog-muscles'>")
        .append(renderLiftMuscleOptions(lift))
        .append("</select><input type='hidden' name='muscles' value='")
        .append(
            WebHtml.escapeHtml(
                lift.muscles().stream().map(Muscle::name).collect(Collectors.joining(","))))
        .append("'/></label><label>Notes <input type='text' name='notes' value='")
        .append(WebHtml.escapeHtml(lift.notes() == null ? "" : lift.notes()))
        .append("'/></label><button type='submit' class='compact-btn'>Save lift</button>")
        .append("</form></details>");
  }

  private static void appendLiftDelete(StringBuilder html, Lift lift, String returnTab) {
    html.append(
            "<form method='post' action='/delete-lift' class='query-form compact-actions' style='margin:8px 0;' ")
        .append(
            "data-confirm='Delete this catalog lift? Completed executions and sets will be retained in history.'>")
        .append("<input type='hidden' name='tab' value='")
        .append(WebHtml.escapeHtml(returnTab))
        .append("'/><input type='hidden' name='lift' value='")
        .append(WebHtml.escapeHtml(lift.name()))
        .append("'/><button type='submit' class='danger compact-btn'>Delete lift</button>")
        .append("</form>");
  }

  private static String renderLiftRegionOptions(LiftRegion selected) {
    StringBuilder html = new StringBuilder();
    for (LiftRegion region : LiftRegion.values()) {
      html.append("<option value='")
          .append(WebHtml.escapeHtml(region.name()))
          .append("'")
          .append(region == selected ? " selected" : "")
          .append(">")
          .append(WebHtml.escapeHtml(region.name()))
          .append("</option>");
    }
    return html.toString();
  }

  private static String renderLiftMainOptions(LiftType selected) {
    StringBuilder html = new StringBuilder();
    for (LiftType type : LiftType.values()) {
      html.append("<option value='")
          .append(WebHtml.escapeHtml(type.toDbValue()))
          .append("'")
          .append(type == selected ? " selected" : "")
          .append(">")
          .append(WebHtml.escapeHtml(type.toDbValue()))
          .append("</option>");
    }
    return html.toString();
  }

  private static String renderLiftMuscleOptions(Lift lift) {
    StringBuilder html = new StringBuilder();
    for (Muscle muscle : Muscle.values()) {
      html.append("<option value='")
          .append(WebHtml.escapeHtml(muscle.name()))
          .append("'")
          .append(lift.muscles().contains(muscle) ? " selected" : "")
          .append(">")
          .append(WebHtml.escapeHtml(muscle.name()))
          .append("</option>");
    }
    return html.toString();
  }

  static String renderExecutionRows(TrainingDataStore db, String liftName) {
    try {
      List<com.lifttrax.db.LiftExecutionRow> history = db.liftHistory(liftName);
      List<LiftExecution> executions = sortedExecutions(history);
      if (executions.isEmpty()) {
        return "<p>No executions recorded.</p>";
      }
      return renderExecutionRows(
          liftName, executions, performedNames(history), liftEnabled(db, liftName));
    } catch (Exception e) {
      return "<div class='status error'>"
          + WebHtml.escapeHtml("Failed to load executions: " + e.getMessage())
          + "</div>";
    }
  }

  private static List<LiftExecution> sortedExecutions(
      List<com.lifttrax.db.LiftExecutionRow> history) {
    return history.stream()
        .map(com.lifttrax.db.LiftExecutionRow::execution)
        .sorted(
            java.util.Comparator.comparing(LiftExecution::date)
                .thenComparing(LiftExecution::id, Comparator.nullsLast(Integer::compareTo))
                .reversed())
        .toList();
  }

  private static Map<Integer, String> performedNames(
      List<com.lifttrax.db.LiftExecutionRow> history) {
    Map<Integer, String> names = new java.util.HashMap<>();
    for (com.lifttrax.db.LiftExecutionRow row : history) {
      names.put(row.execution().id(), row.lift().name());
    }
    return names;
  }

  private static boolean liftEnabled(TrainingDataStore db, String liftName) {
    try {
      return db.isLiftEnabled(liftName);
    } catch (Exception ignored) {
      return true;
    }
  }

  private static String renderExecutionRows(
      String liftName,
      List<LiftExecution> executions,
      Map<Integer, String> performedNames,
      boolean liftEnabled) {
    StringBuilder html = new StringBuilder();
    html.append("<p class='status ")
        .append(liftEnabled ? "success" : "error")
        .append("'>Wave status: ")
        .append(liftEnabled ? "Enabled" : "Disabled for wave")
        .append("</p><ul class='execution-list'>");
    for (LiftExecution execution : executions) {
      appendExecutionRow(html, liftName, execution, performedNames.get(execution.id()));
    }
    return html.append("</ul>").toString();
  }

  private static void appendExecutionRow(
      StringBuilder html, String liftName, LiftExecution execution, String performedName) {
    html.append("<li class='execution-item' style='margin:6px 0;'>")
        .append(
            "<div class='js-exec-view' style='display:flex;align-items:center;gap:8px;flex-wrap:nowrap;'>")
        .append(
            "<span class='execution-text' style='white-space:nowrap;overflow:hidden;text-overflow:ellipsis;flex:1;'>")
        .append(WebHtml.escapeHtml(performedName + " · " + formatExecution(execution)))
        .append("</span>");
    if (execution.id() == null) {
      html.append("<span class='status error'>Execution ID missing; cannot edit or delete.</span>");
    } else {
      appendExecutionActions(html, liftName, execution.id());
    }
    html.append("</div>");
    if (execution.id() != null) {
      appendExecutionEditor(html, liftName, execution);
    }
    html.append("</li>");
  }

  private static void appendExecutionActions(StringBuilder html, String liftName, int executionId) {
    html.append("<div class='execution-row-actions'>")
        .append("<button type='button' class='secondary compact-btn js-exec-edit'>Edit</button>")
        .append(
            "<button type='button' class='secondary danger compact-btn js-exec-delete'>Delete</button>")
        .append("</div>")
        .append(
            "<form method='post' action='/delete-execution' class='query-form execution-delete-form js-exec-delete-form' style='display:none;'>")
        .append("<input type='hidden' name='executionId' value='")
        .append(executionId)
        .append("'/><input type='hidden' name='tab' value='executions'/>")
        .append("<input type='hidden' name='liftQuery' value='")
        .append(WebHtml.escapeHtml(liftName))
        .append("'/></form>");
  }

  private static void appendExecutionEditor(
      StringBuilder html, String liftName, LiftExecution execution) {
    html.append(
            "<form method='post' action='/update-execution' class='query-form execution-edit-form js-exec-form' data-initial-sets='")
        .append(WebHtml.escapeHtml(setsToEditJson(execution.sets())))
        .append("' style='display:none;flex-direction:column;align-items:flex-start;gap:8px;'>")
        .append("<input type='hidden' name='lift' value='")
        .append(WebHtml.escapeHtml(liftName))
        .append("'/><input type='hidden' name='executionId' value='")
        .append(execution.id())
        .append("'/><input type='hidden' name='tab' value='executions'/>")
        .append("<input type='hidden' name='liftQuery' value='")
        .append(WebHtml.escapeHtml(liftName))
        .append("'/><div class='execution-edit-meta' ")
        .append(
            "style='display:flex;align-items:center;gap:8px;flex-wrap:nowrap;overflow-x:auto;'>")
        .append("<label>Date <input type='date' name='date' disabled value='")
        .append(WebHtml.escapeHtml(DATE_FORMAT.format(execution.date())))
        .append("'/></label><label>Notes <input type='text' name='notes' ")
        .append("style='min-width:220px;' disabled value='")
        .append(WebHtml.escapeHtml(execution.notes() == null ? "" : execution.notes()))
        .append("'/></label><label><input type='checkbox' name='warmup' disabled")
        .append(execution.warmup() ? " checked" : "")
        .append("/> Warm-up</label><label><input type='checkbox' name='deload' disabled")
        .append(execution.deload() ? " checked" : "")
        .append("/> Deload</label></div>")
        .append("<div class='js-edit-sets' ")
        .append("style='display:flex;flex-direction:column;gap:6px;width:100%;'>")
        .append(renderSetEditorRows(execution.sets()))
        .append(
            "</div><button type='button' class='secondary compact-btn js-add-set'>Add Set</button>")
        .append(
            "<input type='hidden' name='detailedSets' class='js-detailed-sets' disabled value=''/>")
        .append("<button type='submit'>Save</button>")
        .append(
            "<button type='button' class='secondary compact-btn js-exec-cancel'>Cancel</button>")
        .append("</form>");
  }

  static String renderSetEditorRows(List<ExecutionSet> sets) {
    StringBuilder html = new StringBuilder();
    for (ExecutionSet set : sets) {
      ExecutionSetFormValues formValues = ExecutionSetFormValues.from(set);

      html.append(
              "<div class='js-set-row' style='display:flex;align-items:center;gap:8px;flex-wrap:nowrap;overflow-x:auto;'>")
          .append("<select class='js-set-metric' disabled>")
          .append("<option value='reps'")
          .append(formValues.selectedAttribute("reps"))
          .append(">reps</option>")
          .append("<option value='reps-lr'")
          .append(formValues.selectedAttribute("reps-lr"))
          .append(">reps-lr</option>")
          .append("<option value='time'")
          .append(formValues.selectedAttribute("time"))
          .append(">time</option>")
          .append("<option value='distance'")
          .append(formValues.selectedAttribute("distance"))
          .append(">distance</option>")
          .append("</select>")
          .append(
              "<input type='number' min='0' class='js-set-value' disabled placeholder='value' style='width:90px;' value='")
          .append(WebHtml.escapeHtml(formValues.metricValue()))
          .append("'/>")
          .append(
              "<input type='number' min='0' class='js-set-left' disabled placeholder='left' style='width:80px;' value='")
          .append(WebHtml.escapeHtml(formValues.metricLeft()))
          .append("'/>")
          .append(
              "<input type='number' min='0' class='js-set-right' disabled placeholder='right' style='width:80px;' value='")
          .append(WebHtml.escapeHtml(formValues.metricRight()))
          .append("'/>")
          .append(
              "<input type='text' class='js-set-weight' disabled placeholder='weight' style='width:130px;' value='")
          .append(WebHtml.escapeHtml(formValues.weight()))
          .append("'/>")
          .append(
              "<input type='number' step='0.1' min='0' max='10' class='js-set-rpe' disabled placeholder='rpe' style='width:80px;' value='")
          .append(WebHtml.escapeHtml(formValues.rpe()))
          .append("'/>")
          .append(
              "<label class='missed-target-option'><input type='checkbox' class='js-set-missed' disabled")
          .append(formValues.missed() ? " checked" : "")
          .append("/> Missed target</label>")
          .append(
              "<button type='button' class='secondary compact-btn js-remove-set'>Remove</button>")
          .append("</div>");
    }
    return html.toString();
  }

  static String setsToEditJson(List<ExecutionSet> sets) {
    List<String> items = new ArrayList<>();
    for (ExecutionSet set : sets) {
      ExecutionSetFormValues formValues = ExecutionSetFormValues.from(set);

      String item =
          "{\"metricType\":\""
              + jsonEscape(formValues.metricType())
              + "\","
              + "\"metricValue\":\""
              + jsonEscape(formValues.metricValue())
              + "\","
              + "\"metricLeft\":\""
              + jsonEscape(formValues.metricLeft())
              + "\","
              + "\"metricRight\":\""
              + jsonEscape(formValues.metricRight())
              + "\","
              + "\"weight\":\""
              + jsonEscape(formValues.weight())
              + "\","
              + "\"rpe\":\""
              + jsonEscape(formValues.rpe())
              + "\",\"missed\":"
              + formValues.missed()
              + "}";
      items.add(item);
    }
    return "[" + String.join(",", items) + "]";
  }

  private static String jsonEscape(String value) {
    return value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\r", "\\r")
        .replace("\n", "\\n");
  }

  static String formatExecution(LiftExecution execution) {
    return DATE_FORMAT.format(execution.date())
        + " — "
        + ExecutionSummaryFormatter.formatCompactSummary(execution);
  }

  static String formatSets(List<ExecutionSet> sets) {
    List<String> parts = new ArrayList<>();
    int index = 0;
    while (index < sets.size()) {
      ExecutionSet set = sets.get(index);
      int count = 1;
      while (index + count < sets.size() && set.equals(sets.get(index + count))) {
        count++;
      }
      String weight = set.weight();
      boolean hasWeight =
          weight != null && !weight.isBlank() && !"none".equalsIgnoreCase(weight.trim());
      String item = formatMetric(set.metric()) + (hasWeight ? " @ " + weight : "");
      if (set.rpe() != null) {
        item += " rpe " + String.format(Locale.ROOT, "%.1f", set.rpe());
      }
      if (set.missed()) {
        item += " — Missed target";
      }
      parts.add(count > 1 ? count + "x" + item : item);
      index += count;
    }
    return joinList(parts);
  }

  static String formatMetric(SetMetric metric) {
    if (metric instanceof SetMetric.Reps reps) {
      return reps.reps() + " reps";
    }
    if (metric instanceof SetMetric.RepsLr repsLr) {
      return repsLr.left() + "L/" + repsLr.right() + "R reps";
    }
    if (metric instanceof SetMetric.RepsRange range) {
      return range.min() + "-" + range.max() + " reps";
    }
    if (metric instanceof SetMetric.TimeSecs timeSecs) {
      return timeSecs.seconds() + " sec";
    }
    if (metric instanceof SetMetric.DistanceFeet distanceFeet) {
      return distanceFeet.feet() + " ft";
    }
    return "unknown";
  }

  static String formatMainType(Lift lift) {
    if (lift == null || lift.main() == null) {
      return "Unknown";
    }
    return lift.main().toDbValue();
  }

  static String joinList(List<String> values) {
    return String.join(", ", values);
  }

  static String urlEncode(String value) {
    return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
