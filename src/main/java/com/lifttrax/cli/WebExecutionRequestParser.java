package com.lifttrax.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifttrax.models.ExecutionSet;
import com.lifttrax.models.LiftExecution;
import com.lifttrax.models.SetMetric;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Parses execution and workout request values. */
final class WebExecutionRequestParser {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private WebExecutionRequestParser() {}

  static WebUiRenderer.AddExecutionPrefill parsePrefill(Map<String, String> query) {
    return new WebUiRenderer.AddExecutionPrefill(
        query.getOrDefault("prefillLift", ""),
        query.getOrDefault("prefillWeight", ""),
        query.getOrDefault("prefillSetCount", "1"),
        query.getOrDefault("prefillRpe", ""),
        query.getOrDefault("prefillMetricType", "reps"),
        query.getOrDefault("prefillMetricValue", "5"),
        query.getOrDefault("prefillMetricLeft", "5"),
        query.getOrDefault("prefillMetricRight", "5"),
        query.getOrDefault("prefillDate", ""),
        Boolean.parseBoolean(query.getOrDefault("prefillWarmup", "false")),
        Boolean.parseBoolean(query.getOrDefault("prefillDeload", "false")),
        query.getOrDefault("prefillNotes", ""),
        Boolean.parseBoolean(query.getOrDefault("prefillMissed", "false")));
  }

  static String buildLoadLastNoPriorRedirect(
      Map<String, String> query,
      String liftName,
      LocalDate selectedDate,
      boolean warmup,
      boolean deload,
      String criteria) {
    StringBuilder redirectUrl = new StringBuilder("/?tab=add-execution&statusType=error");
    redirectUrl
        .append("&status=")
        .append(
            WebUiRenderer.urlEncode("No prior executions for " + liftName + " with " + criteria));
    redirectUrl.append("&prefillLift=").append(WebUiRenderer.urlEncode(liftName));
    redirectUrl
        .append("&prefillWeight=")
        .append(WebUiRenderer.urlEncode(query.getOrDefault("weight", "")));
    redirectUrl
        .append("&prefillSetCount=")
        .append(WebUiRenderer.urlEncode(query.getOrDefault("setCount", "1")));
    redirectUrl.append("&prefillDate=").append(WebUiRenderer.urlEncode(selectedDate.toString()));
    redirectUrl
        .append("&prefillRpe=")
        .append(WebUiRenderer.urlEncode(query.getOrDefault("rpe", "")));
    redirectUrl
        .append("&prefillMetricType=")
        .append(WebUiRenderer.urlEncode(query.getOrDefault("metricType", "reps")));
    redirectUrl
        .append("&prefillMetricValue=")
        .append(WebUiRenderer.urlEncode(query.getOrDefault("metricValue", "5")));
    redirectUrl
        .append("&prefillMetricLeft=")
        .append(WebUiRenderer.urlEncode(query.getOrDefault("metricLeft", "5")));
    redirectUrl
        .append("&prefillMetricRight=")
        .append(WebUiRenderer.urlEncode(query.getOrDefault("metricRight", "5")));
    redirectUrl.append("&prefillWarmup=").append(warmup);
    redirectUrl.append("&prefillDeload=").append(deload);
    redirectUrl.append("&prefillMissed=").append(isMissed(query));
    redirectUrl
        .append("&prefillNotes=")
        .append(WebUiRenderer.urlEncode(query.getOrDefault("notes", "")));
    return redirectUrl.toString();
  }

  static String safe(String value) {
    return value == null ? "" : value;
  }

  static void applyMetricPrefill(StringBuilder redirectUrl, SetMetric metric) {
    if (metric instanceof SetMetric.Reps reps) {
      redirectUrl.append("&prefillMetricType=reps");
      redirectUrl.append("&prefillMetricValue=").append(reps.reps());
      return;
    }
    if (metric instanceof SetMetric.RepsLr repsLr) {
      redirectUrl.append("&prefillMetricType=reps-lr");
      redirectUrl.append("&prefillMetricLeft=").append(repsLr.left());
      redirectUrl.append("&prefillMetricRight=").append(repsLr.right());
      return;
    }
    if (metric instanceof SetMetric.TimeSecs timeSecs) {
      redirectUrl.append("&prefillMetricType=time");
      redirectUrl.append("&prefillMetricValue=").append(timeSecs.seconds());
      return;
    }
    if (metric instanceof SetMetric.DistanceFeet distanceFeet) {
      redirectUrl.append("&prefillMetricType=distance");
      redirectUrl.append("&prefillMetricValue=").append(distanceFeet.feet());
    }
  }

  static LiftExecution selectExecutionForFlags(
      List<LiftExecution> executions, boolean warmup, boolean deload) {
    for (LiftExecution execution : executions) {
      if (execution.warmup() == warmup && execution.deload() == deload) {
        return execution;
      }
    }
    return null;
  }

  static LiftExecution findExecutionById(List<LiftExecution> executions, int executionId) {
    for (LiftExecution execution : executions) {
      if (execution.id() != null && execution.id() == executionId) {
        return execution;
      }
    }
    return null;
  }

  static int parsePositiveInt(String value, String fieldName) {
    int parsed = Integer.parseInt(value.trim());
    if (parsed <= 0) {
      throw new IllegalArgumentException(fieldName + " must be greater than 0");
    }
    return parsed;
  }

  static int parseNonNegativeInt(String value) {
    try {
      int parsed = Integer.parseInt(value.trim());
      if (parsed >= 0) {
        return parsed;
      }
    } catch (RuntimeException ignored) {
      // Client-maintained summary counters should not block the durable save.
    }
    return 0;
  }

  static Float parseOptionalFloat(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    float rpe = Float.parseFloat(value.trim());
    if (!Float.isFinite(rpe) || rpe < 0 || rpe > 10) {
      throw new IllegalArgumentException("RPE must be between 0 and 10");
    }
    return rpe;
  }

  static int parseBoundedInt(String value, int fallback, int min, int max) {
    if (value == null || value.isBlank()) {
      return fallback;
    }
    try {
      int parsed = Integer.parseInt(value.trim());
      return Math.max(min, Math.min(max, parsed));
    } catch (NumberFormatException ignored) {
      return fallback;
    }
  }

  static LocalDate parseDateOrDefault(String value, LocalDate fallback) {
    if (value == null || value.isBlank()) {
      return fallback;
    }
    try {
      return LocalDate.parse(value.trim());
    } catch (Exception ignored) {
      return fallback;
    }
  }

  static SetMetric parseMetric(Map<String, String> form) {
    String metricType = form.getOrDefault("metricType", "reps").trim();
    boolean missed = isMissed(form);
    return switch (metricType) {
      case "reps-lr" ->
          new SetMetric.RepsLr(
              parseActualMetric(form.getOrDefault("metricLeft", ""), "Left reps", missed),
              parseActualMetric(form.getOrDefault("metricRight", ""), "Right reps", missed));
      case "time" ->
          new SetMetric.TimeSecs(
              parseActualMetric(form.getOrDefault("metricValue", ""), "Seconds", missed));
      case "distance" ->
          new SetMetric.DistanceFeet(
              parseActualMetric(form.getOrDefault("metricValue", ""), "Feet", missed));
      default ->
          new SetMetric.Reps(
              parseActualMetric(form.getOrDefault("metricValue", ""), "Reps", missed));
    };
  }

  static boolean isMissed(Map<String, String> form) {
    String value = form.getOrDefault("missed", "false");
    return "on".equalsIgnoreCase(value) || Boolean.parseBoolean(value);
  }

  static int parseActualMetric(String value, String fieldName, boolean missed) {
    int parsed = Integer.parseInt(value.trim());
    if (parsed < 0 || (parsed == 0 && !missed)) {
      throw new IllegalArgumentException(
          fieldName
              + (missed
                  ? " cannot be negative"
                  : " must be greater than 0 unless marked Missed target"));
    }
    return parsed;
  }

  static List<ExecutionSet> parseDetailedSets(String json) {
    List<ExecutionSet> result = new ArrayList<>();
    try {
      JsonNode root = OBJECT_MAPPER.readTree(json);
      if (root == null || !root.isArray()) {
        return result;
      }
      for (JsonNode node : root) {
        Map<String, String> fields = new HashMap<>();
        fields.put("metricType", node.path("metricType").asText("reps"));
        fields.put("metricValue", node.path("metricValue").asText(""));
        fields.put("metricLeft", node.path("metricLeft").asText(""));
        fields.put("metricRight", node.path("metricRight").asText(""));
        fields.put("missed", node.path("missed").asText("false"));
        SetMetric metric = parseMetric(fields);
        String weight = node.path("weight").asText("");
        Float rpe = parseOptionalFloat(node.path("rpe").asText(""));
        result.add(new ExecutionSet(metric, weight, rpe, isMissed(fields)));
      }
    } catch (Exception ignored) {
      return List.of();
    }
    return result;
  }
}
