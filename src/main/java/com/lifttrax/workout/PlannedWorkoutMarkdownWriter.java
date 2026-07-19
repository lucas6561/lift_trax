package com.lifttrax.workout;

import com.lifttrax.db.Database;
import java.util.ArrayList;
import java.util.List;

/** Creates a portable Markdown view from any planned-workout file. */
public final class PlannedWorkoutMarkdownWriter {
  private PlannedWorkoutMarkdownWriter() {}

  public static List<String> createMarkdown(PlannedWorkoutFile workoutFile, Database db) {
    PlannedWorkoutHistory.Snapshot history = PlannedWorkoutHistory.load(db, workoutFile);
    List<String> lines = new ArrayList<>();
    lines.add("# " + workoutFile.metadata().name());
    if (!workoutFile.metadata().description().isBlank()) {
      lines.add("");
      lines.add(workoutFile.metadata().description());
    }
    lines.add("");
    lines.add(
        "> Source: "
            + workoutFile.source().programName()
            + " / "
            + workoutFile.source().generator()
            + " | Generated: "
            + workoutFile.source().generatedAt());
    lines.add("");

    for (PlannedWorkoutFile.PlannedWorkoutWeek week : workoutFile.weeks()) {
      lines.add("## Week " + week.weekNumber());
      lines.add("");
      for (PlannedWorkoutFile.PlannedWorkoutDay day : week.days()) {
        lines.add("### " + day.title());
        appendNotes(lines, day.notes(), "");
        lines.add("");
        for (PlannedWorkoutFile.PlannedWorkoutBlock block : day.blocks()) {
          appendBlock(lines, block, history);
        }
      }
    }
    return lines;
  }

  private static void appendBlock(
      List<String> lines,
      PlannedWorkoutFile.PlannedWorkoutBlock block,
      PlannedWorkoutHistory.Snapshot history) {
    lines.add("#### " + block.title());
    lines.add("*" + PlannedWorkoutText.blockMeta(block) + "*");
    appendNotes(lines, block.notes(), "");
    lines.add("");
    for (PlannedWorkoutFile.PlannedExercise exercise : block.exercises()) {
      lines.add(
          "- **"
              + exercise.name()
              + "** - "
              + PlannedWorkoutText.plannedSets(exercise.plannedSets()));
      String details = PlannedWorkoutText.exerciseDetails(exercise);
      if (!details.isBlank()) {
        lines.add("  - Details: " + details);
      }
      if (!exercise.notes().isBlank()) {
        lines.add("  - Notes: " + exercise.notes());
      }
      String suggestions =
          PlannedWorkoutText.loadSuggestions(history, exercise.name(), exercise.plannedSets());
      if (!suggestions.isBlank()) {
        lines.add("  - Suggested: " + suggestions);
      }
      PlannedWorkoutHistory.Summary summary = history.lookup(block, exercise);
      if (summary.last() != null) {
        lines.add("  - Last: " + summary.last());
      }
      if (summary.bestOneRepMax() != null) {
        lines.add("  - Best 1RM: " + summary.bestOneRepMax());
      }
      if (summary.unavailable()) {
        lines.add("  - History unavailable.");
      }
      if (!exercise.substitutionOptions().isEmpty()) {
        lines.add("  - Swap options: " + String.join(", ", exercise.substitutionOptions()));
      }
    }
    lines.add("");
  }

  private static void appendNotes(List<String> lines, List<String> notes, String indent) {
    for (String note : notes) {
      lines.add(indent + "- Note: " + note);
    }
  }
}
