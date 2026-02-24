package com.lifttrax.cli;

import com.lifttrax.db.SqliteDb;
import com.lifttrax.models.ExecutionSet;
import com.lifttrax.models.Lift;
import com.lifttrax.models.LiftExecution;
import com.lifttrax.models.LiftStats;
import com.lifttrax.models.Muscle;
import com.lifttrax.models.SetMetric;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

final class WebUiRenderer {
    static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private WebUiRenderer() {
    }

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

    static String renderIndexBody(SqliteDb db, List<Lift> lifts, String search, String queryLift, String activeTab, String statusMessage, String statusType, AddExecutionPrefill prefill) {
        return renderTabbedLayout(lifts, search, queryLift, activeTab, renderQueryContent(db, queryLift), statusMessage, statusType, prefill);
    }

    static String renderTabbedLayout(List<Lift> lifts, String search, String queryLift, String activeTab, String queryContent, String statusMessage, String statusType, AddExecutionPrefill prefill) {
        String filterControls = renderFilterControls(lifts, search);
        String addExecutionContent = renderAddExecutionForm(lifts, statusMessage, statusType, prefill);
        String executionContent = renderLiftList(lifts, search, "Recorded lifts:");
        String queryControls = renderQueryControls(lifts, queryLift);
        String lastWeekContent = renderLiftList(lifts, search, "Filter lifts for last-week view:");

        return """
                <div class='tabbed-ui' data-initial-tab='%s'>
                  <div class='tabs' role='tablist' aria-label='LiftTrax sections'>
                    <button class='tab is-active' role='tab' type='button' data-tab='add-execution' aria-selected='true'>Add Execution</button>
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
                  <section class='tab-panel' data-panel='query' role='tabpanel'>
                    <h2>Query</h2>
                    %s
                    %s
                    %s
                  </section>
                  <section class='tab-panel' data-panel='last-week' role='tabpanel'>
                    <h2>Last Week</h2>
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
                    }

                    document.querySelectorAll('.tab-panel').forEach((panel) => {
                      panel.querySelectorAll('.js-filter-name, .js-filter-region, .js-filter-main, .js-filter-muscle').forEach((control) => {
                        control.addEventListener('input', () => applyPanelFilters(panel));
                        control.addEventListener('change', () => applyPanelFilters(panel));
                      });
                      const clearButton = panel.querySelector('.js-clear-filters');
                      if (clearButton) {
                        clearButton.addEventListener('click', () => clearPanelFilters(panel));
                      }

                      applyPanelFilters(panel);
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
                        return (document.querySelector("input[name='weightBands']") || {}).value || '';
                      }
                      if (mode === 'accom') {
                        const bar = (document.querySelector("input[name='accomBar']") || {}).value || '';
                        const unit = (document.querySelector("select[name='accomUnit']") || {}).value || 'lb';
                        const accomMode = (document.querySelector("select[name='accomMode']") || {}).value || 'chains';
                        if (accomMode === 'bands') {
                          const bands = (document.querySelector("input[name='accomBands']") || {}).value || '';
                          return `${bar} ${unit}+${bands}`;
                        }
                        const chain = (document.querySelector("input[name='accomChain']") || {}).value || '';
                        return `${bar} ${unit}+${chain}c`;
                      }
                      const value = (document.querySelector("input[name='weightValue']") || {}).value || '';
                      const unit = (document.querySelector("select[name='weightUnit']") || {}).value || 'lb';
                      return `${value} ${unit}`.trim();
                    }

                    const detailedSets = [];
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
                        li.textContent = `${item.metricType} @ ${item.weight}`;
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
                        detailedSets.push({
                          ...metricPayload(),
                          weight: computeWeight(),
                          rpe: (document.querySelector("input[name='rpe']") || {}).value || ''
                        });
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
                filterControls,
                queryControls,
                queryContent,
                filterControls,
                lastWeekContent
        );
    }

    static String renderAddExecutionForm(List<Lift> lifts, String statusMessage, String statusType, AddExecutionPrefill prefillInput) {
        AddExecutionPrefill prefill = prefillInput == null ? AddExecutionPrefill.empty() : prefillInput;
        StringBuilder options = new StringBuilder("<option value=''>Select a lift</option>");
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

        String repsChecked = "reps".equals(prefill.metricType()) ? "checked" : "";
        String repsLrChecked = "reps-lr".equals(prefill.metricType()) ? "checked" : "";
        String timeChecked = "time".equals(prefill.metricType()) ? "checked" : "";
        String distanceChecked = "distance".equals(prefill.metricType()) ? "checked" : "";

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
                        <input type='text' name='muscles' placeholder='QUAD,GLUTE'/>
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
                    <button type='submit' formaction='/load-last-execution' formmethod='get' class='secondary'>Load Last</button>
                  </div>
                  <input type='hidden' name='weight' class='js-weight-hidden' value='%s'/>
                  <input type='hidden' name='detailedSets' class='js-detailed-sets' value='[]'/>
                  <fieldset>
                    <legend>Weight</legend>
                    <div class='segmented'>
                      <label><input type='radio' name='weightMode' value='weight' checked/> Weight</label>
                      <label><input type='radio' name='weightMode' value='lr'/> L/R Weight</label>
                      <label><input type='radio' name='weightMode' value='bands'/> Bands</label>
                      <label><input type='radio' name='weightMode' value='accom'/> Accommodating</label>
                      <label><input type='radio' name='weightMode' value='none'/> None</label>
                      <label><input type='radio' name='weightMode' value='custom'/> Custom</label>
                    </div>
                    <div class='stacked-row weight-weight'>
                      <label>Weight <input type='number' step='0.5' min='0' name='weightValue' placeholder='225'/></label>
                      <label>Unit
                        <select name='weightUnit'><option value='lb'>lb</option><option value='kg'>kg</option></select>
                      </label>
                    </div>
                    <div class='stacked-row weight-lr is-hidden'>
                      <label>Left <input type='number' step='0.5' min='0' name='weightLeft' placeholder='40'/></label>
                      <label>Right <input type='number' step='0.5' min='0' name='weightRight' placeholder='40'/></label>
                      <label>Unit
                        <select name='weightUnitLr'><option value='lb'>lb</option><option value='kg'>kg</option></select>
                      </label>
                    </div>
                    <div class='stacked-row weight-bands is-hidden'>
                      <label>Bands <input type='text' name='weightBands' placeholder='RED+BLUE'/></label>
                    </div>
                    <div class='stacked-row weight-accom is-hidden'>
                      <label>Bar <input type='number' step='0.5' min='0' name='accomBar' placeholder='225'/></label>
                      <label>Unit
                        <select name='accomUnit'><option value='lb'>lb</option><option value='kg'>kg</option></select>
                      </label>
                      <label>Resistance
                        <select name='accomMode'><option value='chains'>Chains</option><option value='bands'>Bands</option></select>
                      </label>
                      <label class='accom-chains'>Chain <input type='number' step='0.5' min='0' name='accomChain' placeholder='40'/></label>
                      <label class='accom-bands is-hidden'>Bands <input type='text' name='accomBands' placeholder='RED+BLUE'/></label>
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
                options,
                WebHtml.escapeHtml(prefill.weight()),
                WebHtml.escapeHtml(prefill.weight()),
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

    static String formatExecution(LiftExecution execution) {
        String notes = execution.notes() == null ? "" : execution.notes();
        return "%s — %s%s"
                .formatted(
                        DATE_FORMAT.format(execution.date()),
                        formatSets(execution.sets()),
                        notes.isBlank() ? "" : " (" + notes + ")"
                );
    }

    static String formatSets(List<ExecutionSet> sets) {
        List<String> parts = new ArrayList<>();
        for (ExecutionSet set : sets) {
            String item = formatMetric(set.metric()) + " @ " + set.weight();
            if (set.rpe() != null) {
                item += " rpe " + String.format(Locale.ROOT, "%.1f", set.rpe());
            }
            parts.add(item);
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
