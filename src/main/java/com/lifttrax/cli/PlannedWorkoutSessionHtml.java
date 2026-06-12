package com.lifttrax.cli;

import com.lifttrax.db.Database;
import com.lifttrax.models.Lift;
import com.lifttrax.workout.PlannedWorkoutFile;
import com.lifttrax.workout.PlannedWorkoutHistory;
import com.lifttrax.workout.PlannedWorkoutJson;
import com.lifttrax.workout.PlannedWorkoutText;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Renders a follow-along workout day seeded from an imported workout file. */
final class PlannedWorkoutSessionHtml {
  private PlannedWorkoutSessionHtml() {}

  static String renderPage(
      PlannedWorkoutFile workoutFile,
      int weekNumber,
      String dayOfWeek,
      List<Lift> localLifts,
      LocalDate date) {
    return renderPage(workoutFile, weekNumber, dayOfWeek, localLifts, date, null);
  }

  static String renderPage(
      PlannedWorkoutFile workoutFile,
      int weekNumber,
      String dayOfWeek,
      List<Lift> localLifts,
      LocalDate date,
      Database db) {
    PlannedWorkoutFile.PlannedWorkoutDay day =
        PlannedWorkoutSessionService.findDay(workoutFile, weekNumber, dayOfWeek);
    StringBuilder html = new StringBuilder();
    html.append("<p><a href='/?tab=import-workout'>Back to Import Workout</a></p>")
        .append("<h1>Train ")
        .append(WebHtml.escapeHtml(day.title()))
        .append("</h1>")
        .append("<p class='muted'>")
        .append(WebHtml.escapeHtml(workoutFile.metadata().name()))
        .append(" / Week ")
        .append(weekNumber)
        .append("</p>")
        .append("<p>Log completed sets as you train.</p>");
    appendNotes(html, day.notes());
    html.append(
            "<form method='post' action='/save-planned-workout-session' class='planned-session-form'>")
        .append("<input type='hidden' name='plannedWorkoutJson' value='")
        .append(WebHtml.escapeHtml(writeWorkoutJson(workoutFile)))
        .append("'/>")
        .append("<input type='hidden' name='weekNumber' value='")
        .append(weekNumber)
        .append("'/>")
        .append("<input type='hidden' name='dayOfWeek' value='")
        .append(WebHtml.escapeHtml(dayOfWeek))
        .append("'/>")
        .append(
            "<input type='hidden' name='sessionResultsJson' class='js-session-results' value='[]'/>")
        .append(
            "<label class='session-date'>Training date <input type='date' name='sessionDate' value='")
        .append(WebHtml.escapeHtml(date.toString()))
        .append("' required/></label>");
    appendBlockNavigation(html, day.blocks());
    Set<String> localLiftNames = new LinkedHashSet<>();
    Map<String, String> localLiftNotes = new HashMap<>();
    for (Lift lift : localLifts) {
      localLiftNames.add(lift.name());
      localLiftNotes.put(lift.name(), lift.notes() == null ? "" : lift.notes());
    }
    for (int blockIndex = 0; blockIndex < day.blocks().size(); blockIndex++) {
      appendBlock(
          html,
          day.blocks().get(blockIndex),
          blockIndex,
          day.blocks().size(),
          localLiftNames,
          localLiftNotes,
          date,
          db);
    }
    html.append("<button type='submit' class='save-workout-session-btn")
        .append(day.blocks().size() > 1 ? " is-hidden" : "")
        .append("'>Save Completed Workout</button>")
        .append("</form>");
    appendScript(html);
    return html.toString();
  }

  static String renderSavedPage(
      PlannedWorkoutFile workoutFile,
      int weekNumber,
      String dayOfWeek,
      LocalDate date,
      PlannedWorkoutSessionService.SaveSummary summary) {
    PlannedWorkoutFile.PlannedWorkoutDay day =
        PlannedWorkoutSessionService.findDay(workoutFile, weekNumber, dayOfWeek);
    StringBuilder html = new StringBuilder();
    html.append("<h1>Workout Saved</h1>")
        .append("<p class='status success'>")
        .append(summary.loggedExecutionCount())
        .append(summary.loggedExecutionCount() == 1 ? " execution" : " executions")
        .append(" logged for ")
        .append(WebHtml.escapeHtml(day.title()))
        .append(" on ")
        .append(WebHtml.escapeHtml(date.toString()))
        .append(".</p>");
    if (!summary.loggedExercises().isEmpty()) {
      html.append("<ul class='session-save-list'>");
      for (PlannedWorkoutSessionService.LoggedExercise exercise : summary.loggedExercises()) {
        html.append("<li><strong>")
            .append(WebHtml.escapeHtml(exercise.liftName()))
            .append("</strong>: ")
            .append(exercise.setCount())
            .append(exercise.setCount() == 1 ? " set" : " sets");
        if (exercise.substitution()) {
          html.append(" <span class='muted'>(swapped exercise)</span>");
        }
        html.append("</li>");
      }
      html.append("</ul>");
    }
    if (summary.skippedExercises() > 0 || summary.skippedSets() > 0) {
      html.append("<p class='muted'>Skipped ")
          .append(summary.skippedExercises())
          .append(summary.skippedExercises() == 1 ? " exercise" : " exercises")
          .append(" and ")
          .append(summary.skippedSets())
          .append(summary.skippedSets() == 1 ? " set" : " sets")
          .append(".</p>");
    }
    html.append("<p><a class='compact-btn' href='/?tab=dashboard'>Back to Dashboard</a> ")
        .append(
            "<a class='compact-btn secondary' href='/?tab=import-workout'>Import Another Workout</a></p>");
    return html.toString();
  }

  private static void appendBlockNavigation(
      StringBuilder html, List<PlannedWorkoutFile.PlannedWorkoutBlock> blocks) {
    if (blocks.isEmpty()) {
      return;
    }
    html.append("<nav class='session-block-nav' aria-label='Workout block navigation'>")
        .append(
            "<div class='session-block-summary'><strong class='js-session-block-position'>Block 1 of ")
        .append(blocks.size())
        .append("</strong><span class='muted js-session-block-title'>")
        .append(WebHtml.escapeHtml(blocks.get(0).title()))
        .append("</span></div><progress class='js-session-block-progress' value='1' max='")
        .append(blocks.size())
        .append("'></progress><div class='session-block-actions'>")
        .append(
            "<button type='button' class='secondary js-session-previous-block' disabled>Previous block</button>")
        .append("<button type='button' class='js-session-next-block")
        .append(blocks.size() == 1 ? " is-hidden" : "")
        .append("'>Next block</button></div></nav>");
  }

  private static void appendBlock(
      StringBuilder html,
      PlannedWorkoutFile.PlannedWorkoutBlock block,
      int blockIndex,
      int blockCount,
      Set<String> localLiftNames,
      Map<String, String> localLiftNotes,
      LocalDate date,
      Database db) {
    html.append("<section class='session-block")
        .append(blockIndex == 0 ? " is-current" : " is-hidden")
        .append("' data-session-block-index='")
        .append(blockIndex)
        .append("'><header><span class='session-block-label'>Block ")
        .append(blockIndex + 1)
        .append(" of ")
        .append(blockCount)
        .append("</span><h2>")
        .append(WebHtml.escapeHtml(block.title()))
        .append("</h2><p class='muted'>")
        .append(WebHtml.escapeHtml(PlannedWorkoutText.blockMeta(block)))
        .append("</p></header>");
    appendNotes(html, block.notes());
    for (int exerciseIndex = 0; exerciseIndex < block.exercises().size(); exerciseIndex++) {
      String key = block.order() + ":" + exerciseIndex;
      appendExercise(
          html,
          key,
          block,
          block.exercises().get(exerciseIndex),
          localLiftNames,
          localLiftNotes,
          date,
          db);
    }
    html.append("</section>");
  }

  private static void appendExercise(
      StringBuilder html,
      String key,
      PlannedWorkoutFile.PlannedWorkoutBlock block,
      PlannedWorkoutFile.PlannedExercise exercise,
      Set<String> localLiftNames,
      Map<String, String> localLiftNotes,
      LocalDate date,
      Database db) {
    html.append("<article class='session-exercise' data-exercise-key='")
        .append(WebHtml.escapeHtml(key))
        .append("' data-planned-lift='")
        .append(WebHtml.escapeHtml(exercise.name()))
        .append("'><header><h3>")
        .append(WebHtml.escapeHtml(exercise.name()))
        .append("</h3><label>Status <select class='js-session-exercise-state'>")
        .append("<option value='complete'>Complete</option>")
        .append("<option value='skipped'>Skipped</option>")
        .append("</select></label></header>");
    html.append(
            "<div class='session-swap-controls'><button type='button' class='compact-btn secondary js-session-swap-toggle' aria-expanded='false'>")
        .append("Change lift</button><div class='session-swap-panel is-hidden'>")
        .append("<label>Performed lift <select class='js-session-performed-lift' required>")
        .append("<optgroup label='Planned and recommended'>");
    Set<String> recommendedNames = PlannedWorkoutSessionService.recommendedLiftNames(exercise);
    appendLiftOptions(html, recommendedNames, localLiftNames, localLiftNotes, true);
    html.append("</optgroup>");
    Set<String> otherLocalLiftNames = new LinkedHashSet<>(localLiftNames);
    otherLocalLiftNames.removeAll(recommendedNames);
    if (!otherLocalLiftNames.isEmpty()) {
      html.append("<optgroup label='Other lifts in your library'>");
      appendLiftOptions(html, otherLocalLiftNames, localLiftNames, localLiftNotes, false);
      html.append("</optgroup>");
    }
    html.append(
        "</select></label><p class='muted'>Choose any lift from your library. Workout-approved alternatives are listed first.</p></div></div>");
    appendLiftNote(html, localLiftNotes.getOrDefault(exercise.name(), ""));
    if (!exercise.notes().isBlank()) {
      html.append("<p class='muted'><strong>Plan note:</strong> ")
          .append(WebHtml.escapeHtml(exercise.notes()))
          .append("</p>");
    }
    appendHistory(html, db, block, exercise);
    List<PlannedWorkoutFile.PlannedSetTarget> plannedSets = exercise.plannedSets();
    appendTargets(html, plannedSets, exercise.name(), db);
    MetricSeed seed = firstMetricSeed(plannedSets, exercise.name(), db);
    html.append(
            "<div class='add-execution-form session-execution-widget js-session-execution-input'>")
        .append(
            ExecutionInputWidgetHtml.render(
                prefill(block, exercise, seed, plannedSets, date), List.of(), false, true))
        .append("</div></article>");
  }

  private static WebUiRenderer.AddExecutionPrefill prefill(
      PlannedWorkoutFile.PlannedWorkoutBlock block,
      PlannedWorkoutFile.PlannedExercise exercise,
      MetricSeed seed,
      List<PlannedWorkoutFile.PlannedSetTarget> plannedSets,
      LocalDate date) {
    boolean deload = plannedSets.stream().anyMatch(PlannedWorkoutFile.PlannedSetTarget::deload);
    return new WebUiRenderer.AddExecutionPrefill(
        exercise.name(),
        seed.weight(),
        "1",
        "",
        seed.metricType(),
        seed.metricValue().isBlank() ? "5" : seed.metricValue(),
        seed.metricLeft().isBlank() ? "5" : seed.metricLeft(),
        seed.metricRight().isBlank() ? "5" : seed.metricRight(),
        date.toString(),
        block.warmup(),
        deload,
        "");
  }

  private static void appendTargets(
      StringBuilder html,
      List<PlannedWorkoutFile.PlannedSetTarget> plannedSets,
      String liftName,
      Database db) {
    if (plannedSets.isEmpty()) {
      html.append("<p class='muted'>Target: Enter completed work</p>");
      return;
    }
    html.append("<details class='session-targets' open><summary>Planned targets</summary><ol>");
    for (PlannedWorkoutFile.PlannedSetTarget target : plannedSets) {
      html.append("<li>Target: ")
          .append(WebHtml.escapeHtml(PlannedWorkoutText.plannedSet(target)))
          .append(suggestionHtml(db, liftName, target))
          .append("</li>");
    }
    html.append("</ol></details>");
  }

  private static MetricSeed firstMetricSeed(
      List<PlannedWorkoutFile.PlannedSetTarget> plannedSets, String liftName, Database db) {
    if (plannedSets.isEmpty()) {
      return new MetricSeed("reps", "5", "5", "5", "");
    }
    return MetricSeed.from(plannedSets.get(0), liftName, db);
  }

  private static String suggestionHtml(
      Database db, String liftName, PlannedWorkoutFile.PlannedSetTarget target) {
    String suggested = PlannedWorkoutText.suggestedWeight(db, liftName, target);
    if (suggested.isBlank()) {
      return "";
    }
    return " <span class='muted'>Suggested: " + WebHtml.escapeHtml(suggested) + "</span>";
  }

  private static void appendHistory(
      StringBuilder html,
      Database db,
      PlannedWorkoutFile.PlannedWorkoutBlock block,
      PlannedWorkoutFile.PlannedExercise exercise) {
    if (db == null) {
      return;
    }
    PlannedWorkoutHistory.Summary history = PlannedWorkoutHistory.lookup(db, block, exercise);
    if (history.unavailable()) {
      html.append("<div class='session-history muted'>History unavailable.</div>");
      return;
    }
    if (history.isEmpty()) {
      return;
    }
    html.append("<div class='session-history' aria-label='Exercise history'>");
    if (history.last() != null) {
      html.append("<span><strong>Last:</strong> ")
          .append(WebHtml.escapeHtml(history.last()))
          .append("</span>");
    }
    if (history.bestOneRepMax() != null) {
      html.append("<span><strong>Best 1RM:</strong> ")
          .append(WebHtml.escapeHtml(history.bestOneRepMax()))
          .append("</span>");
    }
    html.append("</div>");
  }

  private static void appendLiftOptions(
      StringBuilder html,
      Set<String> names,
      Set<String> localLiftNames,
      Map<String, String> localLiftNotes,
      boolean markUnavailable) {
    for (String name : names) {
      String notes = localLiftNotes.getOrDefault(name, "");
      html.append("<option value='")
          .append(WebHtml.escapeHtml(name))
          .append("' data-lift-note='")
          .append(WebHtml.escapeHtml(notes))
          .append("'")
          .append(markUnavailable && !localLiftNames.contains(name) ? " disabled" : "")
          .append(">")
          .append(WebHtml.escapeHtml(name));
      if (markUnavailable && !localLiftNames.contains(name)) {
        html.append(" (not in local lifts)");
      }
      html.append("</option>");
    }
  }

  private static void appendLiftNote(StringBuilder html, String note) {
    html.append("<p class='session-lift-note")
        .append(note.isBlank() ? " is-hidden" : "")
        .append("'><strong>Lift note:</strong> <span>")
        .append(WebHtml.escapeHtml(note))
        .append("</span></p>");
  }

  private static void appendNotes(StringBuilder html, List<String> notes) {
    if (notes.isEmpty()) {
      return;
    }
    html.append("<ul class='planned-notes'>");
    for (String note : notes) {
      html.append("<li>").append(WebHtml.escapeHtml(note)).append("</li>");
    }
    html.append("</ul>");
  }

  private static String writeWorkoutJson(PlannedWorkoutFile workoutFile) {
    try {
      return PlannedWorkoutJson.writeString(workoutFile);
    } catch (Exception e) {
      throw new IllegalArgumentException("Could not prepare imported workout for training.", e);
    }
  }

  private static void appendScript(StringBuilder html) {
    html.append(
        """
        <script>
          (function () {
            const form = document.querySelector('.planned-session-form');
            if (!form) {
              return;
            }

            const blocks = Array.from(form.querySelectorAll('[data-session-block-index]'));
            if (blocks.length === 0) {
              return;
            }
            const previousBlock = form.querySelector('.js-session-previous-block');
            const nextBlock = form.querySelector('.js-session-next-block');
            const saveWorkout = form.querySelector('.save-workout-session-btn');
            const blockPosition = form.querySelector('.js-session-block-position');
            const blockTitle = form.querySelector('.js-session-block-title');
            const blockProgress = form.querySelector('.js-session-block-progress');
            let currentBlockIndex = 0;

            function showBlock(index) {
              currentBlockIndex = Math.max(0, Math.min(index, blocks.length - 1));
              blocks.forEach((block, blockIndex) => {
                const current = blockIndex === currentBlockIndex;
                block.classList.toggle('is-hidden', !current);
                block.classList.toggle('is-current', current);
              });
              const current = blocks[currentBlockIndex];
              blockPosition.textContent = `Block ${currentBlockIndex + 1} of ${blocks.length}`;
              blockTitle.textContent = current.querySelector('h2').textContent;
              blockProgress.value = currentBlockIndex + 1;
              previousBlock.disabled = currentBlockIndex === 0;
              nextBlock.classList.toggle('is-hidden', currentBlockIndex === blocks.length - 1);
              saveWorkout.classList.toggle('is-hidden', currentBlockIndex !== blocks.length - 1);
              current.scrollIntoView({ behavior: 'smooth', block: 'start' });
            }

            const widgetSets = new WeakMap();
            const widgetRadioState = new WeakMap();

            function radioValue(widget, name, fallback) {
              const state = widgetRadioState.get(widget) || {};
              if (state[name]) {
                return state[name];
              }
              const selected = widget.querySelector(`input[name='${name}']:checked`);
              if (selected) {
                return selected.value;
              }
              return fallback;
            }

            function rememberRadioValue(widget, name, value) {
              const state = widgetRadioState.get(widget) || {};
              state[name] = value;
              widgetRadioState.set(widget, state);
            }

            function selectRadio(widget, name, value) {
              const input = widget.querySelector(`input[name='${name}'][value='${value}']`);
              rememberRadioValue(widget, name, value);
              if (!input) {
                return;
              }
              input.checked = true;
              input.dispatchEvent(new Event('change', {bubbles: true}));
            }

            function setInputValue(widget, name, value) {
              const input = widget.querySelector(`[name='${name}']`);
              if (input) {
                input.value = value;
              }
            }

            function syncMetricInputs(widget) {
              const isLr = radioValue(widget, 'metricType', 'reps') === 'reps-lr';
              const single = widget.querySelector('.metric-single');
              const lr = widget.querySelectorAll('.metric-lr');
              if (single) {
                single.classList.toggle('is-hidden', isLr);
              }
              lr.forEach((item) => item.classList.toggle('is-hidden', !isLr));
            }

            function syncWeightMode(widget) {
              const mode = radioValue(widget, 'weightMode', 'weight');
              widget.querySelectorAll('.weight-weight, .weight-lr, .weight-bands, .weight-accom, .weight-custom').forEach((group) => {
                group.classList.add('is-hidden');
              });
              const active = widget.querySelector(`.weight-${mode}`);
              if (active) {
                active.classList.remove('is-hidden');
              }
            }

            function syncAccomMode(widget) {
              const accomMode = widget.querySelector("select[name='accomMode']");
              if (!accomMode) {
                return;
              }
              const chains = widget.querySelector('.accom-chains');
              const bands = widget.querySelector('.accom-bands');
              const useBands = accomMode.value === 'bands';
              if (chains) {
                chains.classList.toggle('is-hidden', useBands);
              }
              if (bands) {
                bands.classList.toggle('is-hidden', !useBands);
              }
            }

            function selectedBandValues(widget, name) {
              return Array.from(widget.querySelectorAll(`input[name='${name}']:checked`)).map((item) => item.value);
            }

            function computeWeight(widget) {
              const mode = radioValue(widget, 'weightMode', 'weight');
              if (mode === 'none') {
                return 'none';
              }
              if (mode === 'custom') {
                return (widget.querySelector("input[name='customWeight']") || {}).value || '';
              }
              if (mode === 'lr') {
                const l = (widget.querySelector("input[name='weightLeft']") || {}).value || '';
                const r = (widget.querySelector("input[name='weightRight']") || {}).value || '';
                const unit = (widget.querySelector("select[name='weightUnitLr']") || {}).value || 'lb';
                return `${l}${unit}|${r}${unit}`;
              }
              if (mode === 'bands') {
                return selectedBandValues(widget, 'weightBandColors').join('+');
              }
              if (mode === 'accom') {
                const bar = (widget.querySelector("input[name='accomBar']") || {}).value || '';
                const unit = (widget.querySelector("select[name='accomUnit']") || {}).value || 'lb';
                const accomMode = (widget.querySelector("select[name='accomMode']") || {}).value || 'chains';
                if (accomMode === 'bands') {
                  return `${bar} ${unit}+${selectedBandValues(widget, 'accomBandColors').join('+')}`;
                }
                const chain = (widget.querySelector("input[name='accomChain']") || {}).value || '';
                return `${bar} ${unit}+${chain}c`;
              }
              const value = (widget.querySelector("input[name='weightValue']") || {}).value || '';
              const unit = (widget.querySelector("select[name='weightUnit']") || {}).value || 'lb';
              const computed = `${value} ${unit}`.trim();
              if (!computed || computed === 'lb' || computed === 'kg') {
                return (widget.querySelector("input[name='customWeight']") || {}).value || '';
              }
              return computed;
            }

            function metricPayload(widget) {
              const metricType = radioValue(widget, 'metricType', 'reps');
              const payload = {metricType};
              if (metricType === 'reps-lr') {
                payload.metricLeft = (widget.querySelector("input[name='metricLeft']") || {}).value || '';
                payload.metricRight = (widget.querySelector("input[name='metricRight']") || {}).value || '';
              } else {
                payload.metricValue = (widget.querySelector("input[name='metricValue']") || {}).value || '';
              }
              return payload;
            }

            function setEntryMode(widget) {
              const selected = widget.querySelector("input[name='setEntryMode']:checked");
              return selected ? selected.value : 'multiple';
            }

            function selectSetEntryMode(widget, mode) {
              const input = widget.querySelector(`input[name='setEntryMode'][value='${mode}']`);
              rememberRadioValue(widget, 'setEntryMode', mode);
              if (input) {
                input.checked = true;
              }
              syncSetEntryMode(widget);
            }

            function syncSetEntryMode(widget) {
              const individual = setEntryMode(widget) === 'individual';
              const multipleControls = widget.querySelector('.entry-mode-multiple');
              const details = widget.querySelector('.individual-sets-details');
              if (multipleControls) {
                multipleControls.classList.toggle('is-hidden', individual);
              }
              if (details) {
                details.open = individual;
                details.classList.toggle('is-hidden', !individual);
              }
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

            function renderSetList(widget) {
              const detailedSets = widgetSets.get(widget) || [];
              const list = widget.querySelector('.js-set-list');
              const hidden = widget.querySelector('.js-detailed-sets');
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
                  renderSetList(widget);
                });
                li.appendChild(document.createTextNode(' '));
                li.appendChild(remove);
                list.appendChild(li);
              });
              hidden.value = JSON.stringify(detailedSets);
            }

            function bindExecutionWidget(widget) {
              let detailedSets = [];
              try {
                detailedSets = JSON.parse((widget.querySelector('.js-detailed-sets') || {}).value || '[]');
              } catch (error) {
                detailedSets = [];
              }
              widgetSets.set(widget, detailedSets);
              ['metricType', 'weightMode', 'setEntryMode'].forEach((name) => {
                const checked = widget.querySelector(`input[name='${name}']:checked`);
                if (checked) {
                  rememberRadioValue(widget, name, checked.value);
                }
              });
              widget.querySelectorAll("input[name='metricType']").forEach((item) => {
                item.addEventListener('change', () => {
                  if (item.checked) {
                    rememberRadioValue(widget, 'metricType', item.value);
                  }
                  syncMetricInputs(widget);
                });
              });
              widget.querySelectorAll("input[name='weightMode']").forEach((item) => {
                item.addEventListener('change', () => {
                  if (item.checked) {
                    rememberRadioValue(widget, 'weightMode', item.value);
                  }
                  syncWeightMode(widget);
                });
              });
              widget.querySelectorAll("input[name='setEntryMode']").forEach((item) => {
                item.addEventListener('change', () => {
                  if (item.checked) {
                    rememberRadioValue(widget, 'setEntryMode', item.value);
                  }
                  syncSetEntryMode(widget);
                });
              });
              const accomMode = widget.querySelector("select[name='accomMode']");
              if (accomMode) {
                accomMode.addEventListener('change', () => syncAccomMode(widget));
              }
              const maxEffortSingleBtn = widget.querySelector('.js-max-effort-single');
              if (maxEffortSingleBtn) {
                maxEffortSingleBtn.addEventListener('click', () => {
                  selectRadio(widget, 'metricType', 'reps');
                  setInputValue(widget, 'metricValue', '1');
                  setInputValue(widget, 'setCount', '1');
                  setInputValue(widget, 'setCopies', '1');
                });
              }
              const bandsOnlyBtn = widget.querySelector('.js-bands-only');
              if (bandsOnlyBtn) {
                bandsOnlyBtn.addEventListener('click', () => selectRadio(widget, 'weightMode', 'bands'));
              }
              const barBandsBtn = widget.querySelector('.js-bar-bands');
              if (barBandsBtn) {
                barBandsBtn.addEventListener('click', () => {
                  selectRadio(widget, 'weightMode', 'accom');
                  const mode = widget.querySelector("select[name='accomMode']");
                  if (mode) {
                    mode.value = 'bands';
                    syncAccomMode(widget);
                  }
                });
              }
              const addSetBtn = widget.querySelector('.js-add-set');
              if (addSetBtn) {
                addSetBtn.addEventListener('click', () => {
                  selectSetEntryMode(widget, 'individual');
                  const setCopiesInput = widget.querySelector("input[name='setCopies']");
                  const copies = Math.max(1, parseInt((setCopiesInput && setCopiesInput.value) || '1', 10) || 1);
                  const payload = {
                    ...metricPayload(widget),
                    weight: computeWeight(widget),
                    rpe: (widget.querySelector("input[name='rpe']") || {}).value || ''
                  };
                  for (let i = 0; i < copies; i++) {
                    detailedSets.push({...payload});
                  }
                  renderSetList(widget);
                });
              }
              widget.addEventListener('keydown', (event) => {
                if (event.key !== 'Enter') {
                  return;
                }
                const tag = event.target.tagName;
                if (tag !== 'INPUT' && tag !== 'SELECT') {
                  return;
                }
                event.preventDefault();
                if (addSetBtn) {
                  addSetBtn.click();
                }
              });
              const clearSetsBtn = widget.querySelector('.js-clear-sets');
              if (clearSetsBtn) {
                clearSetsBtn.addEventListener('click', () => {
                  detailedSets.splice(0, detailedSets.length);
                  renderSetList(widget);
                });
              }
              syncMetricInputs(widget);
              syncWeightMode(widget);
              syncAccomMode(widget);
              syncSetEntryMode(widget);
              renderSetList(widget);
            }

            function collectExecutionSets(widget) {
              const detailedSets = widgetSets.get(widget) || [];
              if (detailedSets.length > 0 || setEntryMode(widget) === 'individual') {
                return detailedSets.map((set) => ({...set, state: 'complete'}));
              }
              const setCountInput = widget.querySelector("input[name='setCount']");
              const setCount = Math.max(1, parseInt((setCountInput && setCountInput.value) || '1', 10) || 1);
              const payload = {
                ...metricPayload(widget),
                weight: computeWeight(widget),
                rpe: (widget.querySelector("input[name='rpe']") || {}).value || '',
                state: 'complete'
              };
              return Array.from({length: setCount}, () => ({...payload}));
            }

            function updateLiftNote(card) {
              const performedLift = card.querySelector('.js-session-performed-lift');
              const note = card.querySelector('.session-lift-note');
              if (!performedLift || !note) {
                return;
              }
              const selected = performedLift.selectedOptions && performedLift.selectedOptions[0];
              const text = selected ? (selected.dataset.liftNote || '') : '';
              const noteText = note.querySelector('span');
              if (noteText) {
                noteText.textContent = text;
              }
              note.classList.toggle('is-hidden', !text.trim());
            }

            function toggleExercise(card) {
              const skipped = card.querySelector('.js-session-exercise-state').value === 'skipped';
              card.classList.toggle('is-skipped', skipped);
              card.querySelectorAll('input, select, button').forEach((control) => {
                control.disabled = skipped;
              });
              card.querySelector('.js-session-exercise-state').disabled = false;
              if (!skipped) {
                card.querySelectorAll('.js-session-execution-input').forEach((widget) => {
                  syncMetricInputs(widget);
                  syncWeightMode(widget);
                  syncAccomMode(widget);
                });
              }
            }

            form.querySelectorAll('.js-session-execution-input').forEach((widget) => bindExecutionWidget(widget));
            form.addEventListener('keydown', (event) => {
              if (event.key !== 'Enter') {
                return;
              }
              const tag = event.target.tagName;
              if (tag === 'INPUT' || tag === 'SELECT') {
                event.preventDefault();
              }
            });
            previousBlock.addEventListener('click', () => showBlock(currentBlockIndex - 1));
            nextBlock.addEventListener('click', () => showBlock(currentBlockIndex + 1));
            form.querySelectorAll('.session-exercise').forEach((card) => {
              card.querySelector('.js-session-exercise-state').addEventListener('change', () => toggleExercise(card));
              const swapToggle = card.querySelector('.js-session-swap-toggle');
              if (swapToggle) {
                const swapPanel = card.querySelector('.session-swap-panel');
                const performedLift = card.querySelector('.js-session-performed-lift');
                swapToggle.addEventListener('click', () => {
                  const expanded = swapToggle.getAttribute('aria-expanded') === 'true';
                  swapToggle.setAttribute('aria-expanded', String(!expanded));
                  swapPanel.classList.toggle('is-hidden', expanded);
                });
                performedLift.addEventListener('change', () => {
                  const swapped = performedLift.value !== card.dataset.plannedLift;
                  card.classList.toggle('is-swapped', swapped);
                  swapToggle.textContent = swapped ? `Changed to: ${performedLift.value}` : 'Change lift';
                  updateLiftNote(card);
                });
              }
              updateLiftNote(card);
              toggleExercise(card);
            });

            form.addEventListener('submit', () => {
              const results = Array.from(form.querySelectorAll('.session-exercise')).map((card) => {
                const widget = card.querySelector('.js-session-execution-input');
                const hiddenWeight = widget.querySelector('.js-weight-hidden');
                if (hiddenWeight) {
                  hiddenWeight.value = computeWeight(widget);
                }
                return {
                  exerciseKey: card.dataset.exerciseKey,
                  plannedLift: card.dataset.plannedLift,
                  performedLift: card.querySelector('.js-session-performed-lift').value,
                  state: card.querySelector('.js-session-exercise-state').value,
                  warmup: (widget.querySelector("input[name='warmup']") || {}).checked || false,
                  deload: (widget.querySelector("input[name='deload']") || {}).checked || false,
                  notes: (widget.querySelector("input[name='notes']") || {}).value || '',
                  sets: collectExecutionSets(widget)
                };
              });
              form.querySelector('.js-session-results').value = JSON.stringify(results);
            });
          })();
        </script>
        """);
  }

  private record MetricSeed(
      String metricType, String metricValue, String metricLeft, String metricRight, String weight) {
    static MetricSeed from(
        PlannedWorkoutFile.PlannedSetTarget target, String liftName, Database db) {
      if (target == null) {
        return new MetricSeed("reps", "", "", "", "");
      }
      String weight = suggestedWeight(target, liftName, db);
      return switch (target.metricType()) {
        case "reps" -> new MetricSeed("reps", text(target.reps()), "", "", weight);
        case "reps_lr" ->
            new MetricSeed(
                "reps-lr", "", text(target.repsLeft()), text(target.repsRight()), weight);
        case "reps_range" ->
            new MetricSeed(
                "reps",
                text(target.repsMax() == null ? target.repsMin() : target.repsMax()),
                "",
                "",
                weight);
        case "time_seconds" -> new MetricSeed("time", text(target.seconds()), "", "", weight);
        case "distance_feet" ->
            new MetricSeed("distance", text(target.distanceFeet()), "", "", weight);
        default -> new MetricSeed("reps", "", "", "", weight);
      };
    }

    private static String text(Integer value) {
      return value == null ? "" : String.valueOf(value);
    }

    private static String suggestedWeight(
        PlannedWorkoutFile.PlannedSetTarget target, String liftName, Database db) {
      return PlannedWorkoutText.suggestedWeight(db, liftName, target);
    }
  }
}
