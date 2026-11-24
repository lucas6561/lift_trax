package com.lifttrax.cli;

import com.lifttrax.workout.PlannedWorkoutFile;
import com.lifttrax.workout.PlannedWorkoutHistory;
import com.lifttrax.workout.PlannedWorkoutText;
import java.util.List;
import java.util.Map;

/** Renders active-block navigation and the read-only adjacent-block preview. */
final class PlannedWorkoutSessionNavigationHtml {
  private PlannedWorkoutSessionNavigationHtml() {}

  static void appendNavigation(
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
        .append("'></progress>");
    if (blocks.size() > 1) {
      html.append(
          "<div class='session-block-peek-openers' aria-label='Preview adjacent blocks'>"
              + "<button type='button' class='secondary js-session-peek-previous' disabled>Peek previous</button>"
              + "<button type='button' class='secondary js-session-peek-next'>Peek next</button></div>");
    }
    html.append(
        "<button type='button' class='secondary js-session-skip-block'>Skip block</button>"
            + "<span class='muted js-session-block-save-status'></span></nav>");
  }

  static void appendActions(StringBuilder html, int blockCount) {
    html.append("<div class='session-block-actions' aria-label='Current block actions'>")
        .append(
            "<button type='button' class='secondary js-session-previous-block' disabled>Previous block</button>")
        .append("<button type='button' class='js-session-next-block'>")
        .append(blockCount == 1 ? "Submit Final Block" : "Submit Block &amp; Next")
        .append("</button></div>");
  }

  static void appendPeekDialog(
      StringBuilder html,
      List<PlannedWorkoutFile.PlannedWorkoutBlock> blocks,
      PlannedWorkoutHistory.Snapshot history,
      Map<String, PlannedWorkoutWarmups.WarmupPlan> warmups) {
    if (blocks.size() < 2) {
      return;
    }
    html.append(
            "<dialog class='session-block-peek js-session-block-peek' aria-labelledby='session-block-peek-title'>"
                + "<article><header class='session-block-peek-header'><div>"
                + "<span class='session-block-label js-session-block-peek-position'>Block 1 of ")
        .append(blocks.size())
        .append(
            "</span><h2 id='session-block-peek-title' class='js-session-block-peek-title' tabindex='-1'>"
                + "Block preview</h2></div>"
                + "<button type='button' class='secondary compact-btn js-session-block-peek-close'>Back to current block</button>"
                + "</header><p class='muted session-block-peek-explanation'>Read-only preview. Your active block and entered work stay exactly where they are.</p>"
                + "<div class='session-block-peek-body'>");
    for (int blockIndex = 0; blockIndex < blocks.size(); blockIndex++) {
      appendPeekPreview(html, blocks.get(blockIndex), blockIndex, history, warmups);
    }
    html.append(
        "</div><footer class='session-block-peek-actions'>"
            + "<button type='button' class='secondary js-session-block-peek-previous'>Previous preview</button>"
            + "<button type='button' class='secondary js-session-block-peek-close'>Back to current block</button>"
            + "<button type='button' class='secondary js-session-block-peek-next'>Next preview</button>"
            + "</footer></article></dialog>");
  }

  private static void appendPeekPreview(
      StringBuilder html,
      PlannedWorkoutFile.PlannedWorkoutBlock block,
      int blockIndex,
      PlannedWorkoutHistory.Snapshot history,
      Map<String, PlannedWorkoutWarmups.WarmupPlan> warmups) {
    html.append("<section class='session-block-peek-preview")
        .append(blockIndex == 0 ? "" : " is-hidden")
        .append("' data-session-block-peek-index='")
        .append(blockIndex)
        .append("' data-session-block-peek-title='")
        .append(WebHtml.escapeHtml(block.title()))
        .append("'><p class='muted'>")
        .append(WebHtml.escapeHtml(PlannedWorkoutText.blockMeta(block)))
        .append("</p>");
    PlannedWorkoutSessionHtml.appendNotes(html, block.notes());
    for (int exerciseIndex = 0; exerciseIndex < block.exercises().size(); exerciseIndex++) {
      PlannedWorkoutFile.PlannedExercise exercise = block.exercises().get(exerciseIndex);
      String key = block.order() + ":" + exerciseIndex;
      html.append("<article class='session-block-peek-exercise'><h3>")
          .append(WebHtml.escapeHtml(exercise.name()))
          .append("</h3>");
      if (!exercise.notes().isBlank()) {
        html.append("<p class='muted'><strong>Plan note:</strong> ")
            .append(WebHtml.escapeHtml(exercise.notes()))
            .append("</p>");
      }
      PlannedWorkoutWarmupHtml.append(html, warmups.get(key));
      PlannedWorkoutSessionHtml.appendTargets(
          html, exercise.plannedSets(), exercise.name(), history);
      html.append("</article>");
    }
    html.append("</section>");
  }
}
