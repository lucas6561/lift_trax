package com.lifttrax.cli;

import com.lifttrax.db.TrainingDataStore;
import com.lifttrax.models.ExecutionSet;
import com.lifttrax.models.Lift;
import com.lifttrax.models.LiftExecution;
import com.lifttrax.models.SetMetric;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Builds HTML for the lightweight LiftTrax web interface.
 *
 * <p>Think of this class as a "template printer": it receives data from the database and turns it
 * into plain HTML strings that the browser can display.
 */
final class WebUiRenderer {
  static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

  private WebUiRenderer() {}

  /**
   * Stores previous form values so we can re-fill the Add Execution form after a submit. This helps
   * users fix mistakes without retyping everything.
   */
  record AddExecutionPrefill(
      String lift,
      String weight,
      String setCount,
      String rpe,
      String metricType,
      String metricValue,
      String metricLeft,
      String metricRight,
      String date,
      boolean warmup,
      boolean deload,
      String notes,
      boolean missed) {
    AddExecutionPrefill(
        String lift,
        String weight,
        String setCount,
        String rpe,
        String metricType,
        String metricValue,
        String metricLeft,
        String metricRight,
        String date,
        boolean warmup,
        boolean deload,
        String notes) {
      this(
          lift,
          weight,
          setCount,
          rpe,
          metricType,
          metricValue,
          metricLeft,
          metricRight,
          date,
          warmup,
          deload,
          notes,
          false);
    }

    static AddExecutionPrefill empty() {
      return new AddExecutionPrefill("", "", "1", "", "reps", "5", "5", "5", "", false, false, "");
    }
  }

  /** Produces the page body, rendering expensive tab bodies only when they are active. */
  static String renderIndexBody(
      TrainingDataStore db,
      List<Lift> lifts,
      String search,
      String queryLift,
      String activeTab,
      String statusMessage,
      String statusType,
      AddExecutionPrefill prefill,
      LocalDate lastWeekStart,
      LocalDate lastWeekEnd,
      int waveWeeks,
      Map<String, String> waveInput) {
    return renderIndexBody(
        db,
        lifts,
        search,
        queryLift,
        activeTab,
        statusMessage,
        statusType,
        prefill,
        lastWeekStart,
        lastWeekEnd,
        waveWeeks,
        waveInput,
        "");
  }

  static String renderIndexBody(
      TrainingDataStore db,
      List<Lift> lifts,
      String search,
      String queryLift,
      String activeTab,
      String statusMessage,
      String statusType,
      AddExecutionPrefill prefill,
      LocalDate lastWeekStart,
      LocalDate lastWeekEnd,
      int waveWeeks,
      Map<String, String> waveInput,
      String resumePanel) {
    String normalizedTab = normalizeTab(activeTab);
    String dashboardContent =
        "dashboard".equals(normalizedTab)
            ? resumePanel + renderDailyDashboard(db, LocalDate.now())
            : deferredTabContent("Dashboard");
    String executionContent =
        "executions".equals(normalizedTab)
            ? renderExecutionList(db, lifts, search, "Recorded lifts:")
            : deferredTabContent("Executions");
    String queryContent =
        "query".equals(normalizedTab)
            ? renderQueryContent(db, queryLift)
            : deferredTabContent("Query");
    String lastWeekContent =
        "last-week".equals(normalizedTab)
            ? renderLastWeekContent(db, lastWeekStart, lastWeekEnd, search)
            : deferredTabContent("Last Week");
    String waveContent =
        "waves".equals(normalizedTab)
            ? renderWaveContent(db, waveWeeks, waveInput)
            : deferredTabContent("Workout Waves");
    String importWorkoutContent =
        "import-workout".equals(normalizedTab)
            ? SavedWorkoutHtml.renderLibrary(db)
            : deferredTabContent("Import Workout");
    String exerciseContent =
        "exercises".equals(normalizedTab)
            ? ExecutionListHtml.renderCatalogList(db, lifts, search, "Exercise catalog:")
            : deferredTabContent("Exercise Catalog");
    return renderTabbedLayout(
        lifts,
        search,
        queryLift,
        normalizedTab,
        new TabContent(
            dashboardContent,
            executionContent,
            queryContent,
            lastWeekContent,
            waveContent,
            importWorkoutContent,
            exerciseContent),
        statusMessage,
        statusType,
        prefill,
        lastWeekStart,
        lastWeekEnd,
        waveWeeks,
        resumePanel);
  }

  static List<Lift> loadIndexLifts(TrainingDataStore db, String activeTab) throws Exception {
    return switch (activeTab) {
      case "dashboard", "import-workout" -> List.of();
      default -> db.listLifts();
    };
  }

  static String normalizeTab(String activeTab) {
    return switch (activeTab == null ? "" : activeTab.trim()) {
      case "dashboard",
          "add-execution",
          "waves",
          "import-workout",
          "executions",
          "query",
          "last-week",
          "exercises" ->
          activeTab.trim();
      default -> "dashboard";
    };
  }

  private static String deferredTabContent(String label) {
    return "<p class='muted'>Open this tab to load " + WebHtml.escapeHtml(label) + ".</p>";
  }

  /**
   * Wraps all major UI panels into one tabbed layout. The small JavaScript block here only handles
   * tab switching and client-side filtering.
   */
  static String renderTabbedLayout(
      List<Lift> lifts,
      String search,
      String queryLift,
      String activeTab,
      String executionContent,
      String queryContent,
      String lastWeekContent,
      String waveContent,
      String importWorkoutContent,
      String statusMessage,
      String statusType,
      AddExecutionPrefill prefill,
      LocalDate lastWeekStart,
      LocalDate lastWeekEnd,
      int waveWeeks) {
    return renderTabbedLayout(
        lifts,
        search,
        queryLift,
        activeTab,
        new TabContent(
            deferredTabContent("Dashboard"),
            executionContent,
            queryContent,
            lastWeekContent,
            waveContent,
            importWorkoutContent,
            executionContent),
        statusMessage,
        statusType,
        prefill,
        lastWeekStart,
        lastWeekEnd,
        waveWeeks,
        "");
  }

  static String renderTabbedLayout(
      List<Lift> lifts,
      String search,
      String queryLift,
      String activeTab,
      TabContent tabContent,
      String statusMessage,
      String statusType,
      AddExecutionPrefill prefill,
      LocalDate lastWeekStart,
      LocalDate lastWeekEnd,
      int waveWeeks,
      String resumePanel) {
    String filterControls =
        switch (activeTab) {
          case "add-execution", "executions", "query", "exercises" ->
              renderFilterControls(lifts, search);
          default -> "";
        };
    String addExecutionContent =
        addExecutionContent(activeTab, lifts, statusMessage, statusType, prefill, resumePanel);
    String queryControls =
        contentWhenActive(activeTab, "query", renderQueryControls(lifts, queryLift));
    String dashboardPanelClass = panelClass(activeTab, "dashboard");
    String addExecutionPanelClass = panelClass(activeTab, "add-execution");
    String executionsPanelClass = panelClass(activeTab, "executions");
    String wavesPanelClass = panelClass(activeTab, "waves");
    String importWorkoutPanelClass = panelClass(activeTab, "import-workout");
    String queryPanelClass = panelClass(activeTab, "query");
    String lastWeekPanelClass = panelClass(activeTab, "last-week");
    String exercisesPanelClass = panelClass(activeTab, "exercises");
    String dashboardLoaded = loaded(activeTab, "dashboard");
    String addExecutionLoaded = loaded(activeTab, "add-execution");
    String executionsLoaded = loaded(activeTab, "executions");
    String wavesLoaded = loaded(activeTab, "waves");
    String importWorkoutLoaded = loaded(activeTab, "import-workout");
    String queryLoaded = loaded(activeTab, "query");
    String lastWeekLoaded = loaded(activeTab, "last-week");
    String exercisesLoaded = loaded(activeTab, "exercises");

    return ClasspathText.read("tabbed-layout.html")
            .formatted(
                WebHtml.escapeHtml(activeTab),
                TaskNavigationHtml.renderSectionHeading(activeTab),
                dashboardPanelClass,
                dashboardLoaded,
                tabContent.dashboardContent(),
                addExecutionPanelClass,
                addExecutionLoaded,
                contentWhenActive(activeTab, "add-execution", filterControls),
                addExecutionContent,
                executionsPanelClass,
                executionsLoaded,
                contentWhenActive(activeTab, "executions", filterControls),
                tabContent.executionContent(),
                exercisesPanelClass,
                exercisesLoaded,
                contentWhenActive(activeTab, "exercises", filterControls),
                TaskNavigationHtml.renderStatusMessage(statusMessage, statusType),
                TaskNavigationHtml.renderNewLiftForm(),
                tabContent.exerciseContent(),
                wavesPanelClass,
                wavesLoaded,
                tabContent.waveContent(),
                importWorkoutPanelClass,
                importWorkoutLoaded,
                tabContent.importWorkoutContent(),
                queryPanelClass,
                queryLoaded,
                contentWhenActive(activeTab, "query", filterControls),
                queryControls,
                tabContent.queryContent(),
                lastWeekPanelClass,
                lastWeekLoaded,
                WebHtml.escapeHtml(lastWeekStart.toString()),
                WebHtml.escapeHtml(lastWeekEnd.toString()),
                contentWhenActive(activeTab, "last-week", filterControls),
                tabContent.lastWeekContent())
        + ClasspathText.script("tabbed-layout");
  }

  private static String addExecutionContent(
      String activeTab,
      List<Lift> lifts,
      String statusMessage,
      String statusType,
      AddExecutionPrefill prefill,
      String resumePanel) {
    if (!"add-execution".equals(activeTab)) {
      return deferredTabContent("Add Execution");
    }
    return TaskNavigationHtml.renderTrainIntro(resumePanel)
        + renderAddExecutionForm(lifts, statusMessage, statusType, prefill);
  }

  private static String contentWhenActive(String activeTab, String expectedTab, String content) {
    return expectedTab.equals(activeTab) ? content : "";
  }

  record TabContent(
      String dashboardContent,
      String executionContent,
      String queryContent,
      String lastWeekContent,
      String waveContent,
      String importWorkoutContent,
      String exerciseContent) {}

  private static String panelClass(String activeTab, String tab) {
    return tab.equals(activeTab) ? "tab-panel is-active" : "tab-panel";
  }

  private static String loaded(String activeTab, String tab) {
    return Boolean.toString(tab.equals(activeTab));
  }

  static String renderDailyDashboard(TrainingDataStore db, LocalDate today) {
    return DailyDashboardRenderer.render(db, today);
  }

  static String renderWaveContent(TrainingDataStore db, int weeks, Map<String, String> waveInput) {
    return WavePlannerHtml.renderWaveContent(db, weeks, waveInput);
  }

  static String renderLiftOptions(List<Lift> options, String selectedName) {
    return WavePlannerHtml.renderLiftOptions(options, selectedName);
  }

  static String renderLastWeekContent(
      TrainingDataStore db, List<Lift> lifts, LocalDate start, LocalDate end) {
    return LastWeekHtml.renderLastWeekContent(db, lifts, start, end);
  }

  private static String renderLastWeekContent(
      TrainingDataStore db, LocalDate start, LocalDate end, String search) {
    return LastWeekHtml.renderLastWeekContent(db, start, end, search);
  }

  static String renderAddExecutionForm(
      List<Lift> lifts, String statusMessage, String statusType, AddExecutionPrefill prefillInput) {
    return AddExecutionHtml.renderAddExecutionForm(lifts, statusMessage, statusType, prefillInput);
  }

  static String renderQueryControls(List<Lift> lifts, String selectedLift) {
    return QueryHistoryHtml.renderQueryControls(lifts, selectedLift);
  }

  static String renderQueryContent(TrainingDataStore db, String liftName) {
    return QueryHistoryHtml.renderQueryContent(db, liftName);
  }

  static String renderFilterControls(List<Lift> lifts, String search) {
    return QueryHistoryHtml.renderFilterControls(lifts, search);
  }

  static String renderLiftList(List<Lift> lifts, String search, String label) {
    return QueryHistoryHtml.renderLiftList(lifts, search, label);
  }

  static String renderLiftTrendSummary(List<LiftExecution> executions, LocalDate today) {
    return QueryHistoryHtml.renderLiftTrendSummary(executions, today);
  }

  static String renderExecutionList(
      TrainingDataStore db, List<Lift> lifts, String search, String label) {
    return ExecutionListHtml.renderExecutionList(db, lifts, search, label);
  }

  static String renderExecutionRows(TrainingDataStore db, String liftName) {
    return ExecutionListHtml.renderExecutionRows(db, liftName);
  }

  static String formatExecution(LiftExecution execution) {
    return ExecutionListHtml.formatExecution(execution);
  }

  static String formatSets(List<ExecutionSet> sets) {
    return ExecutionListHtml.formatSets(sets);
  }

  static String formatMetric(SetMetric metric) {
    return ExecutionListHtml.formatMetric(metric);
  }

  static String formatMainType(Lift lift) {
    return ExecutionListHtml.formatMainType(lift);
  }

  static String joinList(List<String> values) {
    return ExecutionListHtml.joinList(values);
  }

  static String urlEncode(String value) {
    return ExecutionListHtml.urlEncode(value);
  }
}
