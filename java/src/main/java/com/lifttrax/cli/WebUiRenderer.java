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
                  <div class='stacked-row'>
                    <label>Weight <input type='text' name='weight' value='%s' placeholder='225 lb'/></label>
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
