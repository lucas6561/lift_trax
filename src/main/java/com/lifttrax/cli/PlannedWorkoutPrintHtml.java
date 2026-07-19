package com.lifttrax.cli;

import com.lifttrax.db.Database;
import com.lifttrax.models.Lift;
import com.lifttrax.workout.PlannedWorkoutFile;
import com.lifttrax.workout.PlannedWorkoutHistory;
import com.lifttrax.workout.PlannedWorkoutText;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Renders a compact, ink-friendly planned-workout print view. */
final class PlannedWorkoutPrintHtml {
  private PlannedWorkoutPrintHtml() {}

  static String renderPage(PlannedWorkoutFile workoutFile, Database db) {
    PlannedWorkoutHistory.Snapshot history = PlannedWorkoutHistory.load(db, workoutFile);
    Map<String, String> liftNotes = loadLiftNotes(db);
    StringBuilder html = new StringBuilder();
    html.append(
            """
            <!DOCTYPE html>
            <html lang='en'>
            <head>
              <meta charset='utf-8'/>
              <meta name='viewport' content='width=device-width, initial-scale=1'/>
              <title>Print - %s</title>
              <style>
                @page { size: auto; margin: 0.45in; }
                * { box-sizing: border-box; }
                body { max-width: 8.5in; margin: 0 auto; color: #111827; background: #fff; font-family: Arial, sans-serif; font-size: 10pt; line-height: 1.25; }
                h1, h2, h3, h4, p { margin: 0; }
                h1 { font-size: 20pt; }
                h2 { break-after: avoid-page; margin-bottom: 0.12in; padding-bottom: 0.04in; border-bottom: 2px solid #111827; font-size: 15pt; }
                h3 { break-after: avoid-page; margin-bottom: 0.08in; font-size: 12pt; }
                h4 { font-size: 10pt; }
                .print-actions { display: flex; gap: 0.1in; margin: 0.15in 0; }
                .print-actions button, .print-actions a { border: 1px solid #374151; border-radius: 4px; padding: 0.07in 0.12in; color: #111827; background: #f3f4f6; font: inherit; text-decoration: none; cursor: pointer; }
                .print-meta { margin: 0.08in 0 0.18in; color: #4b5563; }
                .print-week { break-before: page; }
                .print-week:first-of-type { break-before: auto; }
                .print-day { margin-bottom: 0.18in; }
                .print-block { break-inside: avoid; border: 1px solid #9ca3af; border-radius: 5px; margin-bottom: 0.09in; padding: 0.08in; }
                .print-block-header { display: flex; justify-content: space-between; gap: 0.12in; margin-bottom: 0.05in; border-bottom: 1px solid #d1d5db; padding-bottom: 0.04in; }
                .print-block-meta, .print-detail { color: #4b5563; font-size: 8.5pt; }
                .print-notes { margin: 0.04in 0; padding-left: 0.18in; }
                .print-exercise { display: grid; grid-template-columns: minmax(1.6in, 0.8fr) minmax(2.7in, 1.4fr); gap: 0.05in 0.12in; border-top: 1px dotted #d1d5db; padding: 0.055in 0; }
                .print-exercise:first-of-type { border-top: 0; }
                .print-target { font-weight: 700; }
                .print-extra { grid-column: 1 / -1; color: #374151; font-size: 8.5pt; }
                @media print { .print-actions { display: none; } }
              </style>
            </head>
            <body>
            """
                .formatted(WebHtml.escapeHtml(workoutFile.metadata().name())))
        .append(
            "<div class='print-actions'><button type='button' onclick='window.print()'>Print</button>")
        .append("<a href='/?tab=import-workout'>Back to LiftTrax</a></div>")
        .append("<header><h1>")
        .append(WebHtml.escapeHtml(workoutFile.metadata().name()))
        .append("</h1><p class='print-meta'>")
        .append(WebHtml.escapeHtml(workoutFile.metadata().description()))
        .append("<br/>Source: ")
        .append(WebHtml.escapeHtml(workoutFile.source().programName()))
        .append(" / ")
        .append(WebHtml.escapeHtml(workoutFile.source().generator()))
        .append(" &middot; Generated ")
        .append(WebHtml.escapeHtml(workoutFile.source().generatedAt()))
        .append("</p></header>");

    for (PlannedWorkoutFile.PlannedWorkoutWeek week : workoutFile.weeks()) {
      html.append("<section class='print-week'><h2>Week ")
          .append(week.weekNumber())
          .append("</h2>");
      for (PlannedWorkoutFile.PlannedWorkoutDay day : week.days()) {
        appendDay(html, day, history, liftNotes);
      }
      html.append("</section>");
    }
    return html.append("</body></html>").toString();
  }

  private static void appendDay(
      StringBuilder html,
      PlannedWorkoutFile.PlannedWorkoutDay day,
      PlannedWorkoutHistory.Snapshot history,
      Map<String, String> liftNotes) {
    html.append("<section class='print-day'><h3>")
        .append(WebHtml.escapeHtml(day.title()))
        .append("</h3>");
    appendProgramNotes(html, day.notes());
    for (PlannedWorkoutFile.PlannedWorkoutBlock block : day.blocks()) {
      html.append("<article class='print-block'><div class='print-block-header'><h4>")
          .append(WebHtml.escapeHtml(block.title()))
          .append("</h4><span class='print-block-meta'>")
          .append(WebHtml.escapeHtml(PlannedWorkoutText.blockMeta(block)))
          .append("</span></div>");
      appendProgramNotes(html, block.notes());
      for (PlannedWorkoutFile.PlannedExercise exercise : block.exercises()) {
        appendExercise(html, block, exercise, history, liftNotes);
      }
      html.append("</article>");
    }
    html.append("</section>");
  }

  private static void appendExercise(
      StringBuilder html,
      PlannedWorkoutFile.PlannedWorkoutBlock block,
      PlannedWorkoutFile.PlannedExercise exercise,
      PlannedWorkoutHistory.Snapshot history,
      Map<String, String> liftNotes) {
    html.append("<div class='print-exercise'><div><strong>")
        .append(WebHtml.escapeHtml(exercise.name()))
        .append("</strong>");
    String details = PlannedWorkoutText.exerciseDetails(exercise);
    if (!details.isBlank()) {
      html.append("<div class='print-detail'>")
          .append(WebHtml.escapeHtml(details))
          .append("</div>");
    }
    html.append("</div><div class='print-target'>")
        .append(WebHtml.escapeHtml(PlannedWorkoutText.plannedSets(exercise.plannedSets())))
        .append("</div>");

    PlannedWorkoutHistory.Summary summary = history.lookup(block, exercise);
    StringBuilder extra = new StringBuilder();
    appendExtra(extra, "Lift notes", liftNotes.get(liftKey(exercise.name())));
    appendExtra(extra, "Program notes", exercise.notes());
    appendExtra(
        extra,
        "Suggested",
        PlannedWorkoutText.loadSuggestions(history, exercise.name(), exercise.plannedSets()));
    appendExtra(extra, "Last", summary.last());
    appendExtra(extra, "Best 1RM", summary.bestOneRepMax());
    appendExtra(extra, "Swap options", String.join(", ", exercise.substitutionOptions()));
    if (summary.unavailable()) {
      appendExtra(extra, "History", "unavailable");
    }
    if (!extra.isEmpty()) {
      html.append("<div class='print-extra'>").append(extra).append("</div>");
    }
    html.append("</div>");
  }

  private static void appendExtra(StringBuilder html, String label, String value) {
    if (value == null || value.isBlank()) {
      return;
    }
    if (!html.isEmpty()) {
      html.append(" &middot; ");
    }
    html.append("<strong>")
        .append(WebHtml.escapeHtml(label))
        .append(":</strong> ")
        .append(WebHtml.escapeHtml(value));
  }

  private static void appendProgramNotes(StringBuilder html, java.util.List<String> notes) {
    if (notes.isEmpty()) {
      return;
    }
    html.append("<div class='print-notes'><strong>Program notes:</strong><ul>");
    for (String note : notes) {
      html.append("<li>").append(WebHtml.escapeHtml(note)).append("</li>");
    }
    html.append("</ul></div>");
  }

  private static Map<String, String> loadLiftNotes(Database db) {
    Map<String, String> notesByLift = new HashMap<>();
    if (db == null) {
      return notesByLift;
    }
    try {
      for (Lift lift : db.listLifts()) {
        notesByLift.put(liftKey(lift.name()), lift.notes());
      }
    } catch (Exception ignored) {
      // The imported program can still be printed when the local lift catalog is unavailable.
    }
    return notesByLift;
  }

  private static String liftKey(String name) {
    return name.trim().toLowerCase(Locale.ROOT);
  }
}
