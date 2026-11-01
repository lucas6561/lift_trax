package com.lifttrax.cli;

import java.util.ArrayList;
import java.util.List;

/** Shared HTML for the execution-entry controls used by Add Execution and workout sessions. */
final class ExecutionInputWidgetHtml {
  private ExecutionInputWidgetHtml() {}

  static String render(
      WebUiRenderer.AddExecutionPrefill prefillInput,
      List<ExecutionSetFormValues> initialSets,
      boolean showQuickPresets) {
    return render(prefillInput, initialSets, showQuickPresets, false);
  }

  static String render(
      WebUiRenderer.AddExecutionPrefill prefillInput,
      List<ExecutionSetFormValues> initialSets,
      boolean showQuickPresets,
      boolean openIndividualSets) {
    return render(prefillInput, initialSets, showQuickPresets, openIndividualSets, "");
  }

  static String render(
      WebUiRenderer.AddExecutionPrefill prefillInput,
      List<ExecutionSetFormValues> initialSets,
      boolean showQuickPresets,
      boolean openIndividualSets,
      String radioNameSuffix) {
    WebUiRenderer.AddExecutionPrefill prefill = defaultPrefill(prefillInput);
    List<ExecutionSetFormValues> safeInitialSets = safeSets(initialSets);
    WeightInputParser.WeightPrefill weightPrefill =
        WeightInputParser.parseWeightPrefill(prefill.weight());
    SetEntryModeState setEntryMode = SetEntryModeState.from(safeInitialSets, openIndividualSets);
    return formatInputWidget(
        prefill, safeInitialSets, weightPrefill, setEntryMode, showQuickPresets, radioNameSuffix);
  }

  private static String formatInputWidget(
      WebUiRenderer.AddExecutionPrefill prefill,
      List<ExecutionSetFormValues> initialSets,
      WeightInputParser.WeightPrefill weight,
      SetEntryModeState setEntryMode,
      boolean showQuickPresets,
      String radioNameSuffix) {
    String weightMode = radioName("weightMode", radioNameSuffix);
    String setEntryModeName = radioName("setEntryMode", radioNameSuffix);
    String metricType = radioName("metricType", radioNameSuffix);
    return ClasspathText.read("execution-input-widget.html")
        .formatted(
            valueWhen(showQuickPresets, quickPresets()),
            WebHtml.escapeHtml(prefill.weight()),
            WebHtml.escapeHtml(executionSetValuesToJson(initialSets)),
            weightMode,
            checked("weight".equals(weight.mode())),
            weightMode,
            checked("lr".equals(weight.mode())),
            weightMode,
            checked("bands".equals(weight.mode())),
            weightMode,
            checked("accom".equals(weight.mode())),
            weightMode,
            checked("none".equals(weight.mode())),
            weightMode,
            checked("custom".equals(weight.mode())),
            WebHtml.escapeHtml(weight.weightValue()),
            selected("lb".equals(weight.weightUnit())),
            selected("kg".equals(weight.weightUnit())),
            WebHtml.escapeHtml(weight.leftValue()),
            WebHtml.escapeHtml(weight.rightValue()),
            selected("lb".equals(weight.lrUnit())),
            selected("kg".equals(weight.lrUnit())),
            renderBandChecks("weightBandColors", weight.bands()),
            WebHtml.escapeHtml(weight.accomBar()),
            selected("lb".equals(weight.accomUnit())),
            selected("kg".equals(weight.accomUnit())),
            selected("chains".equals(weight.accomMode())),
            selected("bands".equals(weight.accomMode())),
            WebHtml.escapeHtml(weight.accomChain()),
            renderBandChecks("accomBandColors", weight.accomBands()),
            WebHtml.escapeHtml(weight.customWeight()),
            setEntryModeName,
            setEntryMode.multipleChecked(),
            setEntryModeName,
            setEntryMode.individualChecked(),
            WebHtml.escapeHtml(prefill.setCount()),
            WebHtml.escapeHtml(prefill.rpe()),
            missedTargetControl(prefill.missed()),
            metricType,
            checked("reps".equals(prefill.metricType())),
            metricType,
            checked("reps-lr".equals(prefill.metricType())),
            metricType,
            checked("time".equals(prefill.metricType())),
            metricType,
            checked("distance".equals(prefill.metricType())),
            WebHtml.escapeHtml(prefill.metricValue()),
            WebHtml.escapeHtml(prefill.metricLeft()),
            WebHtml.escapeHtml(prefill.metricRight()),
            setEntryMode.detailsOpen(),
            setLogStatus(initialSets.size()),
            renderInitialSetItems(initialSets),
            WebHtml.escapeHtml(prefill.date()),
            checked(prefill.warmup()),
            checked(prefill.deload()),
            WebHtml.escapeHtml(prefill.notes()));
  }

  static String renderWorkAlong(
      WebUiRenderer.AddExecutionPrefill prefillInput, String radioNameSuffix) {
    WebUiRenderer.AddExecutionPrefill prefill = defaultPrefill(prefillInput);
    WeightInputParser.WeightPrefill weightPrefill =
        WeightInputParser.parseWeightPrefill(prefill.weight());
    return formatWorkAlongWidget(prefill, weightPrefill, radioNameSuffix);
  }

  private static String formatWorkAlongWidget(
      WebUiRenderer.AddExecutionPrefill prefill,
      WeightInputParser.WeightPrefill weight,
      String radioNameSuffix) {
    SetEntryModeState setEntryMode = SetEntryModeState.from(List.of(), false);
    String weightMode = radioName("weightMode", radioNameSuffix);
    String setEntryModeName = radioName("setEntryMode", radioNameSuffix);
    String metricType = radioName("metricType", radioNameSuffix);
    String metricLabel =
        switch (prefill.metricType()) {
          case "time" -> "Seconds";
          case "distance" -> "Feet";
          default -> "Reps";
        };
    boolean uncommonWeight = !prefill.weight().isBlank() && !"weight".equals(weight.mode());
    boolean leftRightMetric = "reps-lr".equals(prefill.metricType());
    String moreOptionsOpen = valueWhen(uncommonWeight || leftRightMetric, " open");

    return ClasspathText.read("execution-work-along-widget.html")
        .formatted(
            WebHtml.escapeHtml(prefill.weight()),
            WebHtml.escapeHtml(weight.weightValue()),
            selected("lb".equals(weight.weightUnit())),
            selected("kg".equals(weight.weightUnit())),
            metricLabel,
            WebHtml.escapeHtml(prefill.metricValue()),
            WebHtml.escapeHtml(prefill.rpe()),
            WebHtml.escapeHtml(prefill.setCount()),
            moreOptionsOpen,
            missedTargetControl(prefill.missed()),
            weightMode,
            checked(prefill.weight().isBlank() || "weight".equals(weight.mode())),
            weightMode,
            checked("lr".equals(weight.mode())),
            weightMode,
            checked("bands".equals(weight.mode())),
            weightMode,
            checked("accom".equals(weight.mode())),
            weightMode,
            checked(!prefill.weight().isBlank() && "none".equals(weight.mode())),
            weightMode,
            checked("custom".equals(weight.mode())),
            WebHtml.escapeHtml(weight.leftValue()),
            WebHtml.escapeHtml(weight.rightValue()),
            selected("lb".equals(weight.lrUnit())),
            selected("kg".equals(weight.lrUnit())),
            renderBandChecks("weightBandColors", weight.bands()),
            WebHtml.escapeHtml(weight.accomBar()),
            selected("lb".equals(weight.accomUnit())),
            selected("kg".equals(weight.accomUnit())),
            selected("chains".equals(weight.accomMode())),
            selected("bands".equals(weight.accomMode())),
            WebHtml.escapeHtml(weight.accomChain()),
            renderBandChecks("accomBandColors", weight.accomBands()),
            WebHtml.escapeHtml(weight.customWeight()),
            setEntryModeName,
            setEntryMode.multipleChecked(),
            setEntryModeName,
            setEntryMode.individualChecked(),
            setEntryMode.detailsOpen(),
            metricType,
            checked("reps".equals(prefill.metricType())),
            metricType,
            checked("reps-lr".equals(prefill.metricType())),
            metricType,
            checked("time".equals(prefill.metricType())),
            metricType,
            checked("distance".equals(prefill.metricType())),
            WebHtml.escapeHtml(prefill.metricLeft()),
            WebHtml.escapeHtml(prefill.metricRight()),
            WebHtml.escapeHtml(prefill.date()),
            checked(prefill.warmup()),
            checked(prefill.deload()),
            WebHtml.escapeHtml(prefill.notes()));
  }

  private static String radioName(String baseName, String suffix) {
    if (suffix == null || suffix.isBlank()) {
      return baseName;
    }
    return baseName + "-" + suffix.replaceAll("[^A-Za-z0-9_-]", "-");
  }

  private static WebUiRenderer.AddExecutionPrefill defaultPrefill(
      WebUiRenderer.AddExecutionPrefill prefill) {
    return prefill == null ? WebUiRenderer.AddExecutionPrefill.empty() : prefill;
  }

  private static List<ExecutionSetFormValues> safeSets(List<ExecutionSetFormValues> sets) {
    return sets == null ? List.of() : List.copyOf(sets);
  }

  private static String checked(boolean condition) {
    return valueWhen(condition, "checked");
  }

  private static String selected(boolean condition) {
    return valueWhen(condition, " selected");
  }

  private static String valueWhen(boolean condition, String value) {
    return condition ? value : "";
  }

  private static String missedTargetControl(boolean missed) {
    return "<div class='missed-target-control'>"
        + "<label class='missed-target-option'><input type='checkbox' name='missed' class='js-missed-target'"
        + (missed ? " checked" : "")
        + "/> Missed target</label>"
        + "<span class='missed-target-help'>Log actual reps, weight, and RPE. Use 0 reps if none were completed.</span>"
        + "</div>";
  }

  private static String quickPresets() {
    return """
                  <div class='quick-log-presets' aria-label='Quick logging presets'>
                    <span class='quick-log-label'>Quick setup</span>
                    <button type='button' class='secondary compact-btn js-max-effort-single'>1RM Single</button>
                    <button type='button' class='secondary compact-btn js-bands-only'>Bands Only</button>
                    <button type='button' class='secondary compact-btn js-bar-bands'>Bar + Bands</button>
                  </div>
                """;
  }

  private static String renderBandChecks(String name, List<String> selected) {
    StringBuilder html = new StringBuilder();
    for (String color : WeightInputParser.BAND_COLORS) {
      html.append("<label><input type='checkbox' name='")
          .append(name)
          .append("' value='")
          .append(WebHtml.escapeHtml(color))
          .append("'")
          .append(selected.contains(color) ? " checked" : "")
          .append("/> ")
          .append(WebHtml.escapeHtml(color))
          .append("</label>");
    }
    return html.toString();
  }

  private static String renderInitialSetItems(List<ExecutionSetFormValues> initialSets) {
    StringBuilder html = new StringBuilder();
    for (ExecutionSetFormValues set : initialSets) {
      html.append("<li>")
          .append(WebHtml.escapeHtml(setSummary(set)))
          .append(" <button type='button' class='secondary'>Remove</button></li>");
    }
    return html.toString();
  }

  private static String setLogStatus(int setCount) {
    if (setCount == 0) {
      return "No sets in log";
    }
    return setCount == 1 ? "1 set in log" : setCount + " sets in log";
  }

  private static String executionSetValuesToJson(List<ExecutionSetFormValues> sets) {
    List<String> items = new ArrayList<>();
    for (ExecutionSetFormValues set : sets) {
      String item =
          "{\"metricType\":\""
              + jsonEscape(set.metricType())
              + "\","
              + "\"metricValue\":\""
              + jsonEscape(set.metricValue())
              + "\","
              + "\"metricLeft\":\""
              + jsonEscape(set.metricLeft())
              + "\","
              + "\"metricRight\":\""
              + jsonEscape(set.metricRight())
              + "\","
              + "\"weight\":\""
              + jsonEscape(set.weight())
              + "\","
              + "\"rpe\":\""
              + jsonEscape(set.rpe())
              + "\",\"missed\":"
              + set.missed()
              + "}";
      items.add(item);
    }
    return "[" + String.join(",", items) + "]";
  }

  private static String setSummary(ExecutionSetFormValues set) {
    String metric =
        switch (set.metricType()) {
          case "reps-lr" -> set.metricLeft() + "L/" + set.metricRight() + "R reps";
          case "time" -> set.metricValue() + " sec";
          case "distance" -> set.metricValue() + " ft";
          default -> set.metricValue() + " reps";
        };
    String rpe = set.rpe().isBlank() ? "" : ", rpe " + set.rpe();
    String weight = set.weight().isBlank() ? "none" : set.weight();
    return metric + " @ " + weight + rpe + (set.missed() ? " — Missed target" : "");
  }

  private static String jsonEscape(String value) {
    return value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\r", "\\r")
        .replace("\n", "\\n");
  }

  private record SetEntryModeState(
      String multipleChecked, String individualChecked, String detailsOpen) {
    static SetEntryModeState from(
        List<ExecutionSetFormValues> initialSets, boolean openIndividualSets) {
      if (!initialSets.isEmpty() || openIndividualSets) {
        return new SetEntryModeState("", "checked", " open");
      }
      return new SetEntryModeState("checked", "", "");
    }
  }
}
