package com.lifttrax.cli;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lifttrax.models.ExecutionSet;
import com.lifttrax.models.SetMetric;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExecutionInputWidgetCoverageTest {

  @Test
  void missedTargetIsAvailableInBothWidgetsAndRetainedInIndividualLog() {
    WebUiRenderer.AddExecutionPrefill prefill =
        new WebUiRenderer.AddExecutionPrefill(
            "Back Squat", "225 lb", "1", "", "reps", "0", "", "", "", false, false, "", true);
    ExecutionSetFormValues missed =
        ExecutionSetFormValues.from(new ExecutionSet(new SetMetric.Reps(0), "225 lb", null, true));

    String html = ExecutionInputWidgetHtml.render(prefill, List.of(missed), false);
    String workAlong = ExecutionInputWidgetHtml.renderWorkAlong(prefill, "set-1");

    assertTrue(missed.missed());
    assertTrue(html.contains("name='missed' class='js-missed-target' checked"));
    assertTrue(html.contains("0 reps @ 225 lb — Missed target"));
    assertTrue(html.contains("&quot;missed&quot;:true"));
    assertTrue(html.contains("Use 0 reps if none were completed."));
    assertTrue(workAlong.contains("name='missed' class='js-missed-target' checked"));
    assertTrue(workAlong.indexOf("Missed target") > workAlong.indexOf("More set options"));
    assertTrue(workAlong.contains("min='0' name='metricValue' value='0'"));
    assertTrue(workAlong.contains("min='0' max='10' name='rpe' value=''"));
  }

  @Test
  void individualSetLogRendersEveryMetricSummaryAndEscapedJson() {
    List<ExecutionSetFormValues> sets =
        List.of(
            ExecutionSetFormValues.from(
                new ExecutionSet(new SetMetric.Reps(5), "225 \"lb\"", 8.0f)),
            ExecutionSetFormValues.from(
                new ExecutionSet(new SetMetric.RepsLr(4, 3), "40lb|40lb", null)),
            ExecutionSetFormValues.from(
                new ExecutionSet(new SetMetric.TimeSecs(30), "bodyweight", null)),
            ExecutionSetFormValues.from(
                new ExecutionSet(new SetMetric.DistanceFeet(100), "90 lb\ncarry", 7.5f)));

    String html =
        ExecutionInputWidgetHtml.render(
            WebUiRenderer.AddExecutionPrefill.empty(), sets, true, true, "coverage suffix");

    assertTrue(html.contains("5 reps @ 225 &quot;lb&quot;, rpe 8.0"));
    assertTrue(html.contains("4L/3R reps @ 40lb|40lb"));
    assertTrue(html.contains("30 sec @ bodyweight"));
    assertTrue(html.contains("100 ft @ 90 lb"));
    assertTrue(html.contains("4 sets in log"));
    assertTrue(html.contains("name='setEntryMode-coverage-suffix'"));
    assertTrue(html.contains("Quick setup"));
    assertTrue(html.contains("\\&quot;lb\\&quot;"));
    assertTrue(html.contains("\\ncarry"));
  }

  @Test
  void emptySetLogUsesMultipleModeAndNullDefaults() {
    String html = ExecutionInputWidgetHtml.render(null, null, false);

    assertTrue(html.contains("No sets in log"));
    assertTrue(html.contains("value='multiple' checked"));
    assertFalse(html.contains("Quick setup"));
  }

  @Test
  void metricSelectionOnlyMarksTheMatchingOption() {
    ExecutionSetFormValues values =
        ExecutionSetFormValues.from(new ExecutionSet(new SetMetric.DistanceFeet(50), "none", null));

    assertTrue(values.selectedAttribute("distance").contains("selected"));
    assertTrue(values.selectedAttribute("reps").isEmpty());
  }
}
