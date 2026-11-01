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
      appendExercise(lines, block, exercise, history);
    }
    lines.add("");
  }

  private static void appendExercise(
      List<String> lines,
      PlannedWorkoutFile.PlannedWorkoutBlock block,
      PlannedWorkoutFile.PlannedExercise exercise,
      PlannedWorkoutHistory.Snapshot history) {
    lines.add(
        "- **"
            + exercise.name()
            + "** - "
            + PlannedWorkoutText.plannedSets(exercise.plannedSets()));
    appendExerciseDetails(lines, exercise);
    appendLoadGuidance(lines, exercise, history);
    appendHistory(lines, history.lookup(block, exercise));
    if (!exercise.substitutionOptions().isEmpty()) {
      lines.add("  - Swap options: " + String.join(", ", exercise.substitutionOptions()));
    }
  }

  private static void appendExerciseDetails(
      List<String> lines, PlannedWorkoutFile.PlannedExercise exercise) {
    String details = PlannedWorkoutText.exerciseDetails(exercise);
    if (!details.isBlank()) {
      lines.add("  - Details: " + details);
    }
    if (!exercise.notes().isBlank()) {
      lines.add("  - Notes: " + exercise.notes());
    }
  }

  private static void appendLoadGuidance(
      List<String> lines,
      PlannedWorkoutFile.PlannedExercise exercise,
      PlannedWorkoutHistory.Snapshot history) {
    String suggestions =
        PlannedWorkoutText.loadSuggestions(history, exercise.name(), exercise.plannedSets());
    if (!suggestions.isBlank()) {
      lines.add("  - Suggested: " + suggestions);
    }
    for (String error :
        PlannedWorkoutText.loadErrors(history, exercise.name(), exercise.plannedSets())) {
      lines.add("  - Load error: " + error);
    }
  }

  private static void appendHistory(List<String> lines, PlannedWorkoutHistory.Summary summary) {
    if (summary.last() != null) {
      lines.add("  - Last: " + summary.last());
    }
    if (summary.bestOneRepMax() != null) {
      lines.add("  - Best 1RM: " + summary.bestOneRepMax());
    }
    if (summary.unavailable()) {
      lines.add("  - History unavailable.");
    }
  }

  private static void appendNotes(List<String> lines, List<String> notes, String indent) {
    for (String note : notes) {
      lines.add(indent + "- Note: " + note);
    }
  }
}
