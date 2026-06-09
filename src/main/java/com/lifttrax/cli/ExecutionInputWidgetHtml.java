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
    WebUiRenderer.AddExecutionPrefill prefill =
        prefillInput == null ? WebUiRenderer.AddExecutionPrefill.empty() : prefillInput;
    List<ExecutionSetFormValues> safeInitialSets =
        initialSets == null ? List.of() : List.copyOf(initialSets);
    WeightInputParser.WeightPrefill weightPrefill =
        WeightInputParser.parseWeightPrefill(prefill.weight());

    String repsChecked = "reps".equals(prefill.metricType()) ? "checked" : "";
    String repsLrChecked = "reps-lr".equals(prefill.metricType()) ? "checked" : "";
    String timeChecked = "time".equals(prefill.metricType()) ? "checked" : "";
    String distanceChecked = "distance".equals(prefill.metricType()) ? "checked" : "";
    String standardWeightChecked = "weight".equals(weightPrefill.mode()) ? "checked" : "";
    String lrWeightChecked = "lr".equals(weightPrefill.mode()) ? "checked" : "";
    String bandsWeightChecked = "bands".equals(weightPrefill.mode()) ? "checked" : "";
    String accomWeightChecked = "accom".equals(weightPrefill.mode()) ? "checked" : "";
    String noneWeightChecked = "none".equals(weightPrefill.mode()) ? "checked" : "";
    String customWeightChecked = "custom".equals(weightPrefill.mode()) ? "checked" : "";
    String weightUnitLbSelected = "lb".equals(weightPrefill.weightUnit()) ? " selected" : "";
    String weightUnitKgSelected = "kg".equals(weightPrefill.weightUnit()) ? " selected" : "";
    String weightUnitLrLbSelected = "lb".equals(weightPrefill.lrUnit()) ? " selected" : "";
    String weightUnitLrKgSelected = "kg".equals(weightPrefill.lrUnit()) ? " selected" : "";
    String accomUnitLbSelected = "lb".equals(weightPrefill.accomUnit()) ? " selected" : "";
    String accomUnitKgSelected = "kg".equals(weightPrefill.accomUnit()) ? " selected" : "";
    String accomChainsSelected = "chains".equals(weightPrefill.accomMode()) ? " selected" : "";
    String accomBandsSelected = "bands".equals(weightPrefill.accomMode()) ? " selected" : "";
    String bandChecks = renderBandChecks("weightBandColors", weightPrefill.bands());
    String accomBandChecks = renderBandChecks("accomBandColors", weightPrefill.accomBands());
    String quickPresets = showQuickPresets ? quickPresets() : "";
    String detailsOpen = safeInitialSets.isEmpty() && !openIndividualSets ? "" : " open";

    return """
                  %s
                  <input type='hidden' name='weight' class='js-weight-hidden' value='%s'/>
                  <input type='hidden' name='detailedSets' class='js-detailed-sets' value='%s'/>
                  <fieldset>
                    <legend>Weight</legend>
                    <div class='segmented'>
                      <label><input type='radio' name='weightMode' value='weight' %s/> Weight</label>
                      <label><input type='radio' name='weightMode' value='lr' %s/> L/R Weight</label>
                      <label><input type='radio' name='weightMode' value='bands' %s/> Bands</label>
                      <label><input type='radio' name='weightMode' value='accom' %s/> Accommodating</label>
                      <label><input type='radio' name='weightMode' value='none' %s/> None</label>
                      <label><input type='radio' name='weightMode' value='custom' %s/> Custom</label>
                    </div>
                    <div class='stacked-row weight-weight'>
                      <label>Weight <input type='number' step='0.5' min='0' name='weightValue' data-focus-target='add-weight' value='%s' placeholder='225'/></label>
                      <label>Unit
                        <select name='weightUnit'><option value='lb'%s>lb</option><option value='kg'%s>kg</option></select>
                      </label>
                    </div>
                    <div class='stacked-row weight-lr is-hidden'>
                      <label>Left <input type='number' step='0.5' min='0' name='weightLeft' data-focus-target='add-weight' value='%s' placeholder='40'/></label>
                      <label>Right <input type='number' step='0.5' min='0' name='weightRight' value='%s' placeholder='40'/></label>
                      <label>Unit
                        <select name='weightUnitLr'><option value='lb'%s>lb</option><option value='kg'%s>kg</option></select>
                      </label>
                    </div>
                    <div class='stacked-row weight-bands is-hidden'>
                      <label>Bands</label>
                      <div class='segmented'>%s</div>
                    </div>
                    <div class='stacked-row weight-accom is-hidden'>
                      <label>Bar <input type='number' step='0.5' min='0' name='accomBar' data-focus-target='add-weight' value='%s' placeholder='225'/></label>
                      <label>Unit
                        <select name='accomUnit'><option value='lb'%s>lb</option><option value='kg'%s>kg</option></select>
                      </label>
                      <label>Resistance
                        <select name='accomMode'><option value='chains'%s>Chains</option><option value='bands'%s>Bands</option></select>
                      </label>
                      <label class='accom-chains'>Chain <input type='number' step='0.5' min='0' name='accomChain' value='%s' placeholder='40'/></label>
                      <div class='accom-bands is-hidden'>
                        <label>Bands</label>
                        <div class='segmented'>%s</div>
                      </div>
                    </div>
                    <div class='stacked-row weight-custom is-hidden'>
                      <label>Custom <input type='text' name='customWeight' data-focus-target='add-weight' value='%s' placeholder='225 lb+40c'/></label>
                    </div>
                  </fieldset>
                  <div class='stacked-row'>
                    <label>Set Count <input type='number' min='1' name='setCount' value='%s'/></label>
                    <label>RPE <input type='number' step='0.1' min='1' max='10' name='rpe' value='%s' placeholder='8.5'/></label>
                  </div>
                  <fieldset>
                    <legend>Metric</legend>
                    <div class='segmented'>
                      <label><input type='radio' name='metricType' value='reps' %s/> Reps</label>
                      <label><input type='radio' name='metricType' value='reps-lr' %s/> L/R Reps</label>
                      <label><input type='radio' name='metricType' value='time' %s/> Seconds</label>
                      <label><input type='radio' name='metricType' value='distance' %s/> Feet</label>
                    </div>
                    <div class='stacked-row'>
                      <label class='metric-single'>Value <input type='number' min='1' name='metricValue' value='%s'/></label>
                      <label class='metric-lr is-hidden'>Left <input type='number' min='1' name='metricLeft' value='%s'/></label>
                      <label class='metric-lr is-hidden'>Right <input type='number' min='1' name='metricRight' value='%s'/></label>
                    </div>
                    <details class='individual-sets-details'%s>
                      <summary>Individual sets</summary>
                      <div class='stacked-row'>
                        <label>Copies <input type='number' min='1' name='setCopies' value='1'/></label>
                        <button type='button' class='secondary js-add-set'>Add Individual Set</button>
                        <button type='button' class='secondary js-clear-sets'>Clear Individual Sets</button>
                      </div>
                      <ul class='set-list js-set-list'>%s</ul>
                    </details>
                  </fieldset>
                  <div class='stacked-row'>
                    <label>Date <input type='date' name='date' value='%s'/></label>
                    <label><input type='checkbox' name='warmup' %s/> Warm-up</label>
                    <label><input type='checkbox' name='deload' %s/> Deload</label>
                  </div>
                  <label>Notes
                    <input type='text' name='notes' value='%s' placeholder='Optional notes'/>
                  </label>
                """
        .formatted(
            quickPresets,
            WebHtml.escapeHtml(prefill.weight()),
            WebHtml.escapeHtml(executionSetValuesToJson(safeInitialSets)),
            standardWeightChecked,
            lrWeightChecked,
            bandsWeightChecked,
            accomWeightChecked,
            noneWeightChecked,
            customWeightChecked,
            WebHtml.escapeHtml(weightPrefill.weightValue()),
            weightUnitLbSelected,
            weightUnitKgSelected,
            WebHtml.escapeHtml(weightPrefill.leftValue()),
            WebHtml.escapeHtml(weightPrefill.rightValue()),
            weightUnitLrLbSelected,
            weightUnitLrKgSelected,
            bandChecks,
            WebHtml.escapeHtml(weightPrefill.accomBar()),
            accomUnitLbSelected,
            accomUnitKgSelected,
            accomChainsSelected,
            accomBandsSelected,
            WebHtml.escapeHtml(weightPrefill.accomChain()),
            accomBandChecks,
            WebHtml.escapeHtml(weightPrefill.customWeight()),
            WebHtml.escapeHtml(prefill.setCount()),
            WebHtml.escapeHtml(prefill.rpe()),
            repsChecked,
            repsLrChecked,
            timeChecked,
            distanceChecked,
            WebHtml.escapeHtml(prefill.metricValue()),
            WebHtml.escapeHtml(prefill.metricLeft()),
            WebHtml.escapeHtml(prefill.metricRight()),
            detailsOpen,
            renderInitialSetItems(safeInitialSets),
            WebHtml.escapeHtml(prefill.date()),
            prefill.warmup() ? "checked" : "",
            prefill.deload() ? "checked" : "",
            WebHtml.escapeHtml(prefill.notes()));
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
              + "\"}";
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
    return metric + " @ " + weight + rpe;
  }

  private static String jsonEscape(String value) {
    return value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\r", "\\r")
        .replace("\n", "\\n");
  }
}
