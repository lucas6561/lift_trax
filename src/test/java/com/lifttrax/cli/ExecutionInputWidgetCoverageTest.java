package com.lifttrax.cli;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lifttrax.models.ExecutionSet;
import com.lifttrax.models.SetMetric;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExecutionInputWidgetCoverageTest {

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
