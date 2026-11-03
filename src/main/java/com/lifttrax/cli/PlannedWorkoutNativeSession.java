package com.lifttrax.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifttrax.workout.PlannedWorkoutFile;
import java.util.Map;
import java.util.Set;

/** Native form fallback for Work Along when browser scripting is disabled. */
final class PlannedWorkoutNativeSession {
  private PlannedWorkoutNativeSession() {}

  static void append(
      StringBuilder html,
      String key,
      Set<String> localLifts,
      WebUiRenderer.AddExecutionPrefill seed) {
    String prefix = "native-" + key + "-";
    html.append("<noscript><div class='session-native-entry'>")
        .append("<label>Status <select name='")
        .append(prefix)
        .append("state'>")
        .append("<option value='skipped'>Skipped / not logged</option>")
        .append("<option value='complete'>Complete</option></select></label>")
        .append("<label>Performed lift <select name='")
        .append(prefix)
        .append("lift'>");
    for (String lift : localLifts) {
      html.append("<option value='")
          .append(WebHtml.escapeHtml(lift))
          .append("'")
          .append(lift.equals(seed.lift()) ? " selected" : "")
          .append(">")
          .append(WebHtml.escapeHtml(lift))
          .append("</option>");
    }
    html.append("</select></label>");
    input(html, prefix, "weight", "Weight (include lb, kg, bands, or none)", "text", seed.weight());
    html.append("<input type='hidden' name='")
        .append(prefix)
        .append("metricType' value='")
        .append(WebHtml.escapeHtml(seed.metricType()))
        .append("'/>");
    if ("reps-lr".equals(seed.metricType())) {
      input(html, prefix, "metricLeft", "Left reps", "number", seed.metricLeft());
      input(html, prefix, "metricRight", "Right reps", "number", seed.metricRight());
    } else {
      String label =
          switch (seed.metricType()) {
            case "time" -> "Seconds";
            case "distance" -> "Feet";
            default -> "Reps";
          };
      input(html, prefix, "metricValue", label, "number", seed.metricValue());
    }
    input(html, prefix, "setCount", "Matching sets", "number", seed.setCount());
    input(html, prefix, "rpe", "RPE (optional)", "text", "");
    input(html, prefix, "notes", "Notes (optional)", "text", "");
    html.append("</div></noscript>");
  }

  private static void input(
      StringBuilder html, String prefix, String name, String label, String type, String value) {
    html.append("<label>")
        .append(label)
        .append(" <input type='")
        .append(type)
        .append("' name='")
        .append(prefix)
        .append(name)
        .append("' value='")
        .append(WebHtml.escapeHtml(value))
        .append("'/></label>");
  }

  static String results(PlannedWorkoutFile.PlannedWorkoutDay day, Map<String, String> form) {
    var json = new ObjectMapper();
    var results = json.createArrayNode();
    for (var item : PlannedWorkoutSessionService.plannedExercises(day)) {
      String prefix = "native-" + item.key() + "-";
      String state = form.getOrDefault(prefix + "state", "skipped");
      var result = results.addObject();
      result.put("exerciseKey", item.key());
      result.put("plannedLift", item.exercise().name());
      result.put("state", state);
      if ("skipped".equals(state)) {
        continue;
      }
      result.put("performedLift", form.getOrDefault(prefix + "lift", ""));
      result.put("notes", form.getOrDefault(prefix + "notes", ""));
      int count =
          WebExecutionRequestParser.parsePositiveInt(
              form.getOrDefault(prefix + "setCount", "1"), "Matching sets");
      if (count > 1000) {
        throw new IllegalArgumentException("Matching sets must be 1000 or fewer.");
      }
      var sets = result.putArray("sets");
      for (int index = 0; index < count; index++) {
        var set = sets.addObject();
        for (String field :
            Set.of("weight", "metricType", "metricValue", "metricLeft", "metricRight", "rpe")) {
          set.put(field, form.getOrDefault(prefix + field, ""));
        }
      }
    }
    return results.toString();
  }
}
