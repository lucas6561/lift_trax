package com.lifttrax.cli;

import com.lifttrax.db.Database;
import com.lifttrax.models.SetMetric;
import com.lifttrax.workout.PlannedWorkoutFile;
import com.lifttrax.workout.PlannedWorkoutJson;
import com.lifttrax.workout.WorkoutHistoryFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Renders imported planned workout files for the lightweight web UI. */
final class PlannedWorkoutHtml {
  private PlannedWorkoutHtml() {}

  static String renderImportPanel() {
    return """
              <form method='post' action='/planned-workout-preview' class='planned-import-form'>
                <div class='planned-import-layout'>
                  <div class='planned-file-zone'>
                    <input id='plannedWorkoutFile' class='planned-file-input' type='file' accept='application/json,.json'/>
                    <label class='planned-file-picker' for='plannedWorkoutFile'>Select Workout JSON</label>
                    <div class='planned-file-meta'>
                      <strong class='js-planned-file-name'>No file selected</strong>
                      <span class='js-planned-file-detail muted'>Workout format v1</span>
                    </div>
                  </div>
                  <div class='planned-import-actions'>
                    <p class='js-planned-file-status muted'>Ready for a planned workout file.</p>
                    <input type='hidden' name='plannedWorkoutJson' class='js-planned-workout-json'/>
                    <button type='submit' class='compact-btn js-planned-preview' disabled>Preview Workout</button>
                  </div>
                </div>
              </form>
              <script>
                (function () {
                  const fileInput = document.querySelector('#plannedWorkoutFile');
                  const jsonInput = document.querySelector('.js-planned-workout-json');
                  const previewButton = document.querySelector('.js-planned-preview');
                  const fileName = document.querySelector('.js-planned-file-name');
                  const fileDetail = document.querySelector('.js-planned-file-detail');
                  const status = document.querySelector('.js-planned-file-status');

                  if (!fileInput || !jsonInput || !previewButton || !fileName || !fileDetail || !status) {
                    return;
                  }

                  function resetImport(message) {
                    jsonInput.value = '';
                    previewButton.disabled = true;
                    status.textContent = message;
                    status.classList.remove('success', 'error');
                    status.classList.add('muted');
                  }

                  fileInput.addEventListener('change', () => {
                    const file = fileInput.files && fileInput.files.length > 0 ? fileInput.files[0] : null;
                    if (!file) {
                      fileName.textContent = 'No file selected';
                      fileDetail.textContent = 'Workout format v1';
                      resetImport('Ready for a planned workout file.');
                      return;
                    }

                    fileName.textContent = file.name;
                    fileDetail.textContent = `${Math.max(1, Math.round(file.size / 1024))} KB`;
                    resetImport('Reading file...');

                    const reader = new FileReader();
                    reader.addEventListener('load', () => {
                      const text = typeof reader.result === 'string' ? reader.result : '';
                      try {
                        const parsed = JSON.parse(text);
                        const name = parsed && parsed.metadata && parsed.metadata.name ? parsed.metadata.name : file.name;
                        const weeks = Array.isArray(parsed.weeks) ? parsed.weeks.length : 0;
                        jsonInput.value = text;
                        previewButton.disabled = false;
                        status.textContent = weeks > 0 ? `${name} - ${weeks} week${weeks === 1 ? '' : 's'}` : name;
                        status.classList.remove('muted', 'error');
                        status.classList.add('success');
                      } catch (error) {
                        fileDetail.textContent = 'Invalid JSON';
                        resetImport('That file is not valid JSON.');
                        status.classList.remove('muted', 'success');
                        status.classList.add('error');
                      }
                    });
                    reader.addEventListener('error', () => {
                      resetImport('Could not read that file.');
                      status.classList.remove('muted', 'success');
                      status.classList.add('error');
                    });
                    reader.readAsText(file);
                  });

                  document.querySelector('.planned-import-form').addEventListener('submit', (event) => {
                    if (!jsonInput.value) {
                      event.preventDefault();
                      resetImport('Choose a JSON file first.');
                      status.classList.remove('muted', 'success');
                      status.classList.add('error');
                    }
                  });
                })();
              </script>
              """;
  }

  static String renderPage(PlannedWorkoutFile workoutFile) {
    return renderPage(workoutFile, null);
  }

  static String renderPage(PlannedWorkoutFile workoutFile, Database db) {
    StringBuilder html = new StringBuilder();
    html.append("<p><a href='/?tab=import-workout'>Back to Import Workout</a></p>");
    html.append("<h1>").append(WebHtml.escapeHtml(workoutFile.metadata().name())).append("</h1>");
    html.append("<p class='muted'>")
        .append(WebHtml.escapeHtml(workoutFile.metadata().description()))
        .append("</p>");
    html.append("<p><strong>Source:</strong> ")
        .append(WebHtml.escapeHtml(workoutFile.source().generator()))
        .append(" / ")
        .append(WebHtml.escapeHtml(workoutFile.source().kind()))
        .append(" <strong>Generated:</strong> ")
        .append(WebHtml.escapeHtml(workoutFile.source().generatedAt()))
        .append("</p>");
    for (PlannedWorkoutFile.PlannedWorkoutWeek week : workoutFile.weeks()) {
      appendPlannedWeek(html, workoutFile, week, db);
    }
    if (!workoutFile.completedWorkouts().isEmpty()) {
      html.append("<h2>Completed Results</h2>");
      html.append("<p>")
          .append(workoutFile.completedWorkouts().size())
          .append(" completed workouts recorded.</p>");
    }
    return html.toString();
  }

  private static void appendPlannedWeek(
      StringBuilder html,
      PlannedWorkoutFile workoutFile,
      PlannedWorkoutFile.PlannedWorkoutWeek week,
      Database db) {
    html.append("<section class='planned-week'><h2>Week ")
        .append(week.weekNumber())
        .append("</h2>");
    for (PlannedWorkoutFile.PlannedWorkoutDay day : week.days()) {
      html.append("<section class='planned-day'><h3>")
          .append(WebHtml.escapeHtml(day.title()))
          .append("</h3>");
      for (PlannedWorkoutFile.PlannedWorkoutBlock block : day.blocks()) {
        appendPlannedBlock(html, block, db);
      }
      appendStartDayForm(html, workoutFile, week, day);
      html.append("</section>");
    }
    html.append("</section>");
  }

  private static void appendStartDayForm(
      StringBuilder html,
      PlannedWorkoutFile workoutFile,
      PlannedWorkoutFile.PlannedWorkoutWeek week,
      PlannedWorkoutFile.PlannedWorkoutDay day) {
    html.append("<form method='post' action='/planned-workout-session' class='compact-actions'>")
        .append("<input type='hidden' name='plannedWorkoutJson' value='")
        .append(WebHtml.escapeHtml(writeWorkoutJson(workoutFile)))
        .append("'/>")
        .append("<input type='hidden' name='weekNumber' value='")
        .append(week.weekNumber())
        .append("'/>")
        .append("<input type='hidden' name='dayOfWeek' value='")
        .append(WebHtml.escapeHtml(day.dayOfWeek()))
        .append("'/>")
        .append("<button type='submit' class='compact-btn'>Start This Day</button>")
        .append("</form>");
  }

  private static String writeWorkoutJson(PlannedWorkoutFile workoutFile) {
    try {
      return PlannedWorkoutJson.writeString(workoutFile);
    } catch (Exception e) {
      throw new IllegalArgumentException("Could not prepare imported workout for training.", e);
    }
  }

  private static void appendPlannedBlock(
      StringBuilder html, PlannedWorkoutFile.PlannedWorkoutBlock block, Database db) {
    html.append("<article class='planned-block'><header><h4>")
        .append(WebHtml.escapeHtml(block.title()))
        .append("</h4><p class='muted'>")
        .append(WebHtml.escapeHtml(formatBlockMeta(block)))
        .append("</p></header>");
    if (!block.notes().isEmpty()) {
      html.append("<ul class='planned-notes'>");
      for (String note : block.notes()) {
        html.append("<li>").append(WebHtml.escapeHtml(note)).append("</li>");
      }
      html.append("</ul>");
    }
    html.append("<ol class='planned-exercise-list'>");
    for (PlannedWorkoutFile.PlannedExercise exercise : block.exercises()) {
      appendPlannedExercise(html, block, exercise, db);
    }
    html.append("</ol></article>");
  }

  private static void appendPlannedExercise(
      StringBuilder html,
      PlannedWorkoutFile.PlannedWorkoutBlock block,
      PlannedWorkoutFile.PlannedExercise exercise,
      Database db) {
    html.append("<li><strong>").append(WebHtml.escapeHtml(exercise.name())).append("</strong>");
    String details = formatExerciseDetails(exercise);
    if (!details.isBlank()) {
      html.append(" <span class='muted'>").append(WebHtml.escapeHtml(details)).append("</span>");
    }
    html.append("<div>")
        .append(WebHtml.escapeHtml(formatPlannedSets(exercise.plannedSets())))
        .append("</div>");
    if (!exercise.notes().isBlank()) {
      html.append("<div class='muted'>Notes: ")
          .append(WebHtml.escapeHtml(exercise.notes()))
          .append("</div>");
    }
    appendHistory(html, db, block, exercise);
    if (!exercise.substitutionOptions().isEmpty()) {
      html.append("<div class='muted'>Swap options: ")
          .append(WebHtml.escapeHtml(joinList(exercise.substitutionOptions())))
          .append("</div>");
    }
    html.append("</li>");
  }

  private static void appendHistory(
      StringBuilder html,
      Database db,
      PlannedWorkoutFile.PlannedWorkoutBlock block,
      PlannedWorkoutFile.PlannedExercise exercise) {
    if (db == null) {
      return;
    }

    try {
      SetMetric metric = historyMetric(block, exercise);
      boolean includeDeload =
          exercise.plannedSets().stream().anyMatch(PlannedWorkoutFile.PlannedSetTarget::deload);
      String last =
          WorkoutHistoryFormatter.lastExecutionSummary(
              db, exercise.name(), block.warmup(), metric, includeDeload);
      String max = WorkoutHistoryFormatter.bestOneRepMax(db, exercise.name());
      if (last == null && max == null) {
        return;
      }
      html.append("<div class='planned-history'>");
      if (last != null) {
        html.append("<span><strong>Last:</strong> ")
            .append(WebHtml.escapeHtml(last))
            .append("</span>");
      }
      if (max != null) {
        html.append("<span><strong>Best 1RM:</strong> ")
            .append(WebHtml.escapeHtml(max))
            .append("</span>");
      }
      html.append("</div>");
    } catch (Exception e) {
      html.append("<div class='planned-history muted'>History unavailable.</div>");
    }
  }

  private static SetMetric historyMetric(
      PlannedWorkoutFile.PlannedWorkoutBlock block, PlannedWorkoutFile.PlannedExercise exercise) {
    if (block.rounds() != null || exercise.plannedSets().isEmpty()) {
      return null;
    }
    return metricFromTarget(exercise.plannedSets().get(0));
  }

  private static SetMetric metricFromTarget(PlannedWorkoutFile.PlannedSetTarget target) {
    return switch (target.metricType()) {
      case "reps" -> target.reps() == null ? null : new SetMetric.Reps(target.reps());
      case "reps_lr" ->
          target.repsLeft() == null || target.repsRight() == null
              ? null
              : new SetMetric.RepsLr(target.repsLeft(), target.repsRight());
      case "time_seconds" ->
          target.seconds() == null ? null : new SetMetric.TimeSecs(target.seconds());
      case "distance_feet" ->
          target.distanceFeet() == null ? null : new SetMetric.DistanceFeet(target.distanceFeet());
      default -> null;
    };
  }

  private static String formatBlockMeta(PlannedWorkoutFile.PlannedWorkoutBlock block) {
    List<String> parts = new ArrayList<>();
    parts.add(block.blockType().replace('_', ' '));
    if (block.rounds() != null) {
      parts.add(block.rounds() + " rounds");
    }
    if (block.warmup()) {
      parts.add("warm-up");
    }
    return joinList(parts);
  }

  private static String formatExerciseDetails(PlannedWorkoutFile.PlannedExercise exercise) {
    List<String> details = new ArrayList<>();
    if (exercise.region() != null && !exercise.region().isBlank()) {
      details.add(exercise.region());
    }
    if (exercise.type() != null && !exercise.type().isBlank()) {
      details.add(exercise.type());
    }
    if (!exercise.muscles().isEmpty()) {
      details.add(joinList(exercise.muscles()));
    }
    return joinList(details);
  }

  private static String formatPlannedSets(List<PlannedWorkoutFile.PlannedSetTarget> sets) {
    if (sets.isEmpty()) {
      return "No planned set targets.";
    }
    List<String> groups = new ArrayList<>();
    int index = 0;
    while (index < sets.size()) {
      PlannedWorkoutFile.PlannedSetTarget target = sets.get(index);
      int count = 1;
      while (index + count < sets.size() && samePlannedTarget(target, sets.get(index + count))) {
        count++;
      }
      String formatted = formatPlannedSet(target);
      groups.add(count > 1 ? count + "x " + formatted : formatted);
      index += count;
    }
    return joinList(groups);
  }

  private static boolean samePlannedTarget(
      PlannedWorkoutFile.PlannedSetTarget first, PlannedWorkoutFile.PlannedSetTarget second) {
    return Objects.equals(first.metricType(), second.metricType())
        && Objects.equals(first.reps(), second.reps())
        && Objects.equals(first.repsLeft(), second.repsLeft())
        && Objects.equals(first.repsRight(), second.repsRight())
        && Objects.equals(first.repsMin(), second.repsMin())
        && Objects.equals(first.repsMax(), second.repsMax())
        && Objects.equals(first.seconds(), second.seconds())
        && Objects.equals(first.distanceFeet(), second.distanceFeet())
        && Objects.equals(first.percent(), second.percent())
        && Objects.equals(first.rpe(), second.rpe())
        && Objects.equals(first.accommodatingResistance(), second.accommodatingResistance())
        && first.deload() == second.deload();
  }

  static String formatPlannedSet(PlannedWorkoutFile.PlannedSetTarget target) {
    List<String> parts = new ArrayList<>();
    parts.add(formatPlannedMetric(target));
    if (target.percent() != null) {
      parts.add("@ " + target.percent() + "%");
    }
    if (target.rpe() != null) {
      parts.add("RPE " + String.format(Locale.ROOT, "%.1f", target.rpe()));
    }
    if (target.accommodatingResistance() != null
        && !target.accommodatingResistance().isBlank()
        && !"STRAIGHT".equals(target.accommodatingResistance())) {
      parts.add(target.accommodatingResistance());
    }
    if (target.deload()) {
      parts.add("deload");
    }
    return String.join(" ", parts);
  }

  private static String formatPlannedMetric(PlannedWorkoutFile.PlannedSetTarget target) {
    return switch (target.metricType()) {
      case "reps" -> target.reps() == null ? formatRepRange(target) : target.reps() + " reps";
      case "reps_lr" -> target.repsLeft() + "L/" + target.repsRight() + "R reps";
      case "reps_range" -> formatRepRange(target);
      case "time_seconds" -> target.seconds() + " sec";
      case "distance_feet" -> target.distanceFeet() + " ft";
      default -> "planned work";
    };
  }

  private static String formatRepRange(PlannedWorkoutFile.PlannedSetTarget target) {
    if (target.repsMin() != null && target.repsMax() != null) {
      return target.repsMin() + "-" + target.repsMax() + " reps";
    }
    if (target.repsMin() != null) {
      return target.repsMin() + "+ reps";
    }
    if (target.repsMax() != null) {
      return "up to " + target.repsMax() + " reps";
    }
    return "reps";
  }

  private static String joinList(List<String> values) {
    return String.join(", ", values);
  }
}
