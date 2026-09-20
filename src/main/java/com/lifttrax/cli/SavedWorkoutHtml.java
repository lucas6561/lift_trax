package com.lifttrax.cli;

import com.lifttrax.db.SavedWorkout;
import com.lifttrax.db.TrainingDataStore;

/** Renders the private workout library alongside the existing import controls. */
final class SavedWorkoutHtml {
  private SavedWorkoutHtml() {}

  static String renderLibrary(TrainingDataStore db) {
    StringBuilder html = new StringBuilder("<h2>Saved Workouts</h2>");
    html.append("<p class='muted'>Save a workout once, then open it here whenever you train.</p>");
    try {
      var workouts = db.listSavedWorkouts();
      if (workouts.isEmpty()) {
        html.append("<p>No saved workouts yet. Choose a file below and select Save Workout.</p>");
      }
      for (SavedWorkout workout : workouts) {
        String id = WebHtml.escapeHtml(workout.id());
        String urlId = WebUiRenderer.urlEncode(workout.id());
        html.append(
            """
            <article class='planned-block saved-workout'>
              <h3>%s</h3>
              <p class='muted'>Saved %s</p>
              <div class='stacked-row planned-output-buttons'>
                <a role='button' class='compact-btn' href='/saved-workout?id=%s&amp;view=work-along'>Work Along</a>
                <a role='button' class='secondary compact-btn' href='/saved-workout?id=%s'>Load Workout</a>
              </div>
              <details><summary>Manage workout</summary>
                <form method='post' action='/rename-saved-workout'>
                  <input type='hidden' name='savedWorkoutId' value='%s'/>
                  <label>Library name <input name='name' value='%s' maxlength='200' required/></label>
                  <button type='submit' class='secondary compact-btn'>Rename</button>
                </form>
                <form method='post' action='/delete-saved-workout'
                    onsubmit='return confirm("Delete this saved workout? Your logged training results will be kept.")'>
                  <input type='hidden' name='savedWorkoutId' value='%s'/>
                  <p class='muted'>Deleting a saved workout keeps your logged training results.</p>
                  <button type='submit' class='danger compact-btn'>Delete Workout</button>
                </form>
              </details>
            </article>
            """
                .formatted(
                    WebHtml.escapeHtml(workout.name()),
                    workout.createdAt().toLocalDate(),
                    urlId,
                    urlId,
                    id,
                    WebHtml.escapeHtml(workout.name()),
                    id));
      }
    } catch (Exception e) {
      html.append("<p class='status error'>Could not load saved workouts. Please try again.</p>");
    }
    return html.append("<h2>Upload Workout</h2>")
        .append(PlannedWorkoutHtml.renderImportPanel())
        .toString();
  }
}
