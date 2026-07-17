package com.lifttrax.cli;

import com.lifttrax.db.TrainingDataStore;
import com.lifttrax.models.ExecutionSet;
import com.lifttrax.models.ExecutionSummaryFormatter;
import com.lifttrax.models.Lift;
import com.lifttrax.models.LiftExecution;
import com.lifttrax.models.LiftRegion;
import com.lifttrax.models.LiftStats;
import com.lifttrax.models.LiftType;
import com.lifttrax.models.Muscle;
import com.lifttrax.models.SetMetric;
import com.lifttrax.workout.ConjugateWorkoutBuilder;
import com.lifttrax.workout.DeloadWorkoutBuilder;
import com.lifttrax.workout.DynamicLifts;
import com.lifttrax.workout.HypertrophyWorkoutBuilder;
import com.lifttrax.workout.LiftTrendSummary;
import com.lifttrax.workout.MaxEffortLiftPools;
import com.lifttrax.workout.MaxEffortPlan;
import com.lifttrax.workout.PlannedWorkoutExporter;
import com.lifttrax.workout.PlannedWorkoutFile;
import com.lifttrax.workout.PlannedWorkoutMarkdownWriter;
import com.lifttrax.workout.WebConfiguredDynamicLiftSource;
import com.lifttrax.workout.WebConfiguredMaxEffortPlanSource;
import com.lifttrax.workout.WorkoutBuilder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

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
      String notes) {
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
    String normalizedTab = normalizeTab(activeTab);
    String dashboardContent =
        "dashboard".equals(normalizedTab)
            ? renderDailyDashboard(db, lifts, LocalDate.now())
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
            ? renderLastWeekContent(db, lifts, lastWeekStart, lastWeekEnd)
            : deferredTabContent("Last Week");
    String waveContent =
        "waves".equals(normalizedTab)
            ? renderWaveContent(db, waveWeeks, waveInput)
            : deferredTabContent("Workout Waves");
    String importWorkoutContent =
        "import-workout".equals(normalizedTab)
            ? PlannedWorkoutHtml.renderImportPanel()
            : deferredTabContent("Import Workout");
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
            importWorkoutContent),
        statusMessage,
        statusType,
        prefill,
        lastWeekStart,
        lastWeekEnd,
        waveWeeks);
  }

  private static String normalizeTab(String activeTab) {
    return switch (activeTab == null ? "" : activeTab.trim()) {
      case "dashboard",
          "add-execution",
          "waves",
          "import-workout",
          "executions",
          "query",
          "last-week" ->
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
            importWorkoutContent),
        statusMessage,
        statusType,
        prefill,
        lastWeekStart,
        lastWeekEnd,
        waveWeeks);
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
      int waveWeeks) {
    String filterControls = renderFilterControls(lifts, search);
    String addExecutionContent = renderAddExecutionForm(lifts, statusMessage, statusType, prefill);
    String queryControls = renderQueryControls(lifts, queryLift);
    String dashboardTabClass = tabClass(activeTab, "dashboard");
    String addExecutionTabClass = tabClass(activeTab, "add-execution");
    String wavesTabClass = tabClass(activeTab, "waves");
    String importWorkoutTabClass = tabClass(activeTab, "import-workout");
    String executionsTabClass = tabClass(activeTab, "executions");
    String queryTabClass = tabClass(activeTab, "query");
    String lastWeekTabClass = tabClass(activeTab, "last-week");
    String dashboardPanelClass = panelClass(activeTab, "dashboard");
    String addExecutionPanelClass = panelClass(activeTab, "add-execution");
    String executionsPanelClass = panelClass(activeTab, "executions");
    String wavesPanelClass = panelClass(activeTab, "waves");
    String importWorkoutPanelClass = panelClass(activeTab, "import-workout");
    String queryPanelClass = panelClass(activeTab, "query");
    String lastWeekPanelClass = panelClass(activeTab, "last-week");
    String dashboardLoaded = loaded(activeTab, "dashboard");
    String addExecutionLoaded = "true";
    String executionsLoaded = loaded(activeTab, "executions");
    String wavesLoaded = loaded(activeTab, "waves");
    String importWorkoutLoaded = loaded(activeTab, "import-workout");
    String queryLoaded = loaded(activeTab, "query");
    String lastWeekLoaded = loaded(activeTab, "last-week");

    return """
                <div class='tabbed-ui' data-initial-tab='%s'>
                  <div class='tabs' role='tablist' aria-label='LiftTrax sections'>
                    <button class='%s' role='tab' type='button' data-tab='dashboard' aria-selected='%s'>Dashboard</button>
                    <button class='%s' role='tab' type='button' data-tab='add-execution' aria-selected='%s'>Add Execution</button>
                    <button class='%s' role='tab' type='button' data-tab='waves' aria-selected='%s'>Workout Waves</button>
                    <button class='%s' role='tab' type='button' data-tab='import-workout' aria-selected='%s'>Import Workout</button>
                    <button class='%s' role='tab' type='button' data-tab='executions' aria-selected='%s'>Executions</button>
                    <button class='%s' role='tab' type='button' data-tab='query' aria-selected='%s'>Query</button>
                    <button class='%s' role='tab' type='button' data-tab='last-week' aria-selected='%s'>Last Week</button>
                  </div>
                  <section class='%s' data-panel='dashboard' data-loaded='%s' role='tabpanel'>
                    %s
                  </section>
                  <section class='%s' data-panel='add-execution' data-loaded='%s' role='tabpanel'>
                    <h2>Add Execution</h2>
                    %s
                    %s
                  </section>
                  <section class='%s' data-panel='executions' data-loaded='%s' role='tabpanel'>
                    %s
                    %s
                  </section>
                  <section class='%s' data-panel='waves' data-loaded='%s' role='tabpanel'>
                    <h2>Workout Waves</h2>
                    %s
                  </section>
                  <section class='%s' data-panel='import-workout' data-loaded='%s' role='tabpanel'>
                    <h2>Import Workout</h2>
                    %s
                  </section>
                  <section class='%s' data-panel='query' data-loaded='%s' role='tabpanel'>
                    <h2>Query</h2>
                    %s
                    %s
                    %s
                  </section>
                  <section class='%s' data-panel='last-week' data-loaded='%s' role='tabpanel'>
                    <h2>Last Week</h2>
                    <p class='muted'>This tab shows a 7-day window. Use Previous Week or adjust the dates to view older history.</p>
                    <form method='get' action='/' class='query-form compact-actions'>
                      <input type='hidden' name='tab' value='last-week'/>
                      <label>Start <input type='date' name='lastWeekStart' value='%s'/></label>
                      <label>End <input type='date' name='lastWeekEnd' value='%s'/></label>
                      <button type='submit' class='compact-btn'>Apply</button>
                      <button type='submit' name='lastWeekNav' value='prev' class='compact-btn'>← Previous Week</button>
                      <button type='submit' name='lastWeekNav' value='next' class='compact-btn'>Next Week →</button>
                      <button type='submit' name='lastWeekNav' value='current' class='compact-btn'>Current Week</button>
                      <button type='submit' name='lastWeekNav' value='last' class='compact-btn'>Last Week</button>
                    </form>
                    %s
                    %s
                  </section>
                </div>
                <script>
                  (function () {
                    const shell = document.querySelector('.tabbed-ui');
                    const tabs = document.querySelectorAll('.tab');
                    const panels = document.querySelectorAll('.tab-panel');

                    function activateTab(name) {
                      tabs.forEach((item) => {
                        const active = item.dataset.tab === name;
                        item.classList.toggle('is-active', active);
                        item.setAttribute('aria-selected', active ? 'true' : 'false');
                      });
                      panels.forEach((panel) => {
                        panel.classList.toggle('is-active', panel.dataset.panel === name);
                      });
                    }

                    const initialTab = shell && shell.dataset.initialTab ? shell.dataset.initialTab : 'dashboard';
                    activateTab(initialTab);

                    tabs.forEach((tab) => {
                      tab.addEventListener('click', () => {
                        const panel = document.querySelector(`[data-panel='${tab.dataset.tab}']`);
                        if (panel && panel.dataset.loaded === 'false') {
                          const url = new URL(window.location.href);
                          url.searchParams.set('tab', tab.dataset.tab);
                          window.location.href = url.toString();
                          return;
                        }
                        activateTab(tab.dataset.tab);
                      });
                    });

                    function visibleFocusable(control) {
                      if (!control || control.disabled) {
                        return false;
                      }
                      const rects = control.getClientRects();
                      return rects.length > 0;
                    }

                    function firstFocusable(scope) {
                      return Array.from(scope.querySelectorAll("input:not([type='hidden']), select, textarea, button, a[href]"))
                        .find((control) => visibleFocusable(control));
                    }

                    function focusControl(control) {
                      if (!control) {
                        return;
                      }
                      control.focus({preventScroll: true});
                      if (document.activeElement === control) {
                        control.scrollIntoView({block: 'nearest'});
                      }
                    }

                    function focusAfterNavigation() {
                      const params = new URLSearchParams(window.location.search);
                      const target = params.get('focus') || '';
                      if (!target) {
                        return;
                      }
                      const panel = document.querySelector('.tab-panel.is-active') || document;
                      let control = null;
                      if (target === 'add-weight') {
                        control = Array.from(panel.querySelectorAll("[data-focus-target='add-weight']"))
                          .find((item) => visibleFocusable(item));
                        if (!control) {
                          control = panel.querySelector("[name='setCount']");
                        }
                      } else if (target === 'add-lift') {
                        control = panel.querySelector("[data-focus-target='add-lift']");
                      } else if (target === 'save-execution') {
                        control = panel.querySelector("[data-focus-target='save-execution']");
                      } else if (target === 'execution-list') {
                        control = panel.querySelector('.js-exec-edit') || firstFocusable(panel);
                      }
                      focusControl(control);
                    }

                    const FILTER_STORAGE_PREFIX = 'lifttrax.filters.';

                    function syncWaveTypeVisibility() {
                      const waveType = document.querySelector("select[name='waveType']");
                      const conjugateSections = document.querySelectorAll('.js-conjugate-only');
                      if (!waveType || conjugateSections.length === 0) {
                        return;
                      }
                      const showConjugate = waveType.value === 'conjugate';
                      conjugateSections.forEach((section) => {
                        section.classList.toggle('is-hidden', !showConjugate);
                      });
                    }

                    const waveTypeSelect = document.querySelector("select[name='waveType']");
                    if (waveTypeSelect) {
                      waveTypeSelect.addEventListener('change', syncWaveTypeVisibility);
                      syncWaveTypeVisibility();
                    }

                    if (window.location.search.includes('waveGenerate=true')) {
                      const sanitized = new URL(window.location.href);
                      sanitized.search = '';
                      sanitized.searchParams.set('tab', 'waves');
                      window.history.replaceState({}, '', sanitized.toString());
                    }

                    function savePanelFilters(panel) {
                      const nameFilter = panel.querySelector('.js-filter-name');
                      const regionFilter = panel.querySelector('.js-filter-region');
                      const mainFilter = panel.querySelector('.js-filter-main');
                      const muscleFilter = panel.querySelector('.js-filter-muscle');
                      if (!nameFilter || !regionFilter || !mainFilter || !muscleFilter || !panel.dataset.panel) {
                        return;
                      }

                      const state = {
                        name: nameFilter.value || '',
                        region: regionFilter.value || '',
                        main: mainFilter.value || '',
                        muscles: Array.from(muscleFilter.selectedOptions).map((option) => option.value).filter((value) => value)
                      };

                      try {
                        localStorage.setItem(FILTER_STORAGE_PREFIX + panel.dataset.panel, JSON.stringify(state));
                      } catch (error) {
                        // Ignore storage issues and continue with in-memory filters.
                      }
                    }

                    function restorePanelFilters(panel) {
                      if (!panel.dataset.panel) {
                        return;
                      }
                      try {
                        const raw = localStorage.getItem(FILTER_STORAGE_PREFIX + panel.dataset.panel);
                        if (!raw) {
                          return;
                        }
                        const state = JSON.parse(raw);
                        const nameFilter = panel.querySelector('.js-filter-name');
                        const regionFilter = panel.querySelector('.js-filter-region');
                        const mainFilter = panel.querySelector('.js-filter-main');
                        const muscleFilter = panel.querySelector('.js-filter-muscle');
                        if (!nameFilter || !regionFilter || !mainFilter || !muscleFilter) {
                          return;
                        }

                        nameFilter.value = typeof state.name === 'string' ? state.name : '';
                        regionFilter.value = typeof state.region === 'string' ? state.region : '';
                        mainFilter.value = typeof state.main === 'string' ? state.main : '';

                        const selectedMuscles = Array.isArray(state.muscles) ? new Set(state.muscles) : new Set();
                        Array.from(muscleFilter.options).forEach((option) => {
                          option.selected = selectedMuscles.has(option.value);
                        });
                      } catch (error) {
                        // Ignore malformed storage content.
                      }
                    }

                    function applyPanelFilters(panel) {
                      const nameFilter = panel.querySelector('.js-filter-name');
                      const regionFilter = panel.querySelector('.js-filter-region');
                      const mainFilter = panel.querySelector('.js-filter-main');
                      const muscleFilter = panel.querySelector('.js-filter-muscle');
                      if (!nameFilter || !regionFilter || !mainFilter || !muscleFilter) {
                        return;
                      }

                      const searchValue = nameFilter.value.trim().toLowerCase();
                      const regionValue = regionFilter.value;
                      const mainValue = mainFilter.value;
                      const selectedMuscles = Array.from(muscleFilter.selectedOptions)
                        .map((option) => option.value)
                        .filter((value) => value);

                      const matchesFilters = (itemName, itemRegion, itemMain, itemMuscles) => {
                        const matchesName = !searchValue || itemName.includes(searchValue);
                        const matchesRegion = !regionValue || itemRegion === regionValue;
                        const matchesMain = !mainValue || itemMain === mainValue;
                        const matchesMuscle = selectedMuscles.length === 0 || selectedMuscles.some((muscle) => itemMuscles.includes(muscle));
                        return matchesName && matchesRegion && matchesMain && matchesMuscle;
                      };

                      panel.querySelectorAll('[data-filter-item]').forEach((item) => {
                        const itemName = (item.dataset.name || '').toLowerCase();
                        const itemRegion = item.dataset.region || '';
                        const itemMain = item.dataset.main || '';
                        const itemMuscles = (item.dataset.muscles || '').split(',').filter(Boolean);
                        item.classList.toggle('is-hidden', !matchesFilters(itemName, itemRegion, itemMain, itemMuscles));
                      });

                      panel.querySelectorAll("select[name='queryLift'], select[name='lift']").forEach((querySelect) => {
                        let hasVisibleSelection = false;
                        let firstVisibleValue = '';
                        Array.from(querySelect.options).forEach((option) => {
                          if (!option.hasAttribute('data-filter-option')) {
                            return;
                          }
                          const optionName = (option.dataset.name || '').toLowerCase();
                          const optionRegion = option.dataset.region || '';
                          const optionMain = option.dataset.main || '';
                          const optionMuscles = (option.dataset.muscles || '').split(',').filter(Boolean);
                          const visible = matchesFilters(optionName, optionRegion, optionMain, optionMuscles);
                          option.hidden = !visible;
                          option.disabled = !visible;
                          if (visible && !firstVisibleValue) {
                            firstVisibleValue = option.value;
                          }
                          if (visible && option.selected) {
                            hasVisibleSelection = true;
                          }
                        });

                        if (!hasVisibleSelection && firstVisibleValue) {
                          querySelect.value = firstVisibleValue;
                        }
                      });
                    }

                    function clearPanelFilters(panel) {
                      const nameFilter = panel.querySelector('.js-filter-name');
                      const regionFilter = panel.querySelector('.js-filter-region');
                      const mainFilter = panel.querySelector('.js-filter-main');
                      const muscleFilter = panel.querySelector('.js-filter-muscle');
                      if (!nameFilter || !regionFilter || !mainFilter || !muscleFilter) {
                        return;
                      }

                      nameFilter.value = '';
                      regionFilter.value = '';
                      mainFilter.value = '';
                      Array.from(muscleFilter.options).forEach((option) => {
                        option.selected = false;
                      });
                      applyPanelFilters(panel);
                      savePanelFilters(panel);
                    }

                    document.querySelectorAll('.tab-panel').forEach((panel) => {
                      restorePanelFilters(panel);
                      panel.querySelectorAll('.js-filter-name, .js-filter-region, .js-filter-main, .js-filter-muscle').forEach((control) => {
                        control.addEventListener('input', () => {
                          applyPanelFilters(panel);
                          savePanelFilters(panel);
                        });
                        control.addEventListener('change', () => {
                          applyPanelFilters(panel);
                          savePanelFilters(panel);
                        });
                      });
                      const clearButton = panel.querySelector('.js-clear-filters');
                      if (clearButton) {
                        clearButton.addEventListener('click', () => clearPanelFilters(panel));
                      }

                      if (panel.classList.contains('is-active')) {
                        applyPanelFilters(panel);
                        savePanelFilters(panel);
                      }
                    });

                    const addExecutionForm = document.querySelector("form.add-execution-form");
                    const addExecutionScope = addExecutionForm || document;

                    function syncMetricInputs() {
                      const metricType = addExecutionScope.querySelector("input[name='metricType']:checked");
                      const single = addExecutionScope.querySelector('.metric-single');
                      const lr = addExecutionScope.querySelectorAll('.metric-lr');
                      if (!metricType || !single || lr.length === 0) {
                        return;
                      }

                      const isLr = metricType.value === 'reps-lr';
                      single.classList.toggle('is-hidden', isLr);
                      lr.forEach((item) => item.classList.toggle('is-hidden', !isLr));
                    }

                    addExecutionScope.querySelectorAll("input[name='metricType']").forEach((item) => {
                      item.addEventListener('change', syncMetricInputs);
                    });
                    syncMetricInputs();

                    function selectRadio(name, value) {
                      const input = addExecutionScope.querySelector(`input[name='${name}'][value='${value}']`);
                      if (!input) {
                        return;
                      }
                      input.checked = true;
                      input.dispatchEvent(new Event('change', {bubbles: true}));
                    }

                    function setInputValue(name, value) {
                      const input = addExecutionScope.querySelector(`[name='${name}']`);
                      if (input) {
                        input.value = value;
                      }
                    }

                    function syncWeightMode() {
                      const selected = addExecutionScope.querySelector("input[name='weightMode']:checked");
                      const mode = selected ? selected.value : 'weight';
                      addExecutionScope.querySelectorAll('.weight-weight, .weight-lr, .weight-bands, .weight-accom, .weight-custom').forEach((group) => {
                        group.classList.add('is-hidden');
                      });
                      const active = addExecutionScope.querySelector(`.weight-${mode}`);
                      if (active) {
                        active.classList.remove('is-hidden');
                      }
                    }

                    function syncAccomMode() {
                      const accomMode = addExecutionScope.querySelector("select[name='accomMode']");
                      if (!accomMode) {
                        return;
                      }
                      const chains = addExecutionScope.querySelector('.accom-chains');
                      const bands = addExecutionScope.querySelector('.accom-bands');
                      const useBands = accomMode.value === 'bands';
                      if (chains) {
                        chains.classList.toggle('is-hidden', useBands);
                      }
                      if (bands) {
                        bands.classList.toggle('is-hidden', !useBands);
                      }
                    }

                    function computeWeight() {
                      const selectedBandValues = (name) => Array.from(addExecutionScope.querySelectorAll(`input[name='${name}']:checked`)).map((item) => item.value);
                      const mode = (addExecutionScope.querySelector("input[name='weightMode']:checked") || {}).value || 'weight';
                      if (mode === 'none') {
                        return 'none';
                      }
                      if (mode === 'custom') {
                        return (addExecutionScope.querySelector("input[name='customWeight']") || {}).value || '';
                      }
                      if (mode === 'lr') {
                        const l = (addExecutionScope.querySelector("input[name='weightLeft']") || {}).value || '';
                        const r = (addExecutionScope.querySelector("input[name='weightRight']") || {}).value || '';
                        const unit = (addExecutionScope.querySelector("select[name='weightUnitLr']") || {}).value || 'lb';
                        return `${l}${unit}|${r}${unit}`;
                      }
                      if (mode === 'bands') {
                        return selectedBandValues('weightBandColors').join('+');
                      }
                      if (mode === 'accom') {
                        const bar = (addExecutionScope.querySelector("input[name='accomBar']") || {}).value || '';
                        const unit = (addExecutionScope.querySelector("select[name='accomUnit']") || {}).value || 'lb';
                        const accomMode = (addExecutionScope.querySelector("select[name='accomMode']") || {}).value || 'chains';
                        if (accomMode === 'bands') {
                          const bands = selectedBandValues('accomBandColors').join('+');
                          return `${bar} ${unit}+${bands}`;
                        }
                        const chain = (addExecutionScope.querySelector("input[name='accomChain']") || {}).value || '';
                        return `${bar} ${unit}+${chain}c`;
                      }
                      const value = (addExecutionScope.querySelector("input[name='weightValue']") || {}).value || '';
                      const unit = (addExecutionScope.querySelector("select[name='weightUnit']") || {}).value || 'lb';
                      const computed = `${value} ${unit}`.trim();
                      if (!computed || computed === 'lb' || computed === 'kg') {
                        return (addExecutionScope.querySelector("input[name='customWeight']") || {}).value || '';
                      }
                      return computed;
                    }

                    const detailedSets = [];
                    function metricLabel(item) {
                      if (item.metricType === 'reps-lr') {
                        return `${item.metricLeft || '?'}L/${item.metricRight || '?'}R reps`;
                      }
                      if (item.metricType === 'time') {
                        return `${item.metricValue || '?'} sec`;
                      }
                      if (item.metricType === 'distance') {
                        return `${item.metricValue || '?'} ft`;
                      }
                      return `${item.metricValue || '?'} reps`;
                    }

                    function metricPayload() {
                      const type = (addExecutionScope.querySelector("input[name='metricType']:checked") || {}).value || 'reps';
                      const payload = {metricType: type};
                      if (type === 'reps-lr') {
                        payload.metricLeft = (addExecutionScope.querySelector("input[name='metricLeft']") || {}).value || '';
                        payload.metricRight = (addExecutionScope.querySelector("input[name='metricRight']") || {}).value || '';
                      } else {
                        payload.metricValue = (addExecutionScope.querySelector("input[name='metricValue']") || {}).value || '';
                      }
                      return payload;
                    }

                    function selectedSetEntryMode() {
                      return (addExecutionScope.querySelector("input[name='setEntryMode']:checked") || {}).value || 'multiple';
                    }

                    function setSetEntryMode(mode) {
                      const input = addExecutionScope.querySelector(`input[name='setEntryMode'][value='${mode}']`);
                      if (input) {
                        input.checked = true;
                      }
                      syncSetEntryMode();
                    }

                    function syncSetEntryMode() {
                      const individual = selectedSetEntryMode() === 'individual';
                      const multipleControls = addExecutionScope.querySelector('.entry-mode-multiple');
                      const details = addExecutionScope.querySelector('.individual-sets-details');
                      if (multipleControls) {
                        multipleControls.classList.toggle('is-hidden', individual);
                      }
                      if (details) {
                        details.open = individual;
                        details.classList.toggle('is-hidden', !individual);
                      }
                    }

                    function updateSetLogStatus() {
                      const status = addExecutionScope.querySelector('.js-set-log-status');
                      if (!status) {
                        return;
                      }
                      if (detailedSets.length === 0) {
                        status.textContent = 'No sets in log';
                        return;
                      }
                      status.textContent = detailedSets.length === 1 ? '1 set in log' : `${detailedSets.length} sets in log`;
                    }

                    function renderSetList() {
                      const list = addExecutionScope.querySelector('.js-set-list');
                      const hidden = addExecutionScope.querySelector('.js-detailed-sets');
                      if (!list || !hidden) {
                        return;
                      }
                      list.innerHTML = '';
                      detailedSets.forEach((item, index) => {
                        const li = document.createElement('li');
                        const rpeText = item.rpe ? `, rpe ${item.rpe}` : '';
                        li.textContent = `${metricLabel(item)} @ ${item.weight || 'none'}${rpeText}`;
                        const remove = document.createElement('button');
                        remove.type = 'button';
                        remove.className = 'secondary';
                        remove.textContent = 'Remove';
                        remove.addEventListener('click', () => {
                          detailedSets.splice(index, 1);
                          renderSetList();
                          focusControl(addSetBtn || list);
                        });
                        li.appendChild(document.createTextNode(' '));
                        li.appendChild(remove);
                        list.appendChild(li);
                      });
                      hidden.value = JSON.stringify(detailedSets);
                      updateSetLogStatus();
                      saveAddExecutionDraft(addExecutionForm);
                    }

                    addExecutionScope.querySelectorAll("input[name='weightMode']").forEach((item) => {
                      item.addEventListener('change', syncWeightMode);
                    });
                    const accomModeSelect = addExecutionScope.querySelector("select[name='accomMode']");
                    if (accomModeSelect) {
                      accomModeSelect.addEventListener('change', syncAccomMode);
                    }
                    addExecutionScope.querySelectorAll("input[name='setEntryMode']").forEach((item) => {
                      item.addEventListener('change', syncSetEntryMode);
                    });

                    const maxEffortSingleBtn = addExecutionScope.querySelector('.js-max-effort-single');
                    if (maxEffortSingleBtn) {
                      maxEffortSingleBtn.addEventListener('click', () => {
                        selectRadio('metricType', 'reps');
                        setInputValue('metricValue', '1');
                        setInputValue('setCount', '1');
                        setInputValue('setCopies', '1');
                      });
                    }

                    const bandsOnlyBtn = addExecutionScope.querySelector('.js-bands-only');
                    if (bandsOnlyBtn) {
                      bandsOnlyBtn.addEventListener('click', () => selectRadio('weightMode', 'bands'));
                    }

                    const barBandsBtn = addExecutionScope.querySelector('.js-bar-bands');
                    if (barBandsBtn) {
                      barBandsBtn.addEventListener('click', () => {
                        selectRadio('weightMode', 'accom');
                        const mode = addExecutionScope.querySelector("select[name='accomMode']");
                        if (mode) {
                          mode.value = 'bands';
                          syncAccomMode();
                        }
                      });
                    }

                    const addSetBtn = addExecutionScope.querySelector('.js-add-set');
                    if (addSetBtn) {
                      addSetBtn.addEventListener('click', (event) => {
                        event.preventDefault();
                        setSetEntryMode('individual');
                        const setCopiesInput = addExecutionScope.querySelector("input[name='setCopies']");
                        const copies = Math.max(1, parseInt((setCopiesInput && setCopiesInput.value) || '1', 10) || 1);
                        const payload = {
                          ...metricPayload(),
                          weight: computeWeight(),
                          rpe: (addExecutionScope.querySelector("input[name='rpe']") || {}).value || ''
                        };
                        for (let i = 0; i < copies; i++) {
                          detailedSets.push({...payload});
                        }
                        renderSetList();
                        focusControl(addSetBtn);
                      });
                    }
                    const clearSetsBtn = addExecutionScope.querySelector('.js-clear-sets');
                    if (clearSetsBtn) {
                      clearSetsBtn.addEventListener('click', (event) => {
                        event.preventDefault();
                        detailedSets.splice(0, detailedSets.length);
                        renderSetList();
                        focusControl(addSetBtn || clearSetsBtn);
                      });
                    }

                    const ADD_EXECUTION_DRAFT_KEY = 'lifttrax.addExecutionDraft.v1';
                    let restoringAddExecutionDraft = false;

                    function addExecutionParams() {
                      return new URLSearchParams(window.location.search);
                    }

                    function shouldClearAddExecutionDraft() {
                      const params = addExecutionParams();
                      return params.get('tab') === 'add-execution'
                        && params.get('statusType') === 'success'
                        && params.get('status') === 'Execution saved';
                    }

                    function hasServerAddExecutionPrefill() {
                      return Array.from(addExecutionParams().keys()).some((key) => key.startsWith('prefill'));
                    }

                    function addExecutionControlValue(form, name) {
                      return (form.querySelector(`[name='${name}']`) || {}).value || '';
                    }

                    function addExecutionCheckedValues(form, name) {
                      return Array.from(form.querySelectorAll(`input[name='${name}']:checked`)).map((item) => item.value);
                    }

                    function setCheckedValues(form, name, values) {
                      const selected = Array.isArray(values) ? new Set(values) : new Set();
                      form.querySelectorAll(`input[name='${name}']`).forEach((input) => {
                        input.checked = selected.has(input.value);
                      });
                    }

                    function collectAddExecutionDraft(form) {
                      return {
                        lift: addExecutionControlValue(form, 'lift'),
                        weightMode: (form.querySelector("input[name='weightMode']:checked") || {}).value || 'weight',
                        setEntryMode: selectedSetEntryMode(),
                        metricType: (form.querySelector("input[name='metricType']:checked") || {}).value || 'reps',
                        weightValue: addExecutionControlValue(form, 'weightValue'),
                        weightUnit: addExecutionControlValue(form, 'weightUnit'),
                        weightLeft: addExecutionControlValue(form, 'weightLeft'),
                        weightRight: addExecutionControlValue(form, 'weightRight'),
                        weightUnitLr: addExecutionControlValue(form, 'weightUnitLr'),
                        weightBandColors: addExecutionCheckedValues(form, 'weightBandColors'),
                        accomBar: addExecutionControlValue(form, 'accomBar'),
                        accomUnit: addExecutionControlValue(form, 'accomUnit'),
                        accomMode: addExecutionControlValue(form, 'accomMode'),
                        accomChain: addExecutionControlValue(form, 'accomChain'),
                        accomBandColors: addExecutionCheckedValues(form, 'accomBandColors'),
                        customWeight: addExecutionControlValue(form, 'customWeight'),
                        setCount: addExecutionControlValue(form, 'setCount'),
                        rpe: addExecutionControlValue(form, 'rpe'),
                        metricValue: addExecutionControlValue(form, 'metricValue'),
                        metricLeft: addExecutionControlValue(form, 'metricLeft'),
                        metricRight: addExecutionControlValue(form, 'metricRight'),
                        setCopies: addExecutionControlValue(form, 'setCopies'),
                        date: addExecutionControlValue(form, 'date'),
                        warmup: (form.querySelector("input[name='warmup']") || {}).checked || false,
                        deload: (form.querySelector("input[name='deload']") || {}).checked || false,
                        notes: addExecutionControlValue(form, 'notes'),
                        detailedSets: detailedSets.map((item) => ({...item}))
                      };
                    }

                    function applyAddExecutionDraft(form, draft) {
                      if (!draft || typeof draft !== 'object') {
                        return;
                      }
                      restoringAddExecutionDraft = true;
                      [
                        'lift',
                        'weightValue',
                        'weightUnit',
                        'weightLeft',
                        'weightRight',
                        'weightUnitLr',
                        'accomBar',
                        'accomUnit',
                        'accomMode',
                        'accomChain',
                        'customWeight',
                        'setCount',
                        'rpe',
                        'metricValue',
                        'metricLeft',
                        'metricRight',
                        'setCopies',
                        'date',
                        'notes'
                      ].forEach((name) => {
                        if (Object.prototype.hasOwnProperty.call(draft, name)) {
                          setInputValue(name, draft[name] || '');
                        }
                      });
                      setCheckedValues(form, 'weightBandColors', draft.weightBandColors);
                      setCheckedValues(form, 'accomBandColors', draft.accomBandColors);
                      const warmup = form.querySelector("input[name='warmup']");
                      if (warmup) {
                        warmup.checked = Boolean(draft.warmup);
                      }
                      const deload = form.querySelector("input[name='deload']");
                      if (deload) {
                        deload.checked = Boolean(draft.deload);
                      }
                      selectRadio('weightMode', draft.weightMode || 'weight');
                      setSetEntryMode(draft.setEntryMode || 'multiple');
                      selectRadio('metricType', draft.metricType || 'reps');
                      detailedSets.splice(
                        0,
                        detailedSets.length,
                        ...(Array.isArray(draft.detailedSets) ? draft.detailedSets.map((item) => ({...item})) : []));
                      syncMetricInputs();
                      syncWeightMode();
                      syncAccomMode();
                      syncSetEntryMode();
                      renderSetList();
                      restoringAddExecutionDraft = false;
                    }

                    function loadAddExecutionDraft(form) {
                      try {
                        if (shouldClearAddExecutionDraft()) {
                          localStorage.removeItem(ADD_EXECUTION_DRAFT_KEY);
                          return;
                        }
                        if (hasServerAddExecutionPrefill()) {
                          return;
                        }
                        const raw = localStorage.getItem(ADD_EXECUTION_DRAFT_KEY);
                        if (!raw) {
                          return;
                        }
                        applyAddExecutionDraft(form, JSON.parse(raw));
                      } catch (error) {
                        // Ignore malformed or unavailable storage.
                      }
                    }

                    function saveAddExecutionDraft(form) {
                      if (!form || restoringAddExecutionDraft) {
                        return;
                      }
                      try {
                        localStorage.setItem(ADD_EXECUTION_DRAFT_KEY, JSON.stringify(collectAddExecutionDraft(form)));
                      } catch (error) {
                        // Ignore storage issues and continue.
                      }
                    }

                    if (addExecutionForm) {
                      loadAddExecutionDraft(addExecutionForm);
                      addExecutionForm.addEventListener('change', () => saveAddExecutionDraft(addExecutionForm));
                      addExecutionForm.addEventListener('input', () => saveAddExecutionDraft(addExecutionForm));
                      addExecutionForm.addEventListener('submit', () => {
                        saveAddExecutionDraft(addExecutionForm);
                        const hiddenWeight = addExecutionForm.querySelector('.js-weight-hidden');
                        if (hiddenWeight) {
                          hiddenWeight.value = computeWeight();
                        }
                        if (selectedSetEntryMode() === 'multiple') {
                          detailedSets.splice(0, detailedSets.length);
                          renderSetList();
                        }

                        const submitter = document.activeElement;
                        const isLoadLast = submitter && submitter.getAttribute('formaction') === '/load-last-execution';
                        if (isLoadLast) {
                          const warmup = addExecutionForm.querySelector("input[name='warmup']");
                          const deload = addExecutionForm.querySelector("input[name='deload']");

                          Array.from(addExecutionForm.querySelectorAll("input[name='warmup'], input[name='deload']"))
                            .forEach((input) => input.disabled = true);

                          if (warmup && warmup.checked) {
                            warmup.disabled = false;
                          }
                          if (deload && deload.checked) {
                            deload.disabled = false;
                          }
                        }
                      });
                    }
                    syncSetEntryMode();

                    function syncNewLiftMuscles() {
                      const form = document.querySelector("form.new-lift-form");
                      if (!form) {
                        return;
                      }
                      const select = form.querySelector('.js-new-lift-muscles');
                      const hidden = form.querySelector('.js-new-lift-muscles-hidden');
                      if (!select || !hidden) {
                        return;
                      }
                      hidden.value = Array.from(select.selectedOptions)
                        .map((option) => option.value)
                        .filter((value) => value)
                        .join(',');
                    }

                    const newLiftForm = document.querySelector("form.new-lift-form");
                    if (newLiftForm) {
                      const newLiftMusclesSelect = newLiftForm.querySelector('.js-new-lift-muscles');
                      if (newLiftMusclesSelect) {
                        newLiftMusclesSelect.addEventListener('change', syncNewLiftMuscles);
                      }
                      newLiftForm.addEventListener('submit', syncNewLiftMuscles);
                      syncNewLiftMuscles();
                    }

                    function setExecutionEditing(item, editing) {
                      const view = item.querySelector('.js-exec-view');
                      const form = item.querySelector('.js-exec-form');
                      if (!view || !form) {
                        return;
                      }

                      view.style.display = editing ? 'none' : 'flex';
                      form.style.display = editing ? 'flex' : 'none';
                      form.querySelectorAll('input, select, textarea, button').forEach((control) => {
                        control.disabled = !editing;
                      });
                    }

                    function toggleSetMetricRow(row) {
                      const metric = row.querySelector('.js-set-metric');
                      const value = row.querySelector('.js-set-value');
                      const left = row.querySelector('.js-set-left');
                      const right = row.querySelector('.js-set-right');
                      if (!metric || !value || !left || !right) {
                        return;
                      }
                      const isLr = metric.value === 'reps-lr';
                      value.style.display = isLr ? 'none' : 'inline-block';
                      left.style.display = isLr ? 'inline-block' : 'none';
                      right.style.display = isLr ? 'inline-block' : 'none';
                    }

                    function renderSetRows(form, sets) {
                      const container = form.querySelector('.js-edit-sets');
                      if (!container) {
                        return;
                      }
                      container.innerHTML = '';
                      sets.forEach((set) => {
                        const row = document.createElement('div');
                        row.className = 'js-set-row';
                        row.style.display = 'flex';
                        row.style.alignItems = 'center';
                        row.style.gap = '8px';
                        row.style.flexWrap = 'nowrap';
                        row.style.overflowX = 'auto';
                        row.innerHTML = `
                          <select class='js-set-metric'>
                            <option value='reps'>reps</option>
                            <option value='reps-lr'>reps-lr</option>
                            <option value='time'>time</option>
                            <option value='distance'>distance</option>
                          </select>
                          <input type='number' class='js-set-value' placeholder='value' style='width:90px;' />
                          <input type='number' class='js-set-left' placeholder='left' style='width:80px;' />
                          <input type='number' class='js-set-right' placeholder='right' style='width:80px;' />
                          <input type='text' class='js-set-weight' placeholder='weight' style='width:130px;' />
                          <input type='number' step='0.1' class='js-set-rpe' placeholder='rpe' style='width:80px;' />
                          <button type='button' class='secondary compact-btn js-remove-set'>Remove</button>
                        `;
                        const metric = row.querySelector('.js-set-metric');
                        const value = row.querySelector('.js-set-value');
                        const left = row.querySelector('.js-set-left');
                        const right = row.querySelector('.js-set-right');
                        const weight = row.querySelector('.js-set-weight');
                        const rpe = row.querySelector('.js-set-rpe');
                        metric.value = set.metricType || 'reps';
                        value.value = set.metricValue || '';
                        left.value = set.metricLeft || '';
                        right.value = set.metricRight || '';
                        weight.value = set.weight || '';
                        rpe.value = set.rpe || '';
                        toggleSetMetricRow(row);
                        container.appendChild(row);
                      });
                    }

                    function collectSetRows(form) {
                      const rows = Array.from(form.querySelectorAll('.js-set-row'));
                      return rows.map((row) => {
                        const metricType = (row.querySelector('.js-set-metric') || {}).value || 'reps';
                        const metricValue = (row.querySelector('.js-set-value') || {}).value || '';
                        const metricLeft = (row.querySelector('.js-set-left') || {}).value || '';
                        const metricRight = (row.querySelector('.js-set-right') || {}).value || '';
                        const weight = (row.querySelector('.js-set-weight') || {}).value || '';
                        const rpe = (row.querySelector('.js-set-rpe') || {}).value || '';
                        return { metricType, metricValue, metricLeft, metricRight, weight, rpe };
                      });
                    }

                    function bindExecutionForm(form) {
                      let initialSets = [];
                      try {
                        initialSets = JSON.parse(form.dataset.initialSets || '[]');
                      } catch (error) {
                        initialSets = [];
                      }
                      renderSetRows(form, initialSets);
                      form.querySelectorAll('.js-set-row').forEach((row) => toggleSetMetricRow(row));

                      form.addEventListener('change', (event) => {
                        const row = event.target.closest('.js-set-row');
                        if (!row) {
                          return;
                        }
                        if (event.target.classList.contains('js-set-metric')) {
                          toggleSetMetricRow(row);
                        }
                      });

                      form.addEventListener('click', (event) => {
                        const add = event.target.closest('.js-add-set');
                        if (add) {
                          event.preventDefault();
                          const current = collectSetRows(form);
                          current.push({ metricType: 'reps', metricValue: '5', metricLeft: '', metricRight: '', weight: '', rpe: '' });
                          renderSetRows(form, current);
                          focusControl(form.querySelector('.js-set-row:last-child .js-set-metric'));
                          return;
                        }
                        const remove = event.target.closest('.js-remove-set');
                        if (remove) {
                          event.preventDefault();
                          const row = remove.closest('.js-set-row');
                          if (row) {
                            const nextFocus = row.nextElementSibling || row.previousElementSibling;
                            row.remove();
                            focusControl(nextFocus ? firstFocusable(nextFocus) : form.querySelector('.js-add-set'));
                          }
                        }
                      });

                      form.addEventListener('submit', (event) => {
                        const hidden = form.querySelector('.js-detailed-sets');
                        if (!hidden) {
                          return;
                        }
                        const sets = collectSetRows(form);
                        if (sets.length === 0) {
                          event.preventDefault();
                          window.alert('At least one set is required.');
                          return;
                        }
                        hidden.value = JSON.stringify(sets);
                      });
                    }

                    function bindExecutionActions(scope) {
                      scope.querySelectorAll('.js-exec-form').forEach((form) => bindExecutionForm(form));

                      scope.querySelectorAll('.js-exec-edit').forEach((button) => {
                      button.addEventListener('click', (event) => {
                        event.preventDefault();
                        const item = button.closest('.execution-item');
                        if (!item) {
                          return;
                        }
                        const form = item.querySelector('.js-exec-form');
                        setExecutionEditing(item, true);
                        focusControl(form ? firstFocusable(form) : null);
                      });
                    });

                      scope.querySelectorAll('.js-exec-cancel').forEach((button) => {
                      button.addEventListener('click', (event) => {
                        event.preventDefault();
                        const item = button.closest('.execution-item');
                        if (!item) {
                          return;
                        }
                        const form = item.querySelector('.js-exec-form');
                        if (form) {
                          let initialSets = [];
                          try {
                            initialSets = JSON.parse(form.dataset.initialSets || '[]');
                          } catch (error) {
                            initialSets = [];
                          }
                          renderSetRows(form, initialSets);
                          form.reset();
                        }
                        setExecutionEditing(item, false);
                        focusControl(item.querySelector('.js-exec-edit'));
                      });
                    });

                      scope.querySelectorAll('.js-exec-delete').forEach((link) => {
                      link.addEventListener('click', (event) => {
                        event.preventDefault();
                        const item = link.closest('.execution-item');
                        if (!item) {
                          return;
                        }
                        const form = item.querySelector('.js-exec-delete-form');
                        if (!form) {
                          return;
                        }
                        if (window.confirm('Delete this execution?')) {
                          form.submit();
                        }
                      });
                    });
                    }

                    bindExecutionActions(document);

                    document.querySelectorAll('.execution-item').forEach((item) => {
                      setExecutionEditing(item, false);
                    });

                    document.querySelectorAll('.execution-lift-group').forEach((group) => {
                      group.addEventListener('toggle', async () => {
                        if (!group.open) {
                          return;
                        }
                        const body = group.querySelector('.js-exec-body');
                        if (!body || body.dataset.loaded === 'true') {
                          return;
                        }
                        const lift = body.dataset.lift || '';
                        if (!lift) {
                          body.innerHTML = "<div class='status error'>Missing lift name</div>";
                          return;
                        }
                        body.innerHTML = "<p>Loading...</p>";
                        try {
                          const response = await fetch('/executions-fragment?lift=' + encodeURIComponent(lift));
                          const html = await response.text();
                          body.innerHTML = html;
                          body.dataset.loaded = 'true';
                          bindExecutionActions(body);
                          body.querySelectorAll('.execution-item').forEach((item) => setExecutionEditing(item, false));
                        } catch (error) {
                          body.innerHTML = "<div class='status error'>Failed to load executions.</div>";
                        }
                      });
                    });

                    syncWeightMode();
                    syncAccomMode();
                    focusAfterNavigation();
                  })();
                </script>
                """
        .formatted(
            WebHtml.escapeHtml(activeTab),
            dashboardTabClass,
            selected(activeTab, "dashboard"),
            addExecutionTabClass,
            selected(activeTab, "add-execution"),
            wavesTabClass,
            selected(activeTab, "waves"),
            importWorkoutTabClass,
            selected(activeTab, "import-workout"),
            executionsTabClass,
            selected(activeTab, "executions"),
            queryTabClass,
            selected(activeTab, "query"),
            lastWeekTabClass,
            selected(activeTab, "last-week"),
            dashboardPanelClass,
            dashboardLoaded,
            tabContent.dashboardContent(),
            addExecutionPanelClass,
            addExecutionLoaded,
            filterControls,
            addExecutionContent,
            executionsPanelClass,
            executionsLoaded,
            filterControls,
            tabContent.executionContent(),
            wavesPanelClass,
            wavesLoaded,
            tabContent.waveContent(),
            importWorkoutPanelClass,
            importWorkoutLoaded,
            tabContent.importWorkoutContent(),
            queryPanelClass,
            queryLoaded,
            filterControls,
            queryControls,
            tabContent.queryContent(),
            lastWeekPanelClass,
            lastWeekLoaded,
            WebHtml.escapeHtml(lastWeekStart.toString()),
            WebHtml.escapeHtml(lastWeekEnd.toString()),
            filterControls,
            tabContent.lastWeekContent());
  }

  record TabContent(
      String dashboardContent,
      String executionContent,
      String queryContent,
      String lastWeekContent,
      String waveContent,
      String importWorkoutContent) {}

  private static String tabClass(String activeTab, String tab) {
    return tab.equals(activeTab) ? "tab is-active" : "tab";
  }

  private static String panelClass(String activeTab, String tab) {
    return tab.equals(activeTab) ? "tab-panel is-active" : "tab-panel";
  }

  private static String selected(String activeTab, String tab) {
    return Boolean.toString(tab.equals(activeTab));
  }

  private static String loaded(String activeTab, String tab) {
    return Boolean.toString(tab.equals(activeTab));
  }

  static String renderDailyDashboard(TrainingDataStore db, List<Lift> lifts, LocalDate today) {
    return DailyDashboardRenderer.render(db, lifts, today);
  }

  static String renderWaveContent(TrainingDataStore db, int weeks, Map<String, String> waveInput) {
    int normalizedWeeks = Math.max(1, weeks);
    String planner = renderWavePlannerForm(db, normalizedWeeks, waveInput);
    boolean shouldGenerate = "true".equalsIgnoreCase(waveInput.getOrDefault("waveGenerate", ""));
    try {
      if (!shouldGenerate) {
        return planner + "<p>Review movements, then click Generate Wave.</p>";
      }

      String waveType =
          waveInput.getOrDefault("waveType", "conjugate").trim().toLowerCase(Locale.ROOT);
      WorkoutBuilder builder =
          switch (waveType) {
            case "hypertrophy" -> new HypertrophyWorkoutBuilder();
            case "deload" -> new DeloadWorkoutBuilder();
            default ->
                new ConjugateWorkoutBuilder(
                    new WebConfiguredMaxEffortPlanSource(normalizedWeeks, waveInput),
                    new WebConfiguredDynamicLiftSource(waveInput));
          };
      var wave = builder.getWave(normalizedWeeks, db);
      PlannedWorkoutFile plannedWorkout =
          PlannedWorkoutExporter.fromWave(waveProgramName(waveType), waveType, wave);
      List<String> markdown = PlannedWorkoutMarkdownWriter.createMarkdown(plannedWorkout, db);
      String markdownText = String.join("\n", markdown);
      return planner
          + """
                    <p>Configured weeks: <strong>%s</strong></p>
                    %s
                    <pre class='query-output'>%s</pre>
                    """
              .formatted(
                  normalizedWeeks,
                  PlannedWorkoutHtml.renderOutputActions(plannedWorkout),
                  WebHtml.escapeHtml(markdownText));
    } catch (Exception e) {
      return planner
          + """
                    <p>Configured weeks: <strong>%s</strong></p>
                    <p class='status error'>Failed to generate wave: %s</p>
                    """
              .formatted(normalizedWeeks, WebHtml.escapeHtml(e.getMessage()));
    }
  }

  private static String waveProgramName(String waveType) {
    return switch (waveType) {
      case "hypertrophy" -> "Hypertrophy Wave";
      case "deload" -> "Deload Wave";
      default -> "Conjugate Wave";
    };
  }

  private static String renderWavePlannerForm(
      TrainingDataStore db, int weeks, Map<String, String> values) {
    StringBuilder html = new StringBuilder();
    html.append("<form method='get' action='/' class='query-form' style='display:block;'>");
    html.append("<input type='hidden' name='tab' value='waves'/>");
    html.append("<input type='hidden' name='waveGenerate' value='true'/>");

    String waveType = values.getOrDefault("waveType", "conjugate").trim().toLowerCase(Locale.ROOT);
    html.append("<label>Weeks <input type='number' name='waveWeeks' min='1' max='24' value='")
        .append(weeks)
        .append("'/></label>");
    html.append("<label>Wave Type <select name='waveType'>")
        .append("<option value='conjugate'")
        .append("conjugate".equals(waveType) ? " selected" : "")
        .append(">Conjugate</option>")
        .append("<option value='hypertrophy'")
        .append("hypertrophy".equals(waveType) ? " selected" : "")
        .append(">Hypertrophy</option>")
        .append("<option value='deload'")
        .append("deload".equals(waveType) ? " selected" : "")
        .append(">Deload</option>")
        .append("</select></label>");

    try {
      List<Lift> squats = db.liftsByType(LiftType.SQUAT);
      List<Lift> deadlifts = db.liftsByType(LiftType.DEADLIFT);
      List<Lift> benches = db.liftsByType(LiftType.BENCH_PRESS);
      List<Lift> overheads = db.liftsByType(LiftType.OVERHEAD_PRESS);
      DynamicLifts dynamicDefaults = DynamicLifts.fromDatabase(db, false);

      MaxEffortLiftPools pools = new MaxEffortLiftPools(weeks, db);
      List<Lift> lowerDefaults = pools.lowerWeeks();
      List<Lift> upperDefaults = pools.upperWeeks();
      List<MaxEffortPlan.DeloadLowerLifts> lowerDeloadDefaults =
          MaxEffortPlan.deriveLowerDeloadFromPlan(lowerDefaults);
      List<MaxEffortPlan.DeloadUpperLifts> upperDeloadDefaults =
          MaxEffortPlan.deriveUpperDeloadFromPlan(upperDefaults);

      html.append("<div class='js-conjugate-only'>");
      html.append("<h3>Max Effort Rotation</h3>");

      for (int i = 0; i < weeks; i++) {
        int week = i + 1;
        if (week % 7 == 0) {
          continue;
        }
        List<Lift> lowerOpts = (i % 2 == 0) ? squats : deadlifts;
        List<Lift> upperOpts = (i % 2 == 0) ? benches : overheads;
        String lowerKey = "meLowerWeek" + week;
        String upperKey = "meUpperWeek" + week;
        String lowerCurrent = values.getOrDefault(lowerKey, lowerDefaults.get(i).name());
        String upperCurrent = values.getOrDefault(upperKey, upperDefaults.get(i).name());
        html.append("<div class='stacked-row'>");
        html.append("<label>Week ")
            .append(week)
            .append(" Lower <select name='")
            .append(lowerKey)
            .append("'>")
            .append(renderLiftOptions(lowerOpts, lowerCurrent))
            .append("</select></label>");
        html.append("<label>Week ")
            .append(week)
            .append(" Upper <select name='")
            .append(upperKey)
            .append("'>")
            .append(renderLiftOptions(upperOpts, upperCurrent))
            .append("</select></label>");
        html.append("</div>");
      }

      if (!lowerDeloadDefaults.isEmpty() || !upperDeloadDefaults.isEmpty()) {
        html.append("<h3>Deload Weeks</h3>");
      }
      for (int i = 0; i < lowerDeloadDefaults.size(); i++) {
        int n = i + 1;
        int week = n * 7;
        MaxEffortPlan.DeloadLowerLifts def = lowerDeloadDefaults.get(i);
        String squatKey = "meLowerDeload" + n + "Squat";
        String deadliftKey = "meLowerDeload" + n + "Deadlift";
        html.append("<div class='stacked-row'>");
        html.append("<label>Week ")
            .append(week)
            .append(" Lower Squat <select name='")
            .append(squatKey)
            .append("'>")
            .append(renderLiftOptions(squats, values.getOrDefault(squatKey, def.squat().name())))
            .append("</select></label>");
        html.append("<label>Week ")
            .append(week)
            .append(" Lower Deadlift <select name='")
            .append(deadliftKey)
            .append("'>")
            .append(
                renderLiftOptions(
                    deadlifts, values.getOrDefault(deadliftKey, def.deadlift().name())))
            .append("</select></label>");
        html.append("</div>");
      }
      for (int i = 0; i < upperDeloadDefaults.size(); i++) {
        int n = i + 1;
        int week = n * 7;
        MaxEffortPlan.DeloadUpperLifts def = upperDeloadDefaults.get(i);
        String benchKey = "meUpperDeload" + n + "Bench";
        String overheadKey = "meUpperDeload" + n + "Overhead";
        html.append("<div class='stacked-row'>");
        html.append("<label>Week ")
            .append(week)
            .append(" Upper Bench <select name='")
            .append(benchKey)
            .append("'>")
            .append(renderLiftOptions(benches, values.getOrDefault(benchKey, def.bench().name())))
            .append("</select></label>");
        html.append("<label>Week ")
            .append(week)
            .append(" Upper Overhead <select name='")
            .append(overheadKey)
            .append("'>")
            .append(
                renderLiftOptions(
                    overheads, values.getOrDefault(overheadKey, def.overhead().name())))
            .append("</select></label>");
        html.append("</div>");
      }

      html.append("<h3>Dynamic Effort Lifts</h3>");
      html.append("<div class='stacked-row'>")
          .append("<label>Squat <select name='deSquat'>")
          .append(renderLiftOptions(squats, values.get("deSquat")))
          .append("</select></label>")
          .append("<label>Deadlift <select name='deDeadlift'>")
          .append(renderLiftOptions(deadlifts, values.get("deDeadlift")))
          .append("</select></label>")
          .append("<label>Bench <select name='deBench'>")
          .append(renderLiftOptions(benches, values.get("deBench")))
          .append("</select></label>")
          .append("<label>Overhead <select name='deOverhead'>")
          .append(renderLiftOptions(overheads, values.get("deOverhead")))
          .append("</select></label>")
          .append("</div>");
      html.append("<div class='stacked-row'>")
          .append("<label>Squat AR ")
          .append(
              renderArSelect(
                  "deSquatAr",
                  values.getOrDefault("deSquatAr", dynamicDefaults.squat().ar().name())))
          .append("</label>")
          .append("<label>Deadlift AR ")
          .append(
              renderArSelect(
                  "deDeadliftAr",
                  values.getOrDefault("deDeadliftAr", dynamicDefaults.deadlift().ar().name())))
          .append("</label>")
          .append("<label>Bench AR ")
          .append(
              renderArSelect(
                  "deBenchAr",
                  values.getOrDefault("deBenchAr", dynamicDefaults.bench().ar().name())))
          .append("</label>")
          .append("<label>Overhead AR ")
          .append(
              renderArSelect(
                  "deOverheadAr",
                  values.getOrDefault("deOverheadAr", dynamicDefaults.overhead().ar().name())))
          .append("</label>")
          .append("</div>");
      html.append("</div>");
    } catch (Exception e) {
      html.append("<p class='status error'>Failed to load conjugate planner options: ")
          .append(WebHtml.escapeHtml(e.getMessage()))
          .append("</p>");
    }

    html.append("<div class='stacked-row'><button type='submit'>Generate Wave</button></div>");
    html.append("</form>");
    return html.toString();
  }

  private static String renderLiftOptions(List<Lift> options, String selectedName) {
    StringBuilder html = new StringBuilder();
    for (Lift lift : options) {
      boolean selected = selectedName != null && selectedName.equals(lift.name());
      html.append("<option value='")
          .append(WebHtml.escapeHtml(lift.name()))
          .append("'")
          .append(selected ? " selected" : "")
          .append(">")
          .append(WebHtml.escapeHtml(lift.name()))
          .append("</option>");
    }
    if (html.length() == 0 && selectedName != null && !selectedName.isBlank()) {
      html.append("<option value='")
          .append(WebHtml.escapeHtml(selectedName))
          .append("' selected>")
          .append(WebHtml.escapeHtml(selectedName))
          .append("</option>");
    }
    return html.toString();
  }

  private static String renderArSelect(String name, String selected) {
    String current =
        selected == null || selected.isBlank() ? "STRAIGHT" : selected.toUpperCase(Locale.ROOT);
    StringBuilder html = new StringBuilder("<select name='");
    html.append(WebHtml.escapeHtml(name)).append("'>");
    for (String value : List.of("STRAIGHT", "CHAINS", "BANDS")) {
      html.append("<option value='")
          .append(value)
          .append("'")
          .append(value.equals(current) ? " selected" : "")
          .append(">")
          .append(value)
          .append("</option>");
    }
    html.append("</select>");
    return html.toString();
  }

  static String renderLastWeekContent(
      TrainingDataStore db, List<Lift> lifts, LocalDate start, LocalDate end) {
    LocalDate normalizedStart = start.isAfter(end) ? end : start;
    LocalDate normalizedEnd = end.isBefore(start) ? start : end;
    StringBuilder html = new StringBuilder();
    html.append("<p>Showing ")
        .append(WebHtml.escapeHtml(DATE_FORMAT.format(normalizedStart)))
        .append(" through ")
        .append(WebHtml.escapeHtml(DATE_FORMAT.format(normalizedEnd)))
        .append("</p>");

    Map<LocalDate, List<LastWeekExecutionRow>> rowsByDate = new LinkedHashMap<>();
    Map<String, Lift> liftsByName =
        lifts.stream().collect(Collectors.toMap(Lift::name, lift -> lift, (left, right) -> left));
    com.lifttrax.db.SqliteDb.ExecutionHistorySummary history;
    try {
      history = db.executionHistorySummary(normalizedStart, normalizedEnd);
      for (com.lifttrax.db.SqliteDb.LiftExecutionRow row :
          db.getExecutionsBetween(normalizedStart, normalizedEnd)) {
        Lift lift = liftsByName.getOrDefault(row.lift().name(), row.lift());
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
                    renderLastWeekExecutionItem(lift, execution, normalizedStart, normalizedEnd)));
      }
    } catch (Exception e) {
      return "<p class='status error'>Failed to load last-week view: "
          + WebHtml.escapeHtml(e.getMessage())
          + "</p>";
    }
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

    if (rowsByDate.isEmpty()) {
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
      return html.toString();
    }

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
    return html.toString();
  }

  private static String renderLastWeekExecutionItem(
      Lift lift, LiftExecution execution, LocalDate rangeStart, LocalDate rangeEnd) {
    String notes = execution.notes() == null ? "" : execution.notes();
    String initialSetsJson = setsToEditJson(execution.sets());
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
        + renderSetEditorRows(execution.sets())
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
  static String renderAddExecutionForm(
      List<Lift> lifts, String statusMessage, String statusType, AddExecutionPrefill prefillInput) {
    AddExecutionPrefill prefill = prefillInput == null ? AddExecutionPrefill.empty() : prefillInput;
    StringBuilder options = new StringBuilder("<option value=''>Select a lift</option>");
    StringBuilder muscleOptions = new StringBuilder();
    for (Muscle muscle : Muscle.values()) {
      muscleOptions
          .append("<option value='")
          .append(WebHtml.escapeHtml(muscle.name()))
          .append("'>")
          .append(WebHtml.escapeHtml(muscle.name()))
          .append("</option>");
    }
    for (Lift lift : lifts) {
      boolean selected = lift.name().equals(prefill.lift());
      options
          .append("<option value='")
          .append(WebHtml.escapeHtml(lift.name()))
          .append("' data-filter-option data-name='")
          .append(WebHtml.escapeHtml(lift.name()))
          .append("' data-region='")
          .append(WebHtml.escapeHtml(lift.region().toString()))
          .append("' data-main='")
          .append(WebHtml.escapeHtml(formatMainType(lift)))
          .append("' data-muscles='")
          .append(
              WebHtml.escapeHtml(
                  lift.muscles().stream().map(Muscle::name).collect(Collectors.joining(","))))
          .append("'")
          .append(selected ? " selected" : "")
          .append(">")
          .append(WebHtml.escapeHtml(lift.name()))
          .append("</option>");
    }

    String status = "";
    if (statusMessage != null && !statusMessage.isBlank()) {
      String cssClass = "error".equalsIgnoreCase(statusType) ? "status error" : "status success";
      status =
          "<p class='"
              + cssClass
              + "' role='status' aria-live='polite'>"
              + WebHtml.escapeHtml(statusMessage)
              + "</p>";
    }

    return """
                %s
                <div class='add-actions'>
                  <details class='new-lift-details'>
                    <summary>New Lift</summary>
                    <form method='post' action='/add-lift' class='new-lift-form'>
                      <label>Name <input type='text' name='name' required/></label>
                      <label>Region
                        <select name='region'>
                          <option value='UPPER'>UPPER</option>
                          <option value='LOWER'>LOWER</option>
                        </select>
                      </label>
                      <label>Main
                        <select name='main'>
                          <option value='ACCESSORY'>ACCESSORY</option>
                          <option value='BENCH PRESS'>BENCH PRESS</option>
                          <option value='CONDITIONING'>CONDITIONING</option>
                          <option value='DEADLIFT'>DEADLIFT</option>
                          <option value='MOBILITY'>MOBILITY</option>
                          <option value='OVERHEAD PRESS'>OVERHEAD PRESS</option>
                          <option value='SQUAT'>SQUAT</option>
                        </select>
                      </label>
                      <label>Muscles
                        <select class='js-new-lift-muscles' multiple size='6' title='Hold Ctrl/Cmd to select multiple'>%s</select>
                        <input type='hidden' name='muscles' class='js-new-lift-muscles-hidden' value=''/>
                      </label>
                      <label>Notes
                        <input type='text' name='notes' placeholder='Optional notes'/>
                      </label>
                      <button type='submit'>Create Lift</button>
                    </form>
                  </details>
                </div>
                <form method='post' action='/add-execution' class='add-execution-form'>
                  <label>Lift
                    <select name='lift' data-focus-target='add-lift'>%s</select>
                  </label>
                  <div class='stacked-row'>
                    <button type='submit' formaction='/load-last-execution' formmethod='get' class='secondary compact-btn'>Load Last</button>
                  </div>
                  %s
                  <button type='submit' class='save-execution-btn' data-focus-target='save-execution'>Save Execution</button>
                </form>
                %s
                """
        .formatted(
            status,
            muscleOptions,
            options,
            ExecutionInputWidgetHtml.render(prefill, List.of(), true),
            addExecutionSetLogFallbackScript());
  }

  private static String addExecutionSetLogFallbackScript() {
    return """
                <script>
                  (function () {
                    const form = document.querySelector("form.add-execution-form");
                    if (!form) {
                      return;
                    }

                    const detailedSets = [];
                    try {
                      const hidden = form.querySelector('.js-detailed-sets');
                      const initialSets = JSON.parse((hidden && hidden.value) || '[]');
                      if (Array.isArray(initialSets)) {
                        detailedSets.push(...initialSets);
                      }
                    } catch (error) {
                      detailedSets.splice(0, detailedSets.length);
                    }

                    function checkedValue(name, fallback) {
                      const checked = form.querySelector(`input[name='${name}']:checked`);
                      return checked ? checked.value : fallback;
                    }

                    function fieldValue(name) {
                      return (form.querySelector(`[name='${name}']`) || {}).value || '';
                    }

                    function checkedValues(name) {
                      return Array.from(form.querySelectorAll(`input[name='${name}']:checked`)).map((item) => item.value);
                    }

                    function computeWeight() {
                      const mode = checkedValue('weightMode', 'weight');
                      if (mode === 'none') {
                        return 'none';
                      }
                      if (mode === 'custom') {
                        return fieldValue('customWeight');
                      }
                      if (mode === 'lr') {
                        return `${fieldValue('weightLeft')}${fieldValue('weightUnitLr') || 'lb'}|${fieldValue('weightRight')}${fieldValue('weightUnitLr') || 'lb'}`;
                      }
                      if (mode === 'bands') {
                        return checkedValues('weightBandColors').join('+');
                      }
                      if (mode === 'accom') {
                        const bar = fieldValue('accomBar');
                        const unit = fieldValue('accomUnit') || 'lb';
                        if ((fieldValue('accomMode') || 'chains') === 'bands') {
                          return `${bar} ${unit}+${checkedValues('accomBandColors').join('+')}`;
                        }
                        return `${bar} ${unit}+${fieldValue('accomChain')}c`;
                      }
                      const value = fieldValue('weightValue');
                      const unit = fieldValue('weightUnit') || 'lb';
                      return value ? `${value} ${unit}` : '';
                    }

                    function metricPayload() {
                      const metricType = checkedValue('metricType', 'reps');
                      if (metricType === 'reps-lr') {
                        return {
                          metricType,
                          metricLeft: fieldValue('metricLeft'),
                          metricRight: fieldValue('metricRight')
                        };
                      }
                      return {
                        metricType,
                        metricValue: fieldValue('metricValue')
                      };
                    }

                    function metricLabel(item) {
                      if (item.metricType === 'reps-lr') {
                        return `${item.metricLeft || '?'}L/${item.metricRight || '?'}R reps`;
                      }
                      if (item.metricType === 'time') {
                        return `${item.metricValue || '?'} sec`;
                      }
                      if (item.metricType === 'distance') {
                        return `${item.metricValue || '?'} ft`;
                      }
                      return `${item.metricValue || '?'} reps`;
                    }

                    function updateStatus() {
                      const status = form.querySelector('.js-set-log-status');
                      if (!status) {
                        return;
                      }
                      if (detailedSets.length === 0) {
                        status.textContent = 'No sets in log';
                        return;
                      }
                      status.textContent = detailedSets.length === 1 ? '1 set in log' : `${detailedSets.length} sets in log`;
                    }

                    function renderSetList() {
                      const list = form.querySelector('.js-set-list');
                      const hidden = form.querySelector('.js-detailed-sets');
                      if (!list || !hidden) {
                        return;
                      }
                      list.innerHTML = '';
                      detailedSets.forEach((item, index) => {
                        const li = document.createElement('li');
                        const rpeText = item.rpe ? `, rpe ${item.rpe}` : '';
                        li.textContent = `${metricLabel(item)} @ ${item.weight || 'none'}${rpeText}`;
                        const remove = document.createElement('button');
                        remove.type = 'button';
                        remove.className = 'secondary';
                        remove.textContent = 'Remove';
                        remove.addEventListener('click', (event) => {
                          event.preventDefault();
                          detailedSets.splice(index, 1);
                          renderSetList();
                        });
                        li.appendChild(document.createTextNode(' '));
                        li.appendChild(remove);
                        list.appendChild(li);
                      });
                      hidden.value = JSON.stringify(detailedSets);
                      updateStatus();
                    }

                    function selectIndividualMode() {
                      const input = form.querySelector("input[name='setEntryMode'][value='individual']");
                      const details = form.querySelector('.individual-sets-details');
                      const multipleControls = form.querySelector('.entry-mode-multiple');
                      if (input) {
                        input.checked = true;
                      }
                      if (details) {
                        details.open = true;
                        details.classList.remove('is-hidden');
                      }
                      if (multipleControls) {
                        multipleControls.classList.add('is-hidden');
                      }
                    }

                    function addCurrentSet() {
                      selectIndividualMode();
                      const copies = Math.max(1, parseInt(fieldValue('setCopies') || '1', 10) || 1);
                      const payload = {
                        ...metricPayload(),
                        weight: computeWeight(),
                        rpe: fieldValue('rpe')
                      };
                      for (let i = 0; i < copies; i++) {
                        detailedSets.push({...payload});
                      }
                      renderSetList();
                    }

                    form.addEventListener('click', (event) => {
                      const addButton = event.target.closest('.js-add-set');
                      if (addButton) {
                        event.preventDefault();
                        event.stopImmediatePropagation();
                        addCurrentSet();
                        return;
                      }

                      const clearButton = event.target.closest('.js-clear-sets');
                      if (clearButton) {
                        event.preventDefault();
                        event.stopImmediatePropagation();
                        detailedSets.splice(0, detailedSets.length);
                        renderSetList();
                      }
                    }, true);
                  })();
                </script>
                """;
  }

  static String renderQueryControls(List<Lift> lifts, String selectedLift) {
    StringBuilder options = new StringBuilder("<option value=''>Select a lift</option>");
    for (Lift lift : lifts) {
      boolean selected = lift.name().equals(selectedLift);
      options
          .append("<option value='")
          .append(WebHtml.escapeHtml(lift.name()))
          .append("' data-filter-option data-name='")
          .append(WebHtml.escapeHtml(lift.name()))
          .append("' data-region='")
          .append(WebHtml.escapeHtml(lift.region().toString()))
          .append("' data-main='")
          .append(WebHtml.escapeHtml(formatMainType(lift)))
          .append("' data-muscles='")
          .append(
              WebHtml.escapeHtml(
                  lift.muscles().stream().map(Muscle::name).collect(Collectors.joining(","))))
          .append("'")
          .append(selected ? " selected" : "")
          .append(">")
          .append(WebHtml.escapeHtml(lift.name()))
          .append("</option>");
    }

    return """
                <form method='get' action='/' class='query-form'>
                  <input type='hidden' name='tab' value='query'/>
                  <label>Lift
                    <select name='queryLift'>%s</select>
                  </label>
                  <button type='submit'>Run Query</button>
                </form>
                """
        .formatted(options);
  }

  static String renderQueryContent(TrainingDataStore db, String liftName) {
    if (liftName == null || liftName.isBlank()) {
      return "<p>Select a lift and click <strong>Run Query</strong>.</p>";
    }

    StringBuilder text = new StringBuilder();
    text.append("Last Year\n");
    text.append("========\n");
    try {
      LocalDate oneYearAgo = LocalDate.now().minusDays(365);
      List<LiftExecution> recentExecutions =
          db.getExecutions(liftName).stream()
              .filter(exec -> !exec.date().isBefore(oneYearAgo))
              .toList();

      if (recentExecutions.isEmpty()) {
        text.append("no records\n");
      } else {
        for (LiftExecution execution : recentExecutions) {
          text.append(formatExecution(execution)).append("\n");
        }
      }
    } catch (Exception e) {
      text.append("Failed to load last-year data: ").append(e.getMessage()).append("\n");
    }

    text.append("\nBest by reps\n");
    text.append("============\n");
    try {
      LiftStats stats = db.liftStats(liftName);
      if (stats.bestByReps().isEmpty()) {
        text.append("no records\n");
      } else {
        stats.bestByReps().entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(
                entry ->
                    text.append(entry.getKey())
                        .append(" reps: ")
                        .append(entry.getValue())
                        .append("\n"));
      }
    } catch (UnsupportedOperationException e) {
      text.append("Not available in the Java port yet.\n");
    } catch (Exception e) {
      text.append("Failed to load best-by-reps data: ").append(e.getMessage()).append("\n");
    }

    return "<pre class='query-output'>" + WebHtml.escapeHtml(text.toString()) + "</pre>";
  }

  static String renderFilterControls(List<Lift> lifts, String search) {
    List<String> regions =
        lifts.stream().map(lift -> lift.region().toString()).distinct().sorted().toList();
    List<String> mainTypes =
        lifts.stream().map(WebUiRenderer::formatMainType).distinct().sorted().toList();
    List<String> muscles =
        lifts.stream()
            .flatMap(lift -> lift.muscles().stream())
            .map(Muscle::name)
            .distinct()
            .sorted()
            .toList();

    StringBuilder regionOptions = new StringBuilder("<option value=''>All regions</option>");
    for (String region : regions) {
      regionOptions
          .append("<option value='")
          .append(WebHtml.escapeHtml(region))
          .append("'>")
          .append(WebHtml.escapeHtml(region))
          .append("</option>");
    }

    StringBuilder mainOptions = new StringBuilder("<option value=''>All main types</option>");
    for (String mainType : mainTypes) {
      mainOptions
          .append("<option value='")
          .append(WebHtml.escapeHtml(mainType))
          .append("'>")
          .append(WebHtml.escapeHtml(mainType))
          .append("</option>");
    }

    StringBuilder muscleOptions = new StringBuilder();
    for (String muscle : muscles) {
      muscleOptions
          .append("<option value='")
          .append(WebHtml.escapeHtml(muscle))
          .append("'>")
          .append(WebHtml.escapeHtml(muscle))
          .append("</option>");
    }

    return """
                <div class='tab-filter-bar'>
                  <label>Search <input class='js-filter-name' type='search' value='%s' placeholder='lift name'/></label>
                  <label>Region <select class='js-filter-region'>%s</select></label>
                  <label>Main <select class='js-filter-main'>%s</select></label>
                  <label>Muscle <select class='js-filter-muscle' multiple size='4' title='Hold Ctrl/Cmd to select multiple'>%s</select></label>
                  <button type='button' class='js-clear-filters'>Clear Filters</button>
                </div>
                """
        .formatted(WebHtml.escapeHtml(search), regionOptions, mainOptions, muscleOptions);
  }

  static String renderLiftList(List<Lift> lifts, String search, String label) {
    StringBuilder liftList = new StringBuilder();
    liftList.append("<p>").append(WebHtml.escapeHtml(label)).append("</p>");
    liftList.append("<ul class='lift-list'>");

    for (Lift lift : lifts) {
      if (!search.isEmpty() && !lift.name().toLowerCase(Locale.ROOT).contains(search)) {
        continue;
      }
      liftList
          .append("<li data-filter-item data-name='")
          .append(WebHtml.escapeHtml(lift.name()))
          .append("' data-region='")
          .append(WebHtml.escapeHtml(lift.region().toString()))
          .append("' data-main='")
          .append(WebHtml.escapeHtml(formatMainType(lift)))
          .append("' data-muscles='")
          .append(
              WebHtml.escapeHtml(
                  lift.muscles().stream().map(Muscle::name).collect(Collectors.joining(","))))
          .append("'><a href='/lift?name=")
          .append(urlEncode(lift.name()))
          .append("'>")
          .append(WebHtml.escapeHtml(lift.name()))
          .append("</a> — ")
          .append(WebHtml.escapeHtml(lift.region().toString()))
          .append(" / ")
          .append(WebHtml.escapeHtml(formatMainType(lift)))
          .append("</li>");
    }

    liftList.append("</ul>");
    return liftList.toString();
  }

  static String renderLiftTrendSummary(List<LiftExecution> executions, LocalDate today) {
    LiftTrendSummary trends = LiftTrendSummary.from(executions, today);
    StringBuilder html = new StringBuilder();
    html.append("<section class='lift-trends'>")
        .append("<div class='lift-trends-header'>")
        .append("<h2>Recent Trends</h2>")
        .append("<p class='muted'>Last ")
        .append(LiftTrendSummary.RECENT_WINDOW_DAYS)
        .append(" days, excluding warm-ups and deloads from bests and volume.</p>")
        .append("</div>");

    if (!trends.hasAnyExecutions()) {
      html.append(
              "<p class='muted'>No trend data yet. Log this lift a few times to build recent bests, volume, and frequency.</p>")
          .append("</section>");
      return html.toString();
    }

    if (!trends.hasRecentExecutions()) {
      html.append("<p class='muted'>No executions in the last ")
          .append(LiftTrendSummary.RECENT_WINDOW_DAYS)
          .append(" days. Last trained ")
          .append(WebHtml.escapeHtml(DATE_FORMAT.format(trends.lastExecution().date())))
          .append(".</p>");
    } else if (trends.hasSparseRecentHistory()) {
      html.append("<p class='muted'>Sparse recent history: ")
          .append(trends.recentExecutions())
          .append(" ")
          .append(plural(trends.recentExecutions(), "session", "sessions"))
          .append(" so far. Trends get clearer after three or more sessions.</p>");
    }

    html.append("<div class='lift-trend-grid'>");
    appendTrendCard(
        html,
        "Last trained",
        DATE_FORMAT.format(trends.lastExecution().date()),
        ExecutionSummaryFormatter.formatCompactSummary(trends.lastExecution()));
    appendTrendCard(html, "Recent best set", bestSetValue(trends), bestSetDetail(trends));
    appendTrendCard(
        html,
        "90-day frequency",
        trends.recentExecutions() + " " + plural(trends.recentExecutions(), "session", "sessions"),
        trends.recentTrainingDays()
            + " "
            + plural(trends.recentTrainingDays(), "training day", "training days"));
    appendTrendCard(
        html,
        "90-day volume",
        trends.recentWorkSets() + " " + plural(trends.recentWorkSets(), "work set", "work sets"),
        volumeDetail(trends));
    html.append("</div></section>");
    return html.toString();
  }

  private static void appendTrendCard(
      StringBuilder html, String label, String value, String detail) {
    html.append("<div class='lift-trend-card'>")
        .append("<p class='lift-trend-label'>")
        .append(WebHtml.escapeHtml(label))
        .append("</p>")
        .append("<p class='lift-trend-value'>")
        .append(WebHtml.escapeHtml(value))
        .append("</p>");
    if (detail != null && !detail.isBlank()) {
      html.append("<p class='lift-trend-detail muted'>")
          .append(WebHtml.escapeHtml(detail))
          .append("</p>");
    }
    html.append("</div>");
  }

  private static String bestSetValue(LiftTrendSummary trends) {
    LiftTrendSummary.BestSet bestSet = trends.bestRecentSet();
    if (bestSet == null) {
      return "No weighted rep best yet";
    }
    return DATE_FORMAT.format(bestSet.date())
        + " - "
        + formatTrendMetric(bestSet.metric())
        + " @ "
        + bestSet.weight();
  }

  private static String bestSetDetail(LiftTrendSummary trends) {
    LiftTrendSummary.BestSet bestSet = trends.bestRecentSet();
    if (bestSet == null) {
      return "Add weighted rep sets to unlock recent bests.";
    }
    String rpe =
        bestSet.rpe() == null ? "" : " RPE " + String.format(Locale.ROOT, "%.1f", bestSet.rpe());
    return String.format(Locale.ROOT, "%,d lb comparison%s", bestSet.pounds(), rpe);
  }

  private static String volumeDetail(LiftTrendSummary trends) {
    if (trends.recentWorkSets() == 0) {
      return "No work sets in the recent window.";
    }
    if (trends.recentRepVolume() == 0) {
      return "Timed or distance sets logged without rep volume.";
    }
    String reps = trends.recentRepVolume() + " " + plural(trends.recentRepVolume(), "rep", "reps");
    if (trends.recentTonnageLbs() <= 0) {
      return reps;
    }
    return reps + " / " + String.format(Locale.ROOT, "%,d lb-reps", trends.recentTonnageLbs());
  }

  private static String formatTrendMetric(SetMetric metric) {
    if (metric instanceof SetMetric.Reps reps) {
      return reps.reps() + " " + plural(reps.reps(), "rep", "reps");
    }
    return formatMetric(metric);
  }

  private static String plural(int count, String singular, String plural) {
    return count == 1 ? singular : plural;
  }

  static String renderExecutionList(
      TrainingDataStore db, List<Lift> lifts, String search, String label) {
    StringBuilder html = new StringBuilder();
    html.append("<p>").append(WebHtml.escapeHtml(label)).append("</p>");

    Map<String, Boolean> enabledStatuses;
    try {
      enabledStatuses = db.liftEnabledStatuses();
    } catch (Exception ignored) {
      enabledStatuses = Map.of();
    }

    boolean hasLift = false;
    for (Lift lift : lifts) {
      if (!search.isEmpty() && !lift.name().toLowerCase(Locale.ROOT).contains(search)) {
        continue;
      }
      boolean enabled = enabledStatuses.getOrDefault(lift.name(), true);
      hasLift = true;
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
      html.append("<div class='compact-actions' style='margin:8px 0;'>")
          .append("<a class='compact-btn secondary' href='/lift?name=")
          .append(urlEncode(lift.name()))
          .append("'>Details</a>")
          .append("</div>");
      html.append(
              "<form method='post' action='/set-lift-enabled' class='query-form compact-actions' style='margin:8px 0;'>")
          .append("<input type='hidden' name='lift' value='")
          .append(WebHtml.escapeHtml(lift.name()))
          .append("'/>")
          .append("<input type='hidden' name='enabled' value='")
          .append(enabled ? "0" : "1")
          .append("'/>")
          .append("<input type='hidden' name='tab' value='executions'/>")
          .append("<button type='submit' class='compact-btn'>")
          .append(enabled ? "Disable for wave" : "Enable for wave")
          .append("</button>")
          .append("</form>");
      html.append("<details style='margin:8px 0;'>")
          .append("<summary>Edit lift</summary>")
          .append(
              "<form method='post' action='/update-lift' class='query-form compact-actions' style='margin:8px 0;'>")
          .append("<input type='hidden' name='tab' value='executions'/>")
          .append("<input type='hidden' name='currentName' value='")
          .append(WebHtml.escapeHtml(lift.name()))
          .append("'/>")
          .append("<label>Name <input type='text' name='name' required value='")
          .append(WebHtml.escapeHtml(lift.name()))
          .append("'/></label>")
          .append("<label>Region <select name='region'>")
          .append(renderLiftRegionOptions(lift.region()))
          .append("</select></label>")
          .append("<label>Main <select name='main'>")
          .append(renderLiftMainOptions(lift.main()))
          .append("</select></label>")
          .append("<label>Muscles")
          .append("<select multiple size='6' ")
          .append(
              "onchange=\"this.nextElementSibling.value=Array.from(this.selectedOptions).map(o=>o.value).join(',')\">")
          .append(renderLiftMuscleOptions(lift))
          .append("</select>")
          .append("<input type='hidden' name='muscles' value='")
          .append(
              WebHtml.escapeHtml(
                  lift.muscles().stream().map(Muscle::name).collect(Collectors.joining(","))))
          .append("'/></label>")
          .append("<label>Notes <input type='text' name='notes' value='")
          .append(WebHtml.escapeHtml(lift.notes() == null ? "" : lift.notes()))
          .append("'/></label>")
          .append("<button type='submit' class='compact-btn'>Save lift</button>")
          .append("</form>")
          .append("</details>");
      html.append(
              "<form method='post' action='/delete-lift' class='query-form compact-actions' style='margin:8px 0;' ")
          .append(
              "onsubmit=\"return window.confirm('Delete this lift and all its executions?');\">")
          .append("<input type='hidden' name='tab' value='executions'/>")
          .append("<input type='hidden' name='lift' value='")
          .append(WebHtml.escapeHtml(lift.name()))
          .append("'/>")
          .append("<button type='submit' class='danger compact-btn'>Delete lift</button>")
          .append("</form>");
      html.append("<div class='js-exec-body' data-lift='")
          .append(WebHtml.escapeHtml(lift.name()))
          .append("' data-loaded='false'><p>Expand to load executions...</p></div>");

      html.append("</details>");
    }

    if (!hasLift) {
      html.append("<p>No lifts found for current filters.</p>");
    }
    return html.toString();
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
      List<LiftExecution> executions = db.getExecutions(liftName);
      boolean liftEnabled = true;
      try {
        liftEnabled = db.isLiftEnabled(liftName);
      } catch (Exception ignored) {
      }
      if (executions.isEmpty()) {
        return "<p>No executions recorded.</p>";
      }

      StringBuilder html = new StringBuilder();
      html.append("<p class='status ")
          .append(liftEnabled ? "success" : "error")
          .append("'>Wave status: ")
          .append(liftEnabled ? "Enabled" : "Disabled for wave")
          .append("</p>");
      html.append("<ul class='execution-list'>");
      for (LiftExecution execution : executions) {
        html.append("<li class='execution-item' style='margin:6px 0;'>");
        html.append(
            "<div class='js-exec-view' style='display:flex;align-items:center;gap:8px;flex-wrap:nowrap;'>");
        html.append(
                "<span class='execution-text' style='white-space:nowrap;overflow:hidden;text-overflow:ellipsis;flex:1;'>")
            .append(WebHtml.escapeHtml(formatExecution(execution)))
            .append("</span>");
        if (execution.id() == null) {
          html.append(
              "<span class='status error'>Execution ID missing; cannot edit or delete.</span>");
        } else {
          html.append("<div class='execution-row-actions'>");
          html.append(
              "<button type='button' class='secondary compact-btn js-exec-edit'>Edit</button>");
          html.append(
              "<button type='button' class='secondary danger compact-btn js-exec-delete'>Delete</button>");
          html.append("</div>");
          html.append(
                  "<form method='post' action='/delete-execution' class='query-form execution-delete-form js-exec-delete-form' style='display:none;'>")
              .append("<input type='hidden' name='executionId' value='")
              .append(execution.id())
              .append("'/>")
              .append("<input type='hidden' name='tab' value='executions'/>")
              .append("<input type='hidden' name='liftQuery' value='")
              .append(WebHtml.escapeHtml(liftName))
              .append("'/>")
              .append("</form>");
        }
        html.append("</div>");
        if (execution.id() != null) {
          String initialSetsJson = setsToEditJson(execution.sets());
          html.append(
                  "<form method='post' action='/update-execution' class='query-form execution-edit-form js-exec-form' data-initial-sets='")
              .append(WebHtml.escapeHtml(initialSetsJson))
              .append(
                  "' style='display:none;flex-direction:column;align-items:flex-start;gap:8px;'>")
              .append("<input type='hidden' name='lift' value='")
              .append(WebHtml.escapeHtml(liftName))
              .append("'/>")
              .append("<input type='hidden' name='executionId' value='")
              .append(execution.id())
              .append("'/>")
              .append("<input type='hidden' name='tab' value='executions'/>")
              .append("<input type='hidden' name='liftQuery' value='")
              .append(WebHtml.escapeHtml(liftName))
              .append("'/>")
              .append(
                  "<div class='execution-edit-meta' style='display:flex;align-items:center;gap:8px;flex-wrap:nowrap;overflow-x:auto;'>")
              .append("<label>Date <input type='date' name='date' disabled value='")
              .append(WebHtml.escapeHtml(DATE_FORMAT.format(execution.date())))
              .append("'/></label>")
              .append(
                  "<label>Notes <input type='text' name='notes' style='min-width:220px;' disabled value='")
              .append(WebHtml.escapeHtml(execution.notes() == null ? "" : execution.notes()))
              .append("'/></label>")
              .append("<label><input type='checkbox' name='warmup' disabled")
              .append(execution.warmup() ? " checked" : "")
              .append("/> Warm-up</label>")
              .append("<label><input type='checkbox' name='deload' disabled")
              .append(execution.deload() ? " checked" : "")
              .append("/> Deload</label>")
              .append("</div>")
              .append(
                  "<div class='js-edit-sets' style='display:flex;flex-direction:column;gap:6px;width:100%;'>")
              .append(renderSetEditorRows(execution.sets()))
              .append("</div>")
              .append(
                  "<button type='button' class='secondary compact-btn js-add-set'>Add Set</button>")
              .append(
                  "<input type='hidden' name='detailedSets' class='js-detailed-sets' disabled value=''/>")
              .append("<button type='submit'>Save</button>")
              .append(
                  "<button type='button' class='secondary compact-btn js-exec-cancel'>Cancel</button>")
              .append("</form>");
        }
        html.append("</li>");
      }
      html.append("</ul>");
      return html.toString();
    } catch (Exception e) {
      return "<div class='status error'>"
          + WebHtml.escapeHtml("Failed to load executions: " + e.getMessage())
          + "</div>";
    }
  }

  private static String renderSetEditorRows(List<ExecutionSet> sets) {
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
              "<input type='number' class='js-set-value' disabled placeholder='value' style='width:90px;' value='")
          .append(WebHtml.escapeHtml(formValues.metricValue()))
          .append("'/>")
          .append(
              "<input type='number' class='js-set-left' disabled placeholder='left' style='width:80px;' value='")
          .append(WebHtml.escapeHtml(formValues.metricLeft()))
          .append("'/>")
          .append(
              "<input type='number' class='js-set-right' disabled placeholder='right' style='width:80px;' value='")
          .append(WebHtml.escapeHtml(formValues.metricRight()))
          .append("'/>")
          .append(
              "<input type='text' class='js-set-weight' disabled placeholder='weight' style='width:130px;' value='")
          .append(WebHtml.escapeHtml(formValues.weight()))
          .append("'/>")
          .append(
              "<input type='number' step='0.1' class='js-set-rpe' disabled placeholder='rpe' style='width:80px;' value='")
          .append(WebHtml.escapeHtml(formValues.rpe()))
          .append("'/>")
          .append(
              "<button type='button' class='secondary compact-btn js-remove-set'>Remove</button>")
          .append("</div>");
    }
    return html.toString();
  }

  private static String setsToEditJson(List<ExecutionSet> sets) {
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
              + "\"}";
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
