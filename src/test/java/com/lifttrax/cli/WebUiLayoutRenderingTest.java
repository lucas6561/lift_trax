package com.lifttrax.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lifttrax.models.ExecutionSet;
import com.lifttrax.models.Lift;
import com.lifttrax.models.LiftExecution;
import com.lifttrax.models.LiftRegion;
import com.lifttrax.models.LiftType;
import com.lifttrax.models.Muscle;
import com.lifttrax.models.SetMetric;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class WebUiLayoutRenderingTest {
  @Test
  void formatSetsOmitsAtNoneWeight() {
    String formatted =
        WebUiRenderer.formatSets(List.of(new ExecutionSet(new SetMetric.Reps(10), "none", null)));

    assertEquals("10 reps", formatted);
  }

  @Test
  void formatSetsGroupsIdenticalSequentialSets() {
    String formatted =
        WebUiRenderer.formatSets(
            List.of(
                new ExecutionSet(new SetMetric.Reps(15), "65 lb", null),
                new ExecutionSet(new SetMetric.Reps(15), "65 lb", null),
                new ExecutionSet(new SetMetric.Reps(15), "65 lb", null)));

    assertEquals("3x15 reps @ 65 lb", formatted);
  }

  @Test
  void formatExecutionIncludesWarmupAndDeloadFlags() {
    LiftExecution execution =
        new LiftExecution(
            42,
            LocalDate.of(2026, 2, 10),
            List.of(new ExecutionSet(new SetMetric.Reps(3), "315 lb", null)),
            true,
            true,
            "fast");

    String formatted = WebUiRenderer.formatExecution(execution);

    assertTrue(formatted.contains("1 sets x 3 reps @ 315 lb"));
    assertTrue(formatted.contains("(warm-up, deload)"));
    assertTrue(formatted.contains(" - fast"));
  }

  @Test
  void formatMainTypeHandlesNullMain() {
    Lift lift = new Lift("Mystery Lift", null, null, List.of(), "");
    String formatted = WebUiRenderer.formatMainType(lift);
    assertEquals("Unknown", formatted);
  }

  @Test
  void historyLayoutExposesSecondaryNavigation() {
    String html = renderHistoryLayout();

    assertContainsAll(
        html,
        "<h1>History</h1>",
        "aria-label='History views'",
        "Executions",
        "Query",
        "Weekly",
        "data-initial-tab='query'");
    assertFalse(html.contains("role='tablist'"));
  }

  @Test
  void historyLayoutProvidesQueryFilters() {
    String html = renderHistoryLayout();

    assertContainsAll(
        html,
        "Run Query",
        "data-filter-option",
        "hasAttribute('data-filter-option')",
        "select name='queryLift'",
        "query result",
        "js-filter-name",
        "js-filter-muscle",
        "multiple",
        "js-clear-filters",
        "data-muscles='QUAD,GLUTE'",
        "<option value='QUAD'>QUAD</option>",
        "Hold Ctrl/Cmd to select multiple");
  }

  @Test
  void historyLayoutPreservesWeeklyWaveAndImportContent() {
    String html = renderHistoryLayout();

    assertContainsAll(
        html,
        "name='waveWeeks'",
        "Generate Wave",
        "name='lastWeekStart' value='2026-01-01'",
        "name='lastWeekEnd' value='2026-01-07'",
        "← Previous Week",
        "Current Week",
        "last week result",
        "wave result",
        "data-panel='import-workout'",
        "action='/planned-workout-preview'");
  }

  @Test
  void historyLayoutDefersTheAddExecutionPanel() {
    String html = renderHistoryLayout();

    assertTrue(html.contains("Back Squat"));
    assertFalse(html.contains("action='/add-execution'"));
    assertTrue(html.contains("data-panel='add-execution' data-loaded='false'"));
  }

  @Test
  void addExecutionLayoutRendersExecutionAndLiftForms() {
    String html = renderAddExecutionLayout();

    assertContainsAll(
        html,
        "Save Execution",
        "action='/add-execution'",
        "Load Last",
        "formaction='/load-last-execution'",
        "New Lift",
        "action='/add-lift'",
        "class='js-new-lift-muscles'",
        "name='muscles' class='js-new-lift-muscles-hidden'",
        "<option value='QUAD'>QUAD</option>",
        "syncNewLiftMuscles",
        "execution result",
        "name='metricType'",
        "name='setCopies'",
        "class='secondary compact-btn js-max-effort-single'",
        "class='secondary compact-btn js-bands-only'",
        "class='secondary compact-btn js-bar-bands'",
        "class='save-execution-btn'");
  }

  @Test
  void addExecutionLayoutProvidesSetLogBehavior() {
    String html = renderAddExecutionLayout();

    assertContainsAll(
        html,
        "metricLabel(item)",
        "class='individual-sets-details'",
        "class='set-log-status js-set-log-status'",
        "No sets in log",
        "function updateSetLogStatus()",
        "function addCurrentSet()",
        "event.stopImmediatePropagation();",
        "form.addEventListener('click', (event) => {",
        "detailedSets.map((item) => ({...item}))",
        "focusControl(addSetBtn);",
        "focusControl(form.querySelector('.js-set-row:last-child .js-set-metric'))",
        "setInputValue('metricValue', '1')",
        "selectRadio('weightMode', 'bands')",
        "bindExecutionActions(document);");
  }

  @Test
  void addExecutionLayoutScopesFocusAndDraftPersistence() {
    String html = renderAddExecutionLayout();

    assertContainsAll(
        html,
        "data-focus-target='add-lift'",
        "data-focus-target='add-weight'",
        "new URLSearchParams(window.location.search)",
        "function focusAfterNavigation()",
        "focusAfterNavigation();",
        "const addExecutionScope = addExecutionForm || document;",
        "addExecutionScope.querySelector('.js-add-set')",
        "addExecutionScope.querySelector(\"input[name='metricType']:checked\")",
        "function collectAddExecutionDraft(form)",
        "function applyAddExecutionDraft(form, draft)",
        "localStorage.removeItem(ADD_EXECUTION_DRAFT_KEY)",
        "addExecutionForm.addEventListener('input', () => saveAddExecutionDraft(addExecutionForm))",
        "status success' role='status' aria-live='polite");
  }

  private static String renderHistoryLayout() {
    return renderLayout(
        "query",
        "<p>query result</p>",
        "<p>last week result</p>",
        "<form><input name='waveWeeks'/><button>Generate Wave</button></form><p>wave result</p>",
        "<form action='/planned-workout-preview'><input type='file'/></form>");
  }

  private static String renderAddExecutionLayout() {
    return renderLayout("add-execution", "", "", "", "");
  }

  private static String renderLayout(
      String tab, String query, String lastWeek, String wave, String importedWorkout) {
    return WebUiRenderer.renderTabbedLayout(
        testLifts(),
        "",
        "Back Squat",
        tab,
        "<p>execution result</p>",
        query,
        lastWeek,
        wave,
        importedWorkout,
        "Saved",
        "success",
        WebUiRenderer.AddExecutionPrefill.empty(),
        LocalDate.parse("2026-01-01"),
        LocalDate.parse("2026-01-07"),
        6);
  }

  private static List<Lift> testLifts() {
    return List.of(
        new Lift(
            "Back Squat", LiftRegion.LOWER, LiftType.SQUAT, List.of(Muscle.QUAD, Muscle.GLUTE), ""),
        new Lift(
            "Bench Press",
            LiftRegion.UPPER,
            LiftType.BENCH_PRESS,
            List.of(Muscle.CHEST, Muscle.TRICEP),
            ""));
  }

  private static void assertContainsAll(String value, String... expected) {
    for (String text : expected) {
      assertTrue(value.contains(text), () -> "Missing expected text: " + text);
    }
  }
}
