package com.lifttrax.cli;

import com.lifttrax.models.Lift;
import com.lifttrax.workout.PlannedWorkoutFile;
import com.lifttrax.workout.PlannedWorkoutJson;
import com.lifttrax.workout.PlannedWorkoutText;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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
        .append(
            "<p>Enter what you actually completed. Planned counts are prefilled, and weights stay open for today's result.</p>");
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
        .append("<label class='session-date'>Training date <input type='date' name='date' value='")
        .append(WebHtml.escapeHtml(date.toString()))
        .append("' required/></label>");
    Set<String> localLiftNames = new HashSet<>();
    for (Lift lift : localLifts) {
      localLiftNames.add(lift.name());
    }
    for (int blockIndex = 0; blockIndex < day.blocks().size(); blockIndex++) {
      appendBlock(html, day.blocks().get(blockIndex), blockIndex == 0, localLiftNames);
    }
    html.append(
            "<button type='submit' class='save-workout-session-btn'>Save Completed Workout</button>")
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

  private static void appendBlock(
      StringBuilder html,
      PlannedWorkoutFile.PlannedWorkoutBlock block,
      boolean current,
      Set<String> localLiftNames) {
    html.append("<section class='session-block")
        .append(current ? " is-current" : "")
        .append("'><header><span class='session-block-label'>")
        .append(current ? "Current block" : "Upcoming work")
        .append("</span><h2>")
        .append(WebHtml.escapeHtml(block.title()))
        .append("</h2><p class='muted'>")
        .append(WebHtml.escapeHtml(PlannedWorkoutText.blockMeta(block)))
        .append("</p></header>");
    appendNotes(html, block.notes());
    for (int exerciseIndex = 0; exerciseIndex < block.exercises().size(); exerciseIndex++) {
      String key = block.order() + ":" + exerciseIndex;
      appendExercise(html, key, block.exercises().get(exerciseIndex), localLiftNames);
    }
    html.append("</section>");
  }

  private static void appendExercise(
      StringBuilder html,
      String key,
      PlannedWorkoutFile.PlannedExercise exercise,
      Set<String> localLiftNames) {
    html.append("<article class='session-exercise' data-exercise-key='")
        .append(WebHtml.escapeHtml(key))
        .append("' data-planned-lift='")
        .append(WebHtml.escapeHtml(exercise.name()))
        .append("'><header><h3>")
        .append(WebHtml.escapeHtml(exercise.name()))
        .append("</h3><label>Status <select class='js-session-exercise-state'>")
        .append("<option value='complete'>Complete</option>")
        .append("<option value='skipped'>Skipped</option>")
        .append("</select></label></header>")
        .append("<label>Exercise <select class='js-session-performed-lift' required>");
    for (String name : PlannedWorkoutSessionService.allowedLiftNames(exercise)) {
      html.append("<option value='")
          .append(WebHtml.escapeHtml(name))
          .append("'")
          .append(localLiftNames.contains(name) ? "" : " disabled")
          .append(">")
          .append(WebHtml.escapeHtml(name));
      if (!localLiftNames.contains(name)) {
        html.append(" (not in local lifts)");
      }
      html.append("</option>");
    }
    html.append("</select></label>");
    if (!exercise.substitutionOptions().isEmpty()) {
      html.append("<p class='muted'>Swap choices come from this workout file.</p>");
    }
    if (!exercise.notes().isBlank()) {
      html.append("<p class='muted'><strong>Plan note:</strong> ")
          .append(WebHtml.escapeHtml(exercise.notes()))
          .append("</p>");
    }
    html.append("<div class='session-set-list'>");
    List<PlannedWorkoutFile.PlannedSetTarget> plannedSets = exercise.plannedSets();
    if (plannedSets.isEmpty()) {
      appendSet(html, null, 1);
    } else {
      for (int setIndex = 0; setIndex < plannedSets.size(); setIndex++) {
        appendSet(html, plannedSets.get(setIndex), setIndex + 1);
      }
    }
    html.append("</div><label>Exercise notes <input type='text' class='js-session-exercise-notes' ")
        .append("placeholder='Optional notes for history'/></label></article>");
  }

  private static void appendSet(
      StringBuilder html, PlannedWorkoutFile.PlannedSetTarget target, int fallbackSetNumber) {
    MetricSeed seed = MetricSeed.from(target);
    int setNumber =
        target == null || target.setNumber() == null ? fallbackSetNumber : target.setNumber();
    html.append("<fieldset class='session-set' data-session-set><legend>Set ")
        .append(setNumber)
        .append("</legend><p class='muted'>Target: ")
        .append(
            WebHtml.escapeHtml(
                target == null ? "Enter completed work" : PlannedWorkoutText.plannedSet(target)))
        .append("</p><div class='session-set-grid'>")
        .append(
            "<label>Status <select class='js-session-set-state'><option value='complete'>Complete</option>")
        .append("<option value='skipped'>Skipped</option></select></label>")
        .append("<label>Metric <select class='js-session-metric'>")
        .append(option("reps", "Reps", seed.metricType()))
        .append(option("reps-lr", "L/R reps", seed.metricType()))
        .append(option("time", "Seconds", seed.metricType()))
        .append(option("distance", "Feet", seed.metricType()))
        .append("</select></label>")
        .append(
            "<label class='session-metric-single'>Value <input type='number' min='1' class='js-session-metric-value' value='")
        .append(WebHtml.escapeHtml(seed.metricValue()))
        .append("' required/></label>")
        .append(
            "<label class='session-metric-lr'>Left <input type='number' min='1' class='js-session-metric-left' value='")
        .append(WebHtml.escapeHtml(seed.metricLeft()))
        .append("' required/></label>")
        .append(
            "<label class='session-metric-lr'>Right <input type='number' min='1' class='js-session-metric-right' value='")
        .append(WebHtml.escapeHtml(seed.metricRight()))
        .append("' required/></label>")
        .append(
            "<label>Weight <input type='text' class='js-session-weight' placeholder='225 lb or none'/></label>")
        .append(
            "<label>RPE <input type='number' min='0' max='10' step='0.1' class='js-session-rpe' placeholder='8.5'/></label>")
        .append("</div></fieldset>");
  }

  private static String option(String value, String label, String selected) {
    return "<option value='"
        + WebHtml.escapeHtml(value)
        + "'"
        + (value.equals(selected) ? " selected" : "")
        + ">"
        + WebHtml.escapeHtml(label)
        + "</option>";
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

            function toggleSet(row) {
              const state = row.querySelector('.js-session-set-state');
              const metric = row.querySelector('.js-session-metric');
              const single = row.querySelector('.session-metric-single');
              const leftRight = row.querySelectorAll('.session-metric-lr');
              const skipped = state.value === 'skipped';
              const useLeftRight = metric.value === 'reps-lr';
              row.classList.toggle('is-skipped', skipped);
              single.classList.toggle('is-hidden', useLeftRight);
              leftRight.forEach((label) => label.classList.toggle('is-hidden', !useLeftRight));
              row.querySelector('.js-session-metric-value').disabled = skipped || useLeftRight;
              row.querySelector('.js-session-metric-left').disabled = skipped || !useLeftRight;
              row.querySelector('.js-session-metric-right').disabled = skipped || !useLeftRight;
              row.querySelector('.js-session-weight').disabled = skipped;
              row.querySelector('.js-session-rpe').disabled = skipped;
            }

            function toggleExercise(card) {
              const skipped = card.querySelector('.js-session-exercise-state').value === 'skipped';
              card.classList.toggle('is-skipped', skipped);
              if (skipped) {
                card.querySelectorAll('input, select').forEach((control) => control.disabled = true);
                card.querySelector('.js-session-exercise-state').disabled = false;
                return;
              }
              card.querySelector('.js-session-performed-lift').disabled = false;
              card.querySelector('.js-session-exercise-notes').disabled = false;
              card.querySelectorAll('[data-session-set]').forEach((row) => {
                row.querySelector('.js-session-set-state').disabled = false;
                row.querySelector('.js-session-metric').disabled = false;
                toggleSet(row);
              });
            }

            form.querySelectorAll('[data-session-set]').forEach((row) => {
              row.querySelector('.js-session-set-state').addEventListener('change', () => toggleSet(row));
              row.querySelector('.js-session-metric').addEventListener('change', () => toggleSet(row));
              toggleSet(row);
            });
            form.querySelectorAll('.session-exercise').forEach((card) => {
              card.querySelector('.js-session-exercise-state').addEventListener('change', () => toggleExercise(card));
              toggleExercise(card);
            });

            form.addEventListener('submit', () => {
              const results = Array.from(form.querySelectorAll('.session-exercise')).map((card) => ({
                exerciseKey: card.dataset.exerciseKey,
                plannedLift: card.dataset.plannedLift,
                performedLift: card.querySelector('.js-session-performed-lift').value,
                state: card.querySelector('.js-session-exercise-state').value,
                notes: card.querySelector('.js-session-exercise-notes').value,
                sets: Array.from(card.querySelectorAll('[data-session-set]')).map((row) => ({
                  state: row.querySelector('.js-session-set-state').value,
                  metricType: row.querySelector('.js-session-metric').value,
                  metricValue: row.querySelector('.js-session-metric-value').value,
                  metricLeft: row.querySelector('.js-session-metric-left').value,
                  metricRight: row.querySelector('.js-session-metric-right').value,
                  weight: row.querySelector('.js-session-weight').value,
                  rpe: row.querySelector('.js-session-rpe').value
                }))
              }));
              form.querySelector('.js-session-results').value = JSON.stringify(results);
            });
          })();
        </script>
        """);
  }

  private record MetricSeed(
      String metricType, String metricValue, String metricLeft, String metricRight) {
    static MetricSeed from(PlannedWorkoutFile.PlannedSetTarget target) {
      if (target == null) {
        return new MetricSeed("reps", "", "", "");
      }
      return switch (target.metricType()) {
        case "reps" -> new MetricSeed("reps", text(target.reps()), "", "");
        case "reps_lr" ->
            new MetricSeed("reps-lr", "", text(target.repsLeft()), text(target.repsRight()));
        case "reps_range" ->
            new MetricSeed(
                "reps",
                text(target.repsMax() == null ? target.repsMin() : target.repsMax()),
                "",
                "");
        case "time_seconds" -> new MetricSeed("time", text(target.seconds()), "", "");
        case "distance_feet" -> new MetricSeed("distance", text(target.distanceFeet()), "", "");
        default -> new MetricSeed("reps", "", "", "");
      };
    }

    private static String text(Integer value) {
      return value == null ? "" : String.format(Locale.ROOT, "%d", value);
    }
  }
}
