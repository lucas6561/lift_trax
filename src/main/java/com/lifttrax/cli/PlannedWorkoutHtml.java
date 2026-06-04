package com.lifttrax.cli;

import com.lifttrax.db.Database;
import com.lifttrax.workout.PlannedWorkoutFile;
import com.lifttrax.workout.PlannedWorkoutHistory;
import com.lifttrax.workout.PlannedWorkoutJson;
import com.lifttrax.workout.PlannedWorkoutText;

/** Renders imported planned workout files for the lightweight web UI. */
final class PlannedWorkoutHtml {
  private PlannedWorkoutHtml() {}

  static String renderImportPanel() {
    return """
              <form method='post' class='planned-import-form'>
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
                    %s
                  </div>
                </div>
              </form>
              <script>
                (function () {
                  const fileInput = document.querySelector('#plannedWorkoutFile');
                  const jsonInput = document.querySelector('.js-planned-workout-json');
                  const outputButtons = Array.from(document.querySelectorAll('.js-planned-output'));
                  const fileName = document.querySelector('.js-planned-file-name');
                  const fileDetail = document.querySelector('.js-planned-file-detail');
                  const status = document.querySelector('.js-planned-file-status');

                  if (!fileInput || !jsonInput || outputButtons.length === 0 || !fileName || !fileDetail || !status) {
                    return;
                  }

                  function resetImport(message) {
                    jsonInput.value = '';
                    outputButtons.forEach((button) => button.disabled = true);
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
                        outputButtons.forEach((button) => button.disabled = false);
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
              """
        .formatted(renderOutputButtons(true));
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
    html.append(renderOutputActions(workoutFile));
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

  static String renderOutputActions(PlannedWorkoutFile workoutFile) {
    return "<form method='post' class='planned-output-form'>"
        + "<input type='hidden' name='plannedWorkoutJson' value='"
        + WebHtml.escapeHtml(writeWorkoutJson(workoutFile))
        + "'/>"
        + renderOutputButtons(false)
        + "</form>";
  }

  private static String renderOutputButtons(boolean disabled) {
    String disabledAttribute = disabled ? " disabled" : "";
    return """
        <div class='stacked-row planned-output-buttons'>
          <button type='submit' class='compact-btn js-planned-output' formaction='/planned-workout-preview'%s>App Preview</button>
          <button type='submit' class='secondary compact-btn js-planned-output' formaction='/planned-workout-print' formtarget='_blank'%s>Print View</button>
          <button type='submit' class='secondary compact-btn js-planned-output' formaction='/planned-workout-markdown'%s>Save As Markdown</button>
          <button type='submit' class='secondary compact-btn js-planned-output' formaction='/planned-workout-json'%s>Save As Workout JSON</button>
        </div>
        """
        .formatted(disabledAttribute, disabledAttribute, disabledAttribute, disabledAttribute);
  }

  private static void appendPlannedBlock(
      StringBuilder html, PlannedWorkoutFile.PlannedWorkoutBlock block, Database db) {
    html.append("<article class='planned-block'><header><h4>")
        .append(WebHtml.escapeHtml(block.title()))
        .append("</h4><p class='muted'>")
        .append(WebHtml.escapeHtml(PlannedWorkoutText.blockMeta(block)))
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
    String details = PlannedWorkoutText.exerciseDetails(exercise);
    if (!details.isBlank()) {
      html.append(" <span class='muted'>").append(WebHtml.escapeHtml(details)).append("</span>");
    }
    html.append("<div>")
        .append(WebHtml.escapeHtml(PlannedWorkoutText.plannedSets(exercise.plannedSets())))
        .append("</div>");
    if (!exercise.notes().isBlank()) {
      html.append("<div class='muted'>Notes: ")
          .append(WebHtml.escapeHtml(exercise.notes()))
          .append("</div>");
    }
    appendHistory(html, db, block, exercise);
    if (!exercise.substitutionOptions().isEmpty()) {
      html.append("<div class='muted'>Swap options: ")
          .append(WebHtml.escapeHtml(String.join(", ", exercise.substitutionOptions())))
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

    PlannedWorkoutHistory.Summary history = PlannedWorkoutHistory.lookup(db, block, exercise);
    if (history.unavailable()) {
      html.append("<div class='planned-history muted'>History unavailable.</div>");
      return;
    }
    if (history.isEmpty()) {
      return;
    }
    html.append("<div class='planned-history'>");
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
}
