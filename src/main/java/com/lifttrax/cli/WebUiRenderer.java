package com.lifttrax.cli;

import com.lifttrax.db.SqliteDb;
import com.lifttrax.models.ExecutionSet;
import com.lifttrax.models.ExecutionSummaryFormatter;
import com.lifttrax.models.Lift;
import com.lifttrax.models.LiftExecution;
import com.lifttrax.models.LiftRegion;
import com.lifttrax.models.LiftType;
import com.lifttrax.models.LiftStats;
import com.lifttrax.models.Muscle;
import com.lifttrax.models.SetMetric;
import com.lifttrax.workout.ConjugateWorkoutBuilder;
import com.lifttrax.workout.HypertrophyWorkoutBuilder;
import com.lifttrax.workout.WorkoutBuilder;
import com.lifttrax.workout.MaxEffortLiftPools;
import com.lifttrax.workout.MaxEffortPlan;
import com.lifttrax.workout.WebConfiguredDynamicLiftSource;
import com.lifttrax.workout.WebConfiguredMaxEffortPlanSource;
import com.lifttrax.workout.WaveMarkdownWriter;

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
 * <p>
 * Think of this class as a "template printer": it receives data from the database
 * and turns it into plain HTML strings that the browser can display.
 */
final class WebUiRenderer {
    static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private WebUiRenderer() {
    }

    /**
     * Stores previous form values so we can re-fill the Add Execution form after a submit.
     * This helps users fix mistakes without retyping everything.
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
            String notes
    ) {
        static AddExecutionPrefill empty() {
            return new AddExecutionPrefill("", "", "1", "", "reps", "5", "5", "5", "", false, false, "");
        }
    }

    /**
     * Renders checkbox inputs for supported band colors.
     */
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

    /**
     * Produces the full page body by composing each tab's content section.
     */
    static String renderIndexBody(SqliteDb db, List<Lift> lifts, String search, String queryLift, String activeTab,
                                  String statusMessage, String statusType, AddExecutionPrefill prefill,
                                  LocalDate lastWeekStart, LocalDate lastWeekEnd, int waveWeeks,
                                  Map<String, String> waveInput) {
        String executionContent = renderExecutionList(db, lifts, search, "Recorded lifts:");
        String queryContent = renderQueryContent(db, queryLift);
        String lastWeekContent = renderLastWeekContent(db, lifts, lastWeekStart, lastWeekEnd);
        String waveContent = renderWaveContent(db, waveWeeks, waveInput);
        return renderTabbedLayout(lifts, search, queryLift, activeTab, executionContent, queryContent, lastWeekContent, waveContent,
                statusMessage, statusType, prefill, lastWeekStart, lastWeekEnd, waveWeeks);
    }

    /**
     * Wraps all major UI panels into one tabbed layout.
     * The small JavaScript block here only handles tab switching and client-side filtering.
     */
    static String renderTabbedLayout(List<Lift> lifts, String search, String queryLift, String activeTab,
                                     String executionContent, String queryContent, String lastWeekContent, String waveContent,
                                     String statusMessage, String statusType, AddExecutionPrefill prefill,
                                     LocalDate lastWeekStart, LocalDate lastWeekEnd, int waveWeeks) {
        String filterControls = renderFilterControls(lifts, search);
        String addExecutionContent = renderAddExecutionForm(lifts, statusMessage, statusType, prefill);
        String queryControls = renderQueryControls(lifts, queryLift);

        return """
                <div class='tabbed-ui' data-initial-tab='%s'>
                  <div class='tabs' role='tablist' aria-label='LiftTrax sections'>
                    <button class='tab is-active' role='tab' type='button' data-tab='add-execution' aria-selected='true'>Add Execution</button>
                    <button class='tab' role='tab' type='button' data-tab='waves' aria-selected='false'>Workout Waves</button>
                    <button class='tab' role='tab' type='button' data-tab='executions' aria-selected='false'>Executions</button>
                    <button class='tab' role='tab' type='button' data-tab='query' aria-selected='false'>Query</button>
                    <button class='tab' role='tab' type='button' data-tab='last-week' aria-selected='false'>Last Week</button>
                  </div>
                  <section class='tab-panel is-active' data-panel='add-execution' role='tabpanel'>
                    <h2>Add Execution</h2>
                    %s
                    %s
                  </section>
                  <section class='tab-panel' data-panel='executions' role='tabpanel'>
                    %s
                    %s
                  </section>
                  <section class='tab-panel' data-panel='waves' role='tabpanel'>
                    <h2>Workout Waves</h2>
                    %s
                  </section>
                  <section class='tab-panel' data-panel='query' role='tabpanel'>
                    <h2>Query</h2>
                    %s
                    %s
                    %s
                  </section>
                  <section class='tab-panel' data-panel='last-week' role='tabpanel'>
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

                    const initialTab = shell && shell.dataset.initialTab ? shell.dataset.initialTab : 'add-execution';
                    activateTab(initialTab);

                    tabs.forEach((tab) => {
                      tab.addEventListener('click', () => {
                        activateTab(tab.dataset.tab);
                      });
                    });

                    const FILTER_STORAGE_PREFIX = 'lifttrax.filters.';

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

                      applyPanelFilters(panel);
                      savePanelFilters(panel);
                    });

                    function syncMetricInputs() {
                      const metricType = document.querySelector("input[name='metricType']:checked");
                      const single = document.querySelector('.metric-single');
                      const lr = document.querySelectorAll('.metric-lr');
                      if (!metricType || !single || lr.length === 0) {
                        return;
                      }

                      const isLr = metricType.value === 'reps-lr';
                      single.classList.toggle('is-hidden', isLr);
                      lr.forEach((item) => item.classList.toggle('is-hidden', !isLr));
                    }

                    document.querySelectorAll("input[name='metricType']").forEach((item) => {
                      item.addEventListener('change', syncMetricInputs);
                    });
                    syncMetricInputs();

                    function syncWeightMode() {
                      const selected = document.querySelector("input[name='weightMode']:checked");
                      const mode = selected ? selected.value : 'weight';
                      document.querySelectorAll('.weight-weight, .weight-lr, .weight-bands, .weight-accom, .weight-custom').forEach((group) => {
                        group.classList.add('is-hidden');
                      });
                      const active = document.querySelector(`.weight-${mode}`);
                      if (active) {
                        active.classList.remove('is-hidden');
                      }
                    }

                    function syncAccomMode() {
                      const accomMode = document.querySelector("select[name='accomMode']");
                      if (!accomMode) {
                        return;
                      }
                      const chains = document.querySelector('.accom-chains');
                      const bands = document.querySelector('.accom-bands');
                      const useBands = accomMode.value === 'bands';
                      if (chains) {
                        chains.classList.toggle('is-hidden', useBands);
                      }
                      if (bands) {
                        bands.classList.toggle('is-hidden', !useBands);
                      }
                    }

                    function computeWeight() {
                      const selectedBandValues = (name) => Array.from(document.querySelectorAll(`input[name='${name}']:checked`)).map((item) => item.value);
                      const mode = (document.querySelector("input[name='weightMode']:checked") || {}).value || 'weight';
                      if (mode === 'none') {
                        return 'none';
                      }
                      if (mode === 'custom') {
                        return (document.querySelector("input[name='customWeight']") || {}).value || '';
                      }
                      if (mode === 'lr') {
                        const l = (document.querySelector("input[name='weightLeft']") || {}).value || '';
                        const r = (document.querySelector("input[name='weightRight']") || {}).value || '';
                        const unit = (document.querySelector("select[name='weightUnitLr']") || {}).value || 'lb';
                        return `${l}${unit}|${r}${unit}`;
                      }
                      if (mode === 'bands') {
                        return selectedBandValues('weightBandColors').join('+');
                      }
                      if (mode === 'accom') {
                        const bar = (document.querySelector("input[name='accomBar']") || {}).value || '';
                        const unit = (document.querySelector("select[name='accomUnit']") || {}).value || 'lb';
                        const accomMode = (document.querySelector("select[name='accomMode']") || {}).value || 'chains';
                        if (accomMode === 'bands') {
                          const bands = selectedBandValues('accomBandColors').join('+');
                          return `${bar} ${unit}+${bands}`;
                        }
                        const chain = (document.querySelector("input[name='accomChain']") || {}).value || '';
                        return `${bar} ${unit}+${chain}c`;
                      }
                      const value = (document.querySelector("input[name='weightValue']") || {}).value || '';
                      const unit = (document.querySelector("select[name='weightUnit']") || {}).value || 'lb';
                      const computed = `${value} ${unit}`.trim();
                      if (!computed || computed === 'lb' || computed === 'kg') {
                        return (document.querySelector("input[name='customWeight']") || {}).value || '';
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
                      const type = (document.querySelector("input[name='metricType']:checked") || {}).value || 'reps';
                      const payload = {metricType: type};
                      if (type === 'reps-lr') {
                        payload.metricLeft = (document.querySelector("input[name='metricLeft']") || {}).value || '';
                        payload.metricRight = (document.querySelector("input[name='metricRight']") || {}).value || '';
                      } else {
                        payload.metricValue = (document.querySelector("input[name='metricValue']") || {}).value || '';
                      }
                      return payload;
                    }

                    function renderSetList() {
                      const list = document.querySelector('.js-set-list');
                      const hidden = document.querySelector('.js-detailed-sets');
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
                        });
                        li.appendChild(document.createTextNode(' '));
                        li.appendChild(remove);
                        list.appendChild(li);
                      });
                      hidden.value = JSON.stringify(detailedSets);
                    }

                    document.querySelectorAll("input[name='weightMode']").forEach((item) => {
                      item.addEventListener('change', syncWeightMode);
                    });
                    const accomModeSelect = document.querySelector("select[name='accomMode']");
                    if (accomModeSelect) {
                      accomModeSelect.addEventListener('change', syncAccomMode);
                    }

                    const addSetBtn = document.querySelector('.js-add-set');
                    if (addSetBtn) {
                      addSetBtn.addEventListener('click', () => {
                        const setCopiesInput = document.querySelector("input[name='setCopies']");
                        const copies = Math.max(1, parseInt((setCopiesInput && setCopiesInput.value) || '1', 10) || 1);
                        const payload = {
                          ...metricPayload(),
                          weight: computeWeight(),
                          rpe: (document.querySelector("input[name='rpe']") || {}).value || ''
                        };
                        for (let i = 0; i < copies; i++) {
                          detailedSets.push({...payload});
                        }
                        renderSetList();
                      });
                    }
                    const clearSetsBtn = document.querySelector('.js-clear-sets');
                    if (clearSetsBtn) {
                      clearSetsBtn.addEventListener('click', () => {
                        detailedSets.splice(0, detailedSets.length);
                        renderSetList();
                      });
                    }

                    const addExecutionForm = document.querySelector("form.add-execution-form");
                    if (addExecutionForm) {
                      addExecutionForm.addEventListener('submit', () => {
                        const hiddenWeight = addExecutionForm.querySelector('.js-weight-hidden');
                        if (hiddenWeight) {
                          hiddenWeight.value = computeWeight();
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
                          <a href='#' class='js-remove-set'>Remove</a>
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
                          return;
                        }
                        const remove = event.target.closest('.js-remove-set');
                        if (remove) {
                          event.preventDefault();
                          const row = remove.closest('.js-set-row');
                          if (row) {
                            row.remove();
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
                        setExecutionEditing(item, true);
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
                  })();
                </script>
                """.formatted(
                WebHtml.escapeHtml(activeTab),
                filterControls,
                addExecutionContent,
                filterControls,
                executionContent,
                waveContent,
                filterControls,
                queryControls,
                queryContent,
                WebHtml.escapeHtml(lastWeekStart.toString()),
                WebHtml.escapeHtml(lastWeekEnd.toString()),
                filterControls,
                lastWeekContent
        );
    }

    static String renderWaveContent(SqliteDb db, int weeks, Map<String, String> waveInput) {
        int normalizedWeeks = Math.max(1, weeks);
        String planner = renderWavePlannerForm(db, normalizedWeeks, waveInput);
        boolean shouldGenerate = "true".equalsIgnoreCase(waveInput.getOrDefault("waveGenerate", ""));
        try {
            if (!shouldGenerate) {
                return planner + "<p>Review movements, then click Generate Wave.</p>";
            }

            String waveType = waveInput.getOrDefault("waveType", "conjugate").trim().toLowerCase(Locale.ROOT);
            WorkoutBuilder builder = switch (waveType) {
                case "hypertrophy" -> new HypertrophyWorkoutBuilder();
                default -> new ConjugateWorkoutBuilder(
                        new WebConfiguredMaxEffortPlanSource(normalizedWeeks, waveInput),
                        new WebConfiguredDynamicLiftSource(waveInput)
                );
            };
            var wave = builder.getWave(normalizedWeeks, db);
            List<String> markdown = WaveMarkdownWriter.createMarkdown(wave, db);
            String markdownText = String.join("\n", markdown);
            return planner + """
                    <p>Configured weeks: <strong>%s</strong></p>
                    <div class='stacked-row'>
                      <button type='button' class='secondary' onclick='liftTraxSaveWaveMarkdown()'>Save As Markdown</button>
                    </div>
                    <pre class='query-output'>%s</pre>
                    <script>
                      function liftTraxWaveTimestamp() {
                        const now = new Date();
                        const yyyy = now.getUTCFullYear();
                        const mm = String(now.getUTCMonth() + 1).padStart(2, '0');
                        const dd = String(now.getUTCDate()).padStart(2, '0');
                        const hh = String(now.getUTCHours()).padStart(2, '0');
                        const min = String(now.getUTCMinutes()).padStart(2, '0');
                        const ss = String(now.getUTCSeconds()).padStart(2, '0');
                        return `${yyyy}${mm}${dd}-${hh}${min}${ss}`;
                      }

                      function liftTraxSaveWaveMarkdown() {
                        const content = "%s";
                        const fallback = `wave-${liftTraxWaveTimestamp()}.md`;
                        const input = window.prompt("Save wave as", fallback);
                        if (input === null) {
                          return;
                        }
                        const fileName = input.trim() || fallback;
                        const blob = new Blob([content], { type: 'text/markdown;charset=utf-8' });
                        const url = URL.createObjectURL(blob);
                        const link = document.createElement('a');
                        link.href = url;
                        link.download = fileName;
                        document.body.appendChild(link);
                        link.click();
                        link.remove();
                        URL.revokeObjectURL(url);
                      }
                    </script>
                    """.formatted(
                    normalizedWeeks,
                    WebHtml.escapeHtml(markdownText),
                    jsonEscape(markdownText)
            );
        } catch (Exception e) {
            return planner + """
                    <p>Configured weeks: <strong>%s</strong></p>
                    <p class='status error'>Failed to generate wave: %s</p>
                    """.formatted(normalizedWeeks, WebHtml.escapeHtml(e.getMessage()));
        }
    }

    private static String renderWavePlannerForm(SqliteDb db, int weeks, Map<String, String> values) {
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
                .append("</select></label>");

        if (!"conjugate".equals(waveType)) {
            html.append("<div class='stacked-row'><button type='submit'>Generate Wave</button></div>");
            html.append("</form>");
            return html.toString();
        }

        try {
            List<Lift> squats = db.liftsByType(LiftType.SQUAT);
            List<Lift> deadlifts = db.liftsByType(LiftType.DEADLIFT);
            List<Lift> benches = db.liftsByType(LiftType.BENCH_PRESS);
            List<Lift> overheads = db.liftsByType(LiftType.OVERHEAD_PRESS);

            MaxEffortLiftPools pools = new MaxEffortLiftPools(weeks, db);
            List<Lift> lowerDefaults = pools.lowerWeeks();
            List<Lift> upperDefaults = pools.upperWeeks();
            List<MaxEffortPlan.DeloadLowerLifts> lowerDeloadDefaults = MaxEffortPlan.deriveLowerDeloadFromPlan(lowerDefaults);
            List<MaxEffortPlan.DeloadUpperLifts> upperDeloadDefaults = MaxEffortPlan.deriveUpperDeloadFromPlan(upperDefaults);

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
                html.append("<label>Week ").append(week).append(" Lower <select name='").append(lowerKey).append("'>")
                        .append(renderLiftOptions(lowerOpts, lowerCurrent)).append("</select></label>");
                html.append("<label>Week ").append(week).append(" Upper <select name='").append(upperKey).append("'>")
                        .append(renderLiftOptions(upperOpts, upperCurrent)).append("</select></label>");
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
                html.append("<label>Week ").append(week).append(" Lower Squat <select name='").append(squatKey).append("'>")
                        .append(renderLiftOptions(squats, values.getOrDefault(squatKey, def.squat().name()))).append("</select></label>");
                html.append("<label>Week ").append(week).append(" Lower Deadlift <select name='").append(deadliftKey).append("'>")
                        .append(renderLiftOptions(deadlifts, values.getOrDefault(deadliftKey, def.deadlift().name()))).append("</select></label>");
                html.append("</div>");
            }
            for (int i = 0; i < upperDeloadDefaults.size(); i++) {
                int n = i + 1;
                int week = n * 7;
                MaxEffortPlan.DeloadUpperLifts def = upperDeloadDefaults.get(i);
                String benchKey = "meUpperDeload" + n + "Bench";
                String overheadKey = "meUpperDeload" + n + "Overhead";
                html.append("<div class='stacked-row'>");
                html.append("<label>Week ").append(week).append(" Upper Bench <select name='").append(benchKey).append("'>")
                        .append(renderLiftOptions(benches, values.getOrDefault(benchKey, def.bench().name()))).append("</select></label>");
                html.append("<label>Week ").append(week).append(" Upper Overhead <select name='").append(overheadKey).append("'>")
                        .append(renderLiftOptions(overheads, values.getOrDefault(overheadKey, def.overhead().name()))).append("</select></label>");
                html.append("</div>");
            }

            html.append("<h3>Dynamic Effort Lifts</h3>");
            html.append("<div class='stacked-row'>")
                    .append("<label>Squat <select name='deSquat'>").append(renderLiftOptions(squats, values.get("deSquat"))).append("</select></label>")
                    .append("<label>Deadlift <select name='deDeadlift'>").append(renderLiftOptions(deadlifts, values.get("deDeadlift"))).append("</select></label>")
                    .append("<label>Bench <select name='deBench'>").append(renderLiftOptions(benches, values.get("deBench"))).append("</select></label>")
                    .append("<label>Overhead <select name='deOverhead'>").append(renderLiftOptions(overheads, values.get("deOverhead"))).append("</select></label>")
                    .append("</div>");
            html.append("<div class='stacked-row'>")
                    .append("<label>Squat AR ").append(renderArSelect("deSquatAr", values.get("deSquatAr"))).append("</label>")
                    .append("<label>Deadlift AR ").append(renderArSelect("deDeadliftAr", values.get("deDeadliftAr"))).append("</label>")
                    .append("<label>Bench AR ").append(renderArSelect("deBenchAr", values.get("deBenchAr"))).append("</label>")
                    .append("<label>Overhead AR ").append(renderArSelect("deOverheadAr", values.get("deOverheadAr"))).append("</label>")
                    .append("</div>");
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
            html.append("<option value='").append(WebHtml.escapeHtml(lift.name())).append("'")
                    .append(selected ? " selected" : "")
                    .append(">")
                    .append(WebHtml.escapeHtml(lift.name()))
                    .append("</option>");
        }
        if (html.length() == 0 && selectedName != null && !selectedName.isBlank()) {
            html.append("<option value='").append(WebHtml.escapeHtml(selectedName)).append("' selected>")
                    .append(WebHtml.escapeHtml(selectedName))
                    .append("</option>");
        }
        return html.toString();
    }

    private static String renderArSelect(String name, String selected) {
        String current = selected == null || selected.isBlank() ? "STRAIGHT" : selected.toUpperCase(Locale.ROOT);
        StringBuilder html = new StringBuilder("<select name='" + WebHtml.escapeHtml(name) + "'>");
        for (String value : List.of("STRAIGHT", "CHAINS", "BANDS")) {
            html.append("<option value='").append(value).append("'")
                    .append(value.equals(current) ? " selected" : "")
                    .append(">").append(value).append("</option>");
        }
        html.append("</select>");
        return html.toString();
    }

    static String renderLastWeekContent(SqliteDb db, List<Lift> lifts, LocalDate start, LocalDate end) {
        LocalDate normalizedStart = start.isAfter(end) ? end : start;
        LocalDate normalizedEnd = end.isBefore(start) ? start : end;
        LocalDate historyMin = null;
        LocalDate historyMax = null;
        int historyCount = 0;
        LocalDate nearestBefore = null;
        LocalDate nearestAfter = null;
        StringBuilder html = new StringBuilder();
        html.append("<p>Showing ")
                .append(WebHtml.escapeHtml(DATE_FORMAT.format(normalizedStart)))
                .append(" through ")
                .append(WebHtml.escapeHtml(DATE_FORMAT.format(normalizedEnd)))
                .append("</p>");

        Map<LocalDate, List<LastWeekExecutionRow>> rowsByDate = new LinkedHashMap<>();
        for (Lift lift : lifts) {
            try {
                for (LiftExecution execution : db.getExecutions(lift.name())) {
                    historyCount++;
                    if (historyMin == null || execution.date().isBefore(historyMin)) {
                        historyMin = execution.date();
                    }
                    if (historyMax == null || execution.date().isAfter(historyMax)) {
                        historyMax = execution.date();
                    }
                    if (execution.date().isBefore(normalizedStart)) {
                        if (nearestBefore == null || execution.date().isAfter(nearestBefore)) {
                            nearestBefore = execution.date();
                        }
                        continue;
                    }
                    if (execution.date().isAfter(normalizedEnd)) {
                        if (nearestAfter == null || execution.date().isBefore(nearestAfter)) {
                            nearestAfter = execution.date();
                        }
                        continue;
                    }
                    if (execution.id() == null) {
                        continue;
                    }
                    rowsByDate.computeIfAbsent(execution.date(), ignored -> new ArrayList<>())
                            .add(new LastWeekExecutionRow(
                                    lift.name(),
                                    executionSortOrder(lift, execution),
                                    renderLastWeekExecutionItem(lift, execution, normalizedStart, normalizedEnd)
                            ));
                }
            } catch (Exception e) {
                return "<p class='status error'>Failed to load last-week view: " + WebHtml.escapeHtml(e.getMessage()) + "</p>";
            }
        }
        if (historyMin != null && historyMax != null) {
            html.append("<p class='muted'>History available: ")
                    .append(historyCount)
                    .append(" total records from ")
                    .append(WebHtml.escapeHtml(DATE_FORMAT.format(historyMin)))
                    .append(" through ")
                    .append(WebHtml.escapeHtml(DATE_FORMAT.format(historyMax)))
                    .append("</p>");
        } else {
            html.append("<p class='muted'>History available: no execution records found</p>");
        }

        if (rowsByDate.isEmpty()) {
            html.append("<p>no executions in this range (check the date range and filters)</p>");
            if (nearestBefore != null) {
                html.append("<p class='muted'>Closest earlier record in current filters: ")
                        .append(WebHtml.escapeHtml(DATE_FORMAT.format(nearestBefore)))
                        .append("</p>");
            }
            if (nearestAfter != null) {
                html.append("<p class='muted'>Closest later record in current filters: ")
                        .append(WebHtml.escapeHtml(DATE_FORMAT.format(nearestAfter)))
                        .append("</p>");
            }
            return html.toString();
        }

        rowsByDate.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    html.append("<section class='last-week-day'>")
                            .append("<h4>")
                            .append(WebHtml.escapeHtml(DATE_FORMAT.format(entry.getKey())))
                            .append("</h4>")
                            .append("<ul class='execution-list'>");
                    entry.getValue().stream()
                            .sorted(Comparator.comparingInt(LastWeekExecutionRow::sortOrder)
                                    .thenComparing(LastWeekExecutionRow::liftName)
                                    .thenComparing(LastWeekExecutionRow::html))
                            .forEach(row -> html.append(row.html()));
                    html.append("</ul></section>");
                });
        return html.toString();
    }

    private static String renderLastWeekExecutionItem(Lift lift, LiftExecution execution, LocalDate rangeStart, LocalDate rangeEnd) {
        String notes = execution.notes() == null ? "" : execution.notes();
        String initialSetsJson = setsToEditJson(execution.sets());
        return "<li class='execution-item' style='margin:6px 0;' data-filter-item data-name='" + WebHtml.escapeHtml(lift.name())
                + "' data-region='" + WebHtml.escapeHtml(lift.region().toString())
                + "' data-main='" + WebHtml.escapeHtml(formatMainType(lift))
                + "' data-muscles='" + WebHtml.escapeHtml(lift.muscles().stream().map(Muscle::name).collect(Collectors.joining(",")))
                + "'>"
                + "<div class='js-exec-view' style='display:flex;align-items:center;gap:8px;flex-wrap:nowrap;'>"
                + "<span class='execution-text' style='white-space:nowrap;overflow:hidden;text-overflow:ellipsis;flex:1;'>"
                + WebHtml.escapeHtml(lift.name() + " — " + formatExecutionSummary(execution))
                + "</span>"
                + "<a href='#' class='js-exec-edit'>Edit</a>"
                + "<a href='#' class='danger js-exec-delete'>Delete</a>"
                + "<form method='post' action='/delete-execution' class='query-form execution-delete-form js-exec-delete-form' style='display:none;'>"
                + "<input type='hidden' name='executionId' value='" + execution.id() + "'/>"
                + "<input type='hidden' name='tab' value='last-week'/>"
                + "<input type='hidden' name='lastWeekStart' value='" + WebHtml.escapeHtml(DATE_FORMAT.format(rangeStart)) + "'/>"
                + "<input type='hidden' name='lastWeekEnd' value='" + WebHtml.escapeHtml(DATE_FORMAT.format(rangeEnd)) + "'/>"
                + "</form>"
                + "</div>"
                + "<form method='post' action='/update-execution' class='query-form execution-edit-form js-exec-form' data-initial-sets='"
                + WebHtml.escapeHtml(initialSetsJson)
                + "' style='display:none;flex-direction:column;align-items:flex-start;gap:8px;'>"
                + "<input type='hidden' name='lift' value='" + WebHtml.escapeHtml(lift.name()) + "'/>"
                + "<input type='hidden' name='executionId' value='" + execution.id() + "'/>"
                + "<input type='hidden' name='tab' value='last-week'/>"
                + "<input type='hidden' name='lastWeekStart' value='" + WebHtml.escapeHtml(DATE_FORMAT.format(rangeStart)) + "'/>"
                + "<input type='hidden' name='lastWeekEnd' value='" + WebHtml.escapeHtml(DATE_FORMAT.format(rangeEnd)) + "'/>"
                + "<div style='display:flex;align-items:center;gap:8px;flex-wrap:nowrap;overflow-x:auto;'>"
                + "<label>Date <input type='date' name='date' disabled value='" + WebHtml.escapeHtml(DATE_FORMAT.format(execution.date())) + "'/></label>"
                + "<label>Notes <input type='text' name='notes' style='min-width:220px;' disabled value='" + WebHtml.escapeHtml(notes) + "'/></label>"
                + "<label><input type='checkbox' name='warmup' disabled" + (execution.warmup() ? " checked" : "") + "/> Warm-up</label>"
                + "<label><input type='checkbox' name='deload' disabled" + (execution.deload() ? " checked" : "") + "/> Deload</label>"
                + "</div>"
                + "<div class='js-edit-sets' style='display:flex;flex-direction:column;gap:6px;width:100%;'>"
                + renderSetEditorRows(execution.sets())
                + "</div>"
                + "<a href='#' class='js-add-set'>Add Set</a>"
                + "<input type='hidden' name='detailedSets' class='js-detailed-sets' disabled value=''/>"
                + "<button type='submit'>Save</button>"
                + "<a href='#' class='js-exec-cancel'>Cancel</a>"
                + "</form>"
                + "</li>";
    }

    private static int executionSortOrder(Lift lift, LiftExecution execution) {
        if (execution.warmup()) {
            return 0;
        }
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

    private record LastWeekExecutionRow(String liftName, int sortOrder, String html) {
    }

    /**
     * Builds the Add Execution form, including weight mode controls and metric controls.
     * Uses {@link WeightInputParser} so rendering code does not need to parse raw weight text.
     */
    static String renderAddExecutionForm(List<Lift> lifts, String statusMessage, String statusType, AddExecutionPrefill prefillInput) {
        AddExecutionPrefill prefill = prefillInput == null ? AddExecutionPrefill.empty() : prefillInput;
        StringBuilder options = new StringBuilder("<option value=''>Select a lift</option>");
        StringBuilder muscleOptions = new StringBuilder();
        for (Muscle muscle : Muscle.values()) {
            muscleOptions.append("<option value='")
                    .append(WebHtml.escapeHtml(muscle.name()))
                    .append("'>")
                    .append(WebHtml.escapeHtml(muscle.name()))
                    .append("</option>");
        }
        for (Lift lift : lifts) {
            boolean selected = lift.name().equals(prefill.lift());
            options.append("<option value='")
                    .append(WebHtml.escapeHtml(lift.name()))
                    .append("' data-filter-option data-name='")
                    .append(WebHtml.escapeHtml(lift.name()))
                    .append("' data-region='")
                    .append(WebHtml.escapeHtml(lift.region().toString()))
                    .append("' data-main='")
                    .append(WebHtml.escapeHtml(formatMainType(lift)))
                    .append("' data-muscles='")
                    .append(WebHtml.escapeHtml(lift.muscles().stream().map(Muscle::name).collect(Collectors.joining(","))))
                    .append("'")
                    .append(selected ? " selected" : "")
                    .append(">")
                    .append(WebHtml.escapeHtml(lift.name()))
                    .append("</option>");
        }

        String status = "";
        if (statusMessage != null && !statusMessage.isBlank()) {
            String cssClass = "error".equalsIgnoreCase(statusType) ? "status error" : "status success";
            status = "<p class='" + cssClass + "'>" + WebHtml.escapeHtml(statusMessage) + "</p>";
        }

        WeightInputParser.WeightPrefill weightPrefill = WeightInputParser.parseWeightPrefill(prefill.weight());

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
                    <select name='lift'>%s</select>
                  </label>
                  <div class='stacked-row'>
                    <button type='submit' formaction='/load-last-execution' formmethod='get' class='secondary compact-btn'>Load Last</button>
                  </div>
                  <input type='hidden' name='weight' class='js-weight-hidden' value='%s'/>
                  <input type='hidden' name='detailedSets' class='js-detailed-sets' value='[]'/>
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
                      <label>Weight <input type='number' step='0.5' min='0' name='weightValue' value='%s' placeholder='225'/></label>
                      <label>Unit
                        <select name='weightUnit'><option value='lb'%s>lb</option><option value='kg'%s>kg</option></select>
                      </label>
                    </div>
                    <div class='stacked-row weight-lr is-hidden'>
                      <label>Left <input type='number' step='0.5' min='0' name='weightLeft' value='%s' placeholder='40'/></label>
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
                      <label>Bar <input type='number' step='0.5' min='0' name='accomBar' value='%s' placeholder='225'/></label>
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
                      <label>Custom <input type='text' name='customWeight' value='%s' placeholder='225 lb+40c'/></label>
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
                    <div class='stacked-row'>
                      <label>Copies <input type='number' min='1' name='setCopies' value='1'/></label>
                      <button type='button' class='secondary js-add-set'>Add Individual Set</button>
                      <button type='button' class='secondary js-clear-sets'>Clear Individual Sets</button>
                    </div>
                    <ul class='set-list js-set-list'></ul>
                  </fieldset>
                  <div class='stacked-row'>
                    <label>Date <input type='date' name='date' value='%s'/></label>
                    <label><input type='checkbox' name='warmup' %s/> Warm-up</label>
                    <label><input type='checkbox' name='deload' %s/> Deload</label>
                  </div>
                  <label>Notes
                    <input type='text' name='notes' value='%s' placeholder='Optional notes'/>
                  </label>
                  <button type='submit'>Save Execution</button>
                </form>
                """.formatted(
                status,
                muscleOptions,
                options,
                WebHtml.escapeHtml(prefill.weight()),
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
                WebHtml.escapeHtml(prefill.date()),
                prefill.warmup() ? "checked" : "",
                prefill.deload() ? "checked" : "",
                WebHtml.escapeHtml(prefill.notes())
        );
    }

    static String renderQueryControls(List<Lift> lifts, String selectedLift) {
        StringBuilder options = new StringBuilder("<option value=''>Select a lift</option>");
        for (Lift lift : lifts) {
            boolean selected = lift.name().equals(selectedLift);
            options.append("<option value='")
                    .append(WebHtml.escapeHtml(lift.name()))
                    .append("' data-filter-option data-name='")
                    .append(WebHtml.escapeHtml(lift.name()))
                    .append("' data-region='")
                    .append(WebHtml.escapeHtml(lift.region().toString()))
                    .append("' data-main='")
                    .append(WebHtml.escapeHtml(formatMainType(lift)))
                    .append("' data-muscles='")
                    .append(WebHtml.escapeHtml(lift.muscles().stream().map(Muscle::name).collect(Collectors.joining(","))))
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
                """.formatted(options);
    }

    static String renderQueryContent(SqliteDb db, String liftName) {
        if (liftName == null || liftName.isBlank()) {
            return "<p>Select a lift and click <strong>Run Query</strong>.</p>";
        }

        StringBuilder text = new StringBuilder();
        text.append("Last Year\n");
        text.append("========\n");
        try {
            LocalDate oneYearAgo = LocalDate.now().minusDays(365);
            List<LiftExecution> recentExecutions = db.getExecutions(liftName).stream()
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
                        .forEach(entry -> text
                                .append(entry.getKey())
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
        List<String> regions = lifts.stream()
                .map(lift -> lift.region().toString())
                .distinct()
                .sorted()
                .toList();
        List<String> mainTypes = lifts.stream()
                .map(WebUiRenderer::formatMainType)
                .distinct()
                .sorted()
                .toList();
        List<String> muscles = lifts.stream()
                .flatMap(lift -> lift.muscles().stream())
                .map(Muscle::name)
                .distinct()
                .sorted()
                .toList();

        StringBuilder regionOptions = new StringBuilder("<option value=''>All regions</option>");
        for (String region : regions) {
            regionOptions.append("<option value='")
                    .append(WebHtml.escapeHtml(region))
                    .append("'>")
                    .append(WebHtml.escapeHtml(region))
                    .append("</option>");
        }

        StringBuilder mainOptions = new StringBuilder("<option value=''>All main types</option>");
        for (String mainType : mainTypes) {
            mainOptions.append("<option value='")
                    .append(WebHtml.escapeHtml(mainType))
                    .append("'>")
                    .append(WebHtml.escapeHtml(mainType))
                    .append("</option>");
        }

        StringBuilder muscleOptions = new StringBuilder();
        for (String muscle : muscles) {
            muscleOptions.append("<option value='")
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
                """.formatted(WebHtml.escapeHtml(search), regionOptions, mainOptions, muscleOptions);
    }

    static String renderLiftList(List<Lift> lifts, String search, String label) {
        StringBuilder liftList = new StringBuilder();
        liftList.append("<p>").append(WebHtml.escapeHtml(label)).append("</p>");
        liftList.append("<ul class='lift-list'>");

        for (Lift lift : lifts) {
            if (!search.isEmpty() && !lift.name().toLowerCase(Locale.ROOT).contains(search)) {
                continue;
            }
            liftList.append("<li data-filter-item data-name='")
                    .append(WebHtml.escapeHtml(lift.name()))
                    .append("' data-region='")
                    .append(WebHtml.escapeHtml(lift.region().toString()))
                    .append("' data-main='")
                    .append(WebHtml.escapeHtml(formatMainType(lift)))
                    .append("' data-muscles='")
                    .append(WebHtml.escapeHtml(lift.muscles().stream().map(Muscle::name).collect(Collectors.joining(","))))
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

    static String renderExecutionList(SqliteDb db, List<Lift> lifts, String search, String label) {
        StringBuilder html = new StringBuilder();
        html.append("<p>").append(WebHtml.escapeHtml(label)).append("</p>");

        boolean hasLift = false;
        for (Lift lift : lifts) {
            if (!search.isEmpty() && !lift.name().toLowerCase(Locale.ROOT).contains(search)) {
                continue;
            }
            boolean enabled = true;
            try {
                enabled = db.isLiftEnabled(lift.name());
            } catch (Exception ignored) {
            }
            hasLift = true;
            html.append("<details class='execution-lift-group' data-filter-item data-name='")
                    .append(WebHtml.escapeHtml(lift.name()))
                    .append("' data-region='")
                    .append(WebHtml.escapeHtml(lift.region().toString()))
                    .append("' data-main='")
                    .append(WebHtml.escapeHtml(formatMainType(lift)))
                    .append("' data-muscles='")
                    .append(WebHtml.escapeHtml(lift.muscles().stream().map(Muscle::name).collect(Collectors.joining(","))))
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
            html.append("<form method='post' action='/set-lift-enabled' class='query-form compact-actions' style='margin:8px 0;'>")
                    .append("<input type='hidden' name='lift' value='").append(WebHtml.escapeHtml(lift.name())).append("'/>")
                    .append("<input type='hidden' name='enabled' value='").append(enabled ? "0" : "1").append("'/>")
                    .append("<input type='hidden' name='tab' value='executions'/>")
                    .append("<button type='submit' class='compact-btn'>").append(enabled ? "Disable for wave" : "Enable for wave").append("</button>")
                    .append("</form>");
            html.append("<details style='margin:8px 0;'>")
                    .append("<summary>Edit lift</summary>")
                    .append("<form method='post' action='/update-lift' class='query-form compact-actions' style='margin:8px 0;'>")
                    .append("<input type='hidden' name='tab' value='executions'/>")
                    .append("<input type='hidden' name='currentName' value='").append(WebHtml.escapeHtml(lift.name())).append("'/>")
                    .append("<label>Name <input type='text' name='name' required value='").append(WebHtml.escapeHtml(lift.name())).append("'/></label>")
                    .append("<label>Region <select name='region'>").append(renderLiftRegionOptions(lift.region())).append("</select></label>")
                    .append("<label>Main <select name='main'>").append(renderLiftMainOptions(lift.main())).append("</select></label>")
                    .append("<label>Muscles")
                    .append("<select multiple size='6' ")
                    .append("onchange=\"this.nextElementSibling.value=Array.from(this.selectedOptions).map(o=>o.value).join(',')\">")
                    .append(renderLiftMuscleOptions(lift))
                    .append("</select>")
                    .append("<input type='hidden' name='muscles' value='")
                    .append(WebHtml.escapeHtml(lift.muscles().stream().map(Muscle::name).collect(Collectors.joining(","))))
                    .append("'/></label>")
                    .append("<label>Notes <input type='text' name='notes' value='")
                    .append(WebHtml.escapeHtml(lift.notes() == null ? "" : lift.notes()))
                    .append("'/></label>")
                    .append("<button type='submit' class='compact-btn'>Save lift</button>")
                    .append("</form>")
                    .append("</details>");
            html.append("<form method='post' action='/delete-lift' class='query-form compact-actions' style='margin:8px 0;' ")
                    .append("onsubmit=\"return window.confirm('Delete this lift and all its executions?');\">")
                    .append("<input type='hidden' name='tab' value='executions'/>")
                    .append("<input type='hidden' name='lift' value='").append(WebHtml.escapeHtml(lift.name())).append("'/>")
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

    static String renderExecutionRows(SqliteDb db, String liftName) {
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
                html.append("<div class='js-exec-view' style='display:flex;align-items:center;gap:8px;flex-wrap:nowrap;'>");
                html.append("<span class='execution-text' style='white-space:nowrap;overflow:hidden;text-overflow:ellipsis;flex:1;'>")
                        .append(WebHtml.escapeHtml(formatExecution(execution)))
                        .append("</span>");
                if (execution.id() == null) {
                    html.append("<span class='status error'>Execution ID missing; cannot edit or delete.</span>");
                } else {
                    html.append("<a href='#' class='js-exec-edit'>Edit</a>");
                    html.append("<a href='#' class='danger js-exec-delete'>Delete</a>");
                    html.append("<form method='post' action='/delete-execution' class='query-form execution-delete-form js-exec-delete-form' style='display:none;'>")
                            .append("<input type='hidden' name='executionId' value='").append(execution.id()).append("'/>")
                            .append("<input type='hidden' name='tab' value='executions'/>")
                            .append("<input type='hidden' name='liftQuery' value='").append(WebHtml.escapeHtml(liftName)).append("'/>")
                            .append("</form>");
                }
                html.append("</div>");
                if (execution.id() != null) {
                    String initialSetsJson = setsToEditJson(execution.sets());
                    html.append("<form method='post' action='/update-execution' class='query-form execution-edit-form js-exec-form' data-initial-sets='")
                            .append(WebHtml.escapeHtml(initialSetsJson))
                            .append("' style='display:none;flex-direction:column;align-items:flex-start;gap:8px;'>")
                            .append("<input type='hidden' name='lift' value='").append(WebHtml.escapeHtml(liftName)).append("'/>")
                            .append("<input type='hidden' name='executionId' value='").append(execution.id()).append("'/>")
                            .append("<input type='hidden' name='tab' value='executions'/>")
                            .append("<input type='hidden' name='liftQuery' value='").append(WebHtml.escapeHtml(liftName)).append("'/>")
                            .append("<div style='display:flex;align-items:center;gap:8px;flex-wrap:nowrap;overflow-x:auto;'>")
                            .append("<label>Date <input type='date' name='date' disabled value='").append(WebHtml.escapeHtml(DATE_FORMAT.format(execution.date()))).append("'/></label>")
                            .append("<label>Notes <input type='text' name='notes' style='min-width:220px;' disabled value='").append(WebHtml.escapeHtml(execution.notes() == null ? "" : execution.notes())).append("'/></label>")
                            .append("<label><input type='checkbox' name='warmup' disabled").append(execution.warmup() ? " checked" : "").append("/> Warm-up</label>")
                            .append("<label><input type='checkbox' name='deload' disabled").append(execution.deload() ? " checked" : "").append("/> Deload</label>")
                            .append("</div>")
                            .append("<div class='js-edit-sets' style='display:flex;flex-direction:column;gap:6px;width:100%;'>")
                            .append(renderSetEditorRows(execution.sets()))
                            .append("</div>")
                            .append("<a href='#' class='js-add-set'>Add Set</a>")
                            .append("<input type='hidden' name='detailedSets' class='js-detailed-sets' disabled value=''/>")
                            .append("<button type='submit'>Save</button>")
                            .append("<a href='#' class='js-exec-cancel'>Cancel</a>")
                            .append("</form>");
                }
                html.append("</li>");
            }
            html.append("</ul>");
            return html.toString();
        } catch (Exception e) {
            return "<div class='status error'>" + WebHtml.escapeHtml("Failed to load executions: " + e.getMessage()) + "</div>";
        }
    }

    private static String renderSetEditorRows(List<ExecutionSet> sets) {
        StringBuilder html = new StringBuilder();
        for (ExecutionSet set : sets) {
            String metricType = "reps";
            String metricValue = "";
            String metricLeft = "";
            String metricRight = "";
            if (set.metric() instanceof SetMetric.Reps reps) {
                metricType = "reps";
                metricValue = String.valueOf(reps.reps());
            } else if (set.metric() instanceof SetMetric.RepsLr repsLr) {
                metricType = "reps-lr";
                metricLeft = String.valueOf(repsLr.left());
                metricRight = String.valueOf(repsLr.right());
            } else if (set.metric() instanceof SetMetric.TimeSecs timeSecs) {
                metricType = "time";
                metricValue = String.valueOf(timeSecs.seconds());
            } else if (set.metric() instanceof SetMetric.DistanceFeet distanceFeet) {
                metricType = "distance";
                metricValue = String.valueOf(distanceFeet.feet());
            }

            html.append("<div class='js-set-row' style='display:flex;align-items:center;gap:8px;flex-wrap:nowrap;overflow-x:auto;'>")
                    .append("<select class='js-set-metric' disabled>")
                    .append("<option value='reps'").append("reps".equals(metricType) ? " selected" : "").append(">reps</option>")
                    .append("<option value='reps-lr'").append("reps-lr".equals(metricType) ? " selected" : "").append(">reps-lr</option>")
                    .append("<option value='time'").append("time".equals(metricType) ? " selected" : "").append(">time</option>")
                    .append("<option value='distance'").append("distance".equals(metricType) ? " selected" : "").append(">distance</option>")
                    .append("</select>")
                    .append("<input type='number' class='js-set-value' disabled placeholder='value' style='width:90px;' value='").append(WebHtml.escapeHtml(metricValue)).append("'/>")
                    .append("<input type='number' class='js-set-left' disabled placeholder='left' style='width:80px;' value='").append(WebHtml.escapeHtml(metricLeft)).append("'/>")
                    .append("<input type='number' class='js-set-right' disabled placeholder='right' style='width:80px;' value='").append(WebHtml.escapeHtml(metricRight)).append("'/>")
                    .append("<input type='text' class='js-set-weight' disabled placeholder='weight' style='width:130px;' value='").append(WebHtml.escapeHtml(set.weight() == null ? "" : set.weight())).append("'/>")
                    .append("<input type='number' step='0.1' class='js-set-rpe' disabled placeholder='rpe' style='width:80px;' value='").append(WebHtml.escapeHtml(set.rpe() == null ? "" : String.format(Locale.ROOT, "%s", set.rpe()))).append("'/>")
                    .append("<a href='#' class='js-remove-set'>Remove</a>")
                    .append("</div>");
        }
        return html.toString();
    }

    private static String setsToEditJson(List<ExecutionSet> sets) {
        List<String> items = new ArrayList<>();
        for (ExecutionSet set : sets) {
            String metricType = "reps";
            String metricValue = "";
            String metricLeft = "";
            String metricRight = "";
            if (set.metric() instanceof SetMetric.Reps reps) {
                metricType = "reps";
                metricValue = String.valueOf(reps.reps());
            } else if (set.metric() instanceof SetMetric.RepsLr repsLr) {
                metricType = "reps-lr";
                metricLeft = String.valueOf(repsLr.left());
                metricRight = String.valueOf(repsLr.right());
            } else if (set.metric() instanceof SetMetric.TimeSecs timeSecs) {
                metricType = "time";
                metricValue = String.valueOf(timeSecs.seconds());
            } else if (set.metric() instanceof SetMetric.DistanceFeet distanceFeet) {
                metricType = "distance";
                metricValue = String.valueOf(distanceFeet.feet());
            }

            String item = "{\"metricType\":\"" + jsonEscape(metricType) + "\","
                    + "\"metricValue\":\"" + jsonEscape(metricValue) + "\","
                    + "\"metricLeft\":\"" + jsonEscape(metricLeft) + "\","
                    + "\"metricRight\":\"" + jsonEscape(metricRight) + "\","
                    + "\"weight\":\"" + jsonEscape(set.weight() == null ? "" : set.weight()) + "\","
                    + "\"rpe\":\"" + jsonEscape(set.rpe() == null ? "" : String.format(Locale.ROOT, "%s", set.rpe())) + "\"}";
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
        return DATE_FORMAT.format(execution.date()) + " — " + ExecutionSummaryFormatter.formatCompactSummary(execution);
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
            boolean hasWeight = weight != null
                    && !weight.isBlank()
                    && !"none".equalsIgnoreCase(weight.trim());
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
