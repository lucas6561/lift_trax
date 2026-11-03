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

  static String renderResumePanel(String ownerUserId) {
    return PlannedWorkoutResumeHtml.renderResumePanel(ownerUserId);
  }

  static String renderResumePage(String draftKey, String ownerUserId) {
    return PlannedWorkoutResumeHtml.renderResumePage(draftKey, ownerUserId);
  }

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
    return renderPage(workoutFile, weekNumber, dayOfWeek, localLifts, date, db, "local-user");
  }

  static String renderPage(
      PlannedWorkoutFile workoutFile,
      int weekNumber,
      String dayOfWeek,
      List<Lift> localLifts,
      LocalDate date,
      Database db,
      String ownerUserId) {
    PlannedWorkoutFile.PlannedWorkoutDay day =
        PlannedWorkoutSessionService.findDay(workoutFile, weekNumber, dayOfWeek);
    PlannedWorkoutHistory.Snapshot history =
        PlannedWorkoutHistory.load(db, day, localLifts.stream().map(Lift::name).toList());
    Map<String, PlannedWorkoutWarmups.WarmupPlan> warmups =
        PlannedWorkoutWarmups.forDay(day, history);
    StringBuilder html = new StringBuilder();
    html.append("<p><a href='/?tab=import-workout'>Back to Workouts</a></p>")
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
    appendSessionForm(
        html,
        workoutFile,
        weekNumber,
        dayOfWeek,
        localLifts,
        date,
        ownerUserId,
        day,
        history,
        warmups);
    appendScript(html);
    return html.toString();
  }

  private static void appendSessionForm(
      StringBuilder html,
      PlannedWorkoutFile workoutFile,
      int weekNumber,
      String dayOfWeek,
      List<Lift> localLifts,
      LocalDate date,
      String ownerUserId,
      PlannedWorkoutFile.PlannedWorkoutDay day,
      PlannedWorkoutHistory.Snapshot history,
      Map<String, PlannedWorkoutWarmups.WarmupPlan> warmups) {
    html.append(
            "<form method='post' action='/save-planned-workout-session' class='planned-session-form' data-draft-key='")
        .append(WebHtml.escapeHtml(draftKey(workoutFile, weekNumber, dayOfWeek, ownerUserId)))
        .append("' data-legacy-draft-key='")
        .append(WebHtml.escapeHtml(legacyDraftKey(workoutFile, weekNumber, dayOfWeek, ownerUserId)))
        .append("' data-workout-name='")
        .append(WebHtml.escapeHtml(workoutFile.metadata().name()))
        .append("' data-day-title='")
        .append(WebHtml.escapeHtml(day.title()))
        .append("' data-owner-scope='")
        .append(WebHtml.escapeHtml(draftPrefix(ownerUserId)))
        .append("'>")
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
            "<input type='hidden' name='savedSessionLoggedCount' class='js-session-saved-logged-count' value='0'/>")
        .append(
            "<input type='hidden' name='savedSessionSkippedExercises' class='js-session-saved-skipped-exercises' value='0'/>")
        .append(
            "<input type='hidden' name='savedSessionSkippedSets' class='js-session-saved-skipped-sets' value='0'/>")
        .append(
            "<label class='session-date'>Training date <input type='date' name='sessionDate' value='")
        .append(WebHtml.escapeHtml(date.toString()))
        .append("' required/></label>");
    appendBlockNavigation(html, day.blocks());
    html.append(ClasspathText.read("session-no-script.html"));
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
          history,
          warmups);
    }
    appendBlockActions(html, day.blocks().size());
    html.append("<noscript><button type='submit' formnovalidate>Save Workout</button></noscript>");
    html.append(
            "<button type='button' class='secondary js-session-sync-retry is-hidden'>Retry Finish</button>")
        .append(
            "<button type='button' class='secondary js-session-discard'>Discard Workout</button>")
        .append(
            "<p class='status muted session-device-status js-session-device-status' role='status'>Preparing device backup...</p>")
        .append(
            "<p class='status muted js-session-sync-status' role='status'>Not yet submitted to hosted history.</p>")
        .append("</form>");
  }

  static String renderSavedPage(
      PlannedWorkoutFile workoutFile,
      int weekNumber,
      String dayOfWeek,
      LocalDate date,
      PlannedWorkoutSessionService.SaveSummary summary) {
    return renderSavedPage(workoutFile, weekNumber, dayOfWeek, date, summary, 0, 0, 0);
  }

  static String renderSavedPage(
      PlannedWorkoutFile workoutFile,
      int weekNumber,
      String dayOfWeek,
      LocalDate date,
      PlannedWorkoutSessionService.SaveSummary summary,
      int previouslyLoggedCount,
      int previouslySkippedExercises,
      int previouslySkippedSets) {
    return renderSavedPage(
        workoutFile,
        weekNumber,
        dayOfWeek,
        date,
        summary,
        previouslyLoggedCount,
        previouslySkippedExercises,
        previouslySkippedSets,
        "local-user");
  }

  static String renderSavedPage(
      PlannedWorkoutFile workoutFile,
      int weekNumber,
      String dayOfWeek,
      LocalDate date,
      PlannedWorkoutSessionService.SaveSummary summary,
      int previouslyLoggedCount,
      int previouslySkippedExercises,
      int previouslySkippedSets,
      String ownerUserId) {
    PlannedWorkoutFile.PlannedWorkoutDay day =
        PlannedWorkoutSessionService.findDay(workoutFile, weekNumber, dayOfWeek);
    int totalLogged = previouslyLoggedCount + summary.loggedExecutionCount();
    int totalSkippedExercises = previouslySkippedExercises + summary.skippedExercises();
    int totalSkippedSets = previouslySkippedSets + summary.skippedSets();
    StringBuilder html = new StringBuilder();
    html.append("<h1>Workout Saved</h1>")
        .append("<p class='status success'>")
        .append(totalLogged)
        .append(totalLogged == 1 ? " execution" : " executions")
        .append(" logged for ")
        .append(WebHtml.escapeHtml(day.title()))
        .append(" on ")
        .append(WebHtml.escapeHtml(date.toString()))
        .append(".</p>");
    if (previouslyLoggedCount > 0) {
      html.append("<p class='muted'>Earlier block saves are included in this total.</p>");
    }
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
    if (totalSkippedExercises > 0 || totalSkippedSets > 0) {
      html.append("<p class='muted'>Skipped ")
          .append(totalSkippedExercises)
          .append(totalSkippedExercises == 1 ? " exercise" : " exercises")
          .append(" and ")
          .append(totalSkippedSets)
          .append(totalSkippedSets == 1 ? " set" : " sets")
          .append(".</p>");
    }
    html.append("<p><a class='compact-btn' href='/?tab=dashboard'>Back to Dashboard</a> ")
        .append(
            "<a class='compact-btn secondary' href='/?tab=import-workout'>Import Another Workout</a></p>");
    html.append("<div class='js-saved-session' data-draft-key='")
        .append(WebHtml.escapeHtml(draftKey(workoutFile, weekNumber, dayOfWeek, ownerUserId)))
        .append("'></div>")
        .append(ClasspathText.script("saved-session"));
    return html.toString();
  }

  static String draftPrefix(String ownerUserId) {
    return "lifttrax:planned-session:" + BrowserAccountScope.forUser(ownerUserId) + ":";
  }

  static String legacyDraftPrefix(String ownerUserId) {
    return "lifttrax:planned-session:"
        + Integer.toHexString(String.valueOf(ownerUserId).hashCode())
        + ":";
  }

  private static String legacyDraftKey(
      PlannedWorkoutFile workoutFile, int weekNumber, String dayOfWeek, String ownerUserId) {
    String sourceTime =
        workoutFile.source().generatedAt() == null ? "" : workoutFile.source().generatedAt();
    return legacyDraftPrefix(ownerUserId)
        + Integer.toHexString(
            (workoutFile.metadata().name() + "|" + sourceTime + "|" + weekNumber + "|" + dayOfWeek)
                .hashCode());
  }

  static String draftKey(
      PlannedWorkoutFile workoutFile, int weekNumber, String dayOfWeek, String ownerUserId) {
    String sourceTime =
        workoutFile.source().generatedAt() == null ? "" : workoutFile.source().generatedAt();
    return draftPrefix(ownerUserId)
        + Integer.toHexString(
            (workoutFile.metadata().name() + "|" + sourceTime + "|" + weekNumber + "|" + dayOfWeek)
                .hashCode());
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
        .append(
            "'></progress><button type='button' class='secondary js-session-skip-block'>Skip block</button><span class='muted js-session-block-save-status'></span></nav>");
  }

  private static void appendBlockActions(StringBuilder html, int blockCount) {
    html.append("<div class='session-block-actions' aria-label='Current block actions'>")
        .append(
            "<button type='button' class='secondary js-session-previous-block' disabled>Previous block</button>")
        .append("<button type='button' class='js-session-next-block'>")
        .append(blockCount == 1 ? "Submit Final Block" : "Submit Block &amp; Next")
        .append("</button></div>");
  }

  private static void appendBlock(
      StringBuilder html,
      PlannedWorkoutFile.PlannedWorkoutBlock block,
      int blockIndex,
      int blockCount,
      Set<String> localLiftNames,
      Map<String, String> localLiftNotes,
      LocalDate date,
      PlannedWorkoutHistory.Snapshot history,
      Map<String, PlannedWorkoutWarmups.WarmupPlan> warmups) {
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
          history,
          warmups.get(key));
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
      PlannedWorkoutHistory.Snapshot history,
      PlannedWorkoutWarmups.WarmupPlan warmup) {
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
    Set<String> selectableNames = new LinkedHashSet<>(localLiftNames);
    selectableNames.addAll(recommendedNames);
    boolean includeDeload =
        exercise.plannedSets().stream().anyMatch(PlannedWorkoutFile.PlannedSetTarget::deload);
    Map<String, PlannedWorkoutHistory.Summary> liftHistory = new HashMap<>();
    for (String name : selectableNames) {
      liftHistory.put(name, history.lookup(name, block.warmup(), includeDeload, 6));
    }
    appendLiftOptions(html, recommendedNames, localLiftNames, localLiftNotes, liftHistory, true);
    html.append("</optgroup>");
    Set<String> otherLocalLiftNames = new LinkedHashSet<>(localLiftNames);
    otherLocalLiftNames.removeAll(recommendedNames);
    if (!otherLocalLiftNames.isEmpty()) {
      html.append("<optgroup label='Other lifts in your library'>");
      appendLiftOptions(
          html, otherLocalLiftNames, localLiftNames, localLiftNotes, liftHistory, false);
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
    appendHistory(html, liftHistory.get(exercise.name()));
    PlannedWorkoutWarmupHtml.append(html, warmup);
    html.append(ClasspathText.read("session-plate-calculator.html"));
    List<PlannedWorkoutFile.PlannedSetTarget> plannedSets = exercise.plannedSets();
    appendTargets(html, plannedSets, exercise.name(), history);
    MetricSeed seed = firstMetricSeed(plannedSets, exercise.name(), history);
    html.append(
            "<div class='add-execution-form session-execution-widget js-session-execution-input'>")
        .append(
            "<div class='stacked-row'><button type='button' class='secondary compact-btn js-session-load-last'>Load last</button>"
                + "<span class='muted js-session-load-last-status' role='status'></span></div>")
        .append(
            ExecutionInputWidgetHtml.renderWorkAlong(
                prefill(block, exercise, seed, plannedSets, date),
                plannedSetDefaults(plannedSets, exercise.name(), history),
                key))
        .append("</div>");
    PlannedWorkoutNativeSession.append(
        html, key, localLiftNames, prefill(block, exercise, seed, plannedSets, date));
    html.append("</article>");
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
        suggestedSetCount(block, plannedSets),
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

  private static String suggestedSetCount(
      PlannedWorkoutFile.PlannedWorkoutBlock block,
      List<PlannedWorkoutFile.PlannedSetTarget> plannedSets) {
    if (block.rounds() != null) {
      return String.valueOf(block.rounds());
    }
    return String.valueOf(Math.max(1, plannedSets.size()));
  }

  private static List<ExecutionSetFormValues> plannedSetDefaults(
      List<PlannedWorkoutFile.PlannedSetTarget> plannedSets,
      String liftName,
      PlannedWorkoutHistory.Snapshot history) {
    if (plannedSets.stream().noneMatch(target -> target.weight() != null)) {
      return List.of();
    }
    return plannedSets.stream()
        .map(
            target -> {
              MetricSeed seed = MetricSeed.from(target, liftName, history);
              return new ExecutionSetFormValues(
                  seed.metricType(),
                  seed.metricValue(),
                  seed.metricLeft(),
                  seed.metricRight(),
                  seed.weight(),
                  "");
            })
        .toList();
  }

  private static void appendTargets(
      StringBuilder html,
      List<PlannedWorkoutFile.PlannedSetTarget> plannedSets,
      String liftName,
      PlannedWorkoutHistory.Snapshot history) {
    html.append(
        "<details class='session-targets session-guidance' data-pane='targets' open><summary>Planned Targets</summary><ol>");
    if (plannedSets.isEmpty()) {
      html.append("<li>Target: Enter completed work</li>");
    }
    for (PlannedWorkoutFile.PlannedSetTarget target : plannedSets) {
      html.append("<li>Target: ")
          .append(WebHtml.escapeHtml(PlannedWorkoutText.plannedSet(target)))
          .append(suggestionHtml(history, liftName, target))
          .append("</li>");
    }
    html.append("</ol></details>");
  }

  private static MetricSeed firstMetricSeed(
      List<PlannedWorkoutFile.PlannedSetTarget> plannedSets,
      String liftName,
      PlannedWorkoutHistory.Snapshot history) {
    if (plannedSets.isEmpty()) {
      return new MetricSeed("reps", "5", "5", "5", "");
    }
    return MetricSeed.from(plannedSets.get(0), liftName, history);
  }

  private static String suggestionHtml(
      PlannedWorkoutHistory.Snapshot history,
      String liftName,
      PlannedWorkoutFile.PlannedSetTarget target) {
    PlannedWorkoutHistory.WeightResolution resolution = history.resolveWeight(liftName, target);
    if (resolution.hasError()) {
      return " <span class='status error planned-load-error'><strong>Cannot calculate:</strong> "
          + WebHtml.escapeHtml(resolution.error())
          + "</span>";
    }
    if (resolution.weight().isBlank()) {
      return "";
    }
    String label = target.weight() == null ? "Suggested" : "Prescribed";
    return " <span class='muted'>"
        + label
        + ": "
        + WebHtml.escapeHtml(resolution.weight())
        + "</span>";
  }

  private static void appendHistory(StringBuilder html, PlannedWorkoutHistory.Summary summary) {
    html.append("<details class='session-guidance' data-pane='history'><summary>History</summary>");
    appendHistoryContent(html, summary);
    html.append("</details>");
  }

  private static void appendHistoryContent(
      StringBuilder html, PlannedWorkoutHistory.Summary summary) {
    html.append("<div class='session-history' aria-label='Exercise history' aria-live='polite'>");
    if (summary.unavailable()) {
      html.append("History unavailable.</div>");
      return;
    }
    if (summary.isEmpty()) {
      html.append("No history for this lift.</div>");
      return;
    }
    if (summary.last() != null) {
      html.append("<span><strong>Last:</strong> ")
          .append(WebHtml.escapeHtml(summary.last()))
          .append("</span>");
    }
    if (summary.bestOneRepMax() != null) {
      html.append("<span><strong>Best 1RM:</strong> ")
          .append(WebHtml.escapeHtml(summary.bestOneRepMax()))
          .append("</span>");
    }
    html.append("</div>");
  }

  private static void appendLiftOptions(
      StringBuilder html,
      Set<String> names,
      Set<String> localLiftNames,
      Map<String, String> localLiftNotes,
      Map<String, PlannedWorkoutHistory.Summary> liftHistory,
      boolean markUnavailable) {
    for (String name : names) {
      String notes = localLiftNotes.getOrDefault(name, "");
      PlannedWorkoutHistory.Summary summary = liftHistory.get(name);
      html.append("<option value='")
          .append(WebHtml.escapeHtml(name))
          .append("' data-lift-note='")
          .append(WebHtml.escapeHtml(notes))
          .append("' data-history-last='")
          .append(WebHtml.escapeHtml(summary.last() == null ? "" : summary.last()))
          .append("' data-history-best='")
          .append(
              WebHtml.escapeHtml(summary.bestOneRepMax() == null ? "" : summary.bestOneRepMax()))
          .append("' data-history-unavailable='")
          .append(summary.unavailable())
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
    html.append(ClasspathText.script("session-guidance"))
        .append(ClasspathText.script("planned-session"));
  }

  private record MetricSeed(
      String metricType, String metricValue, String metricLeft, String metricRight, String weight) {
    static MetricSeed from(
        PlannedWorkoutFile.PlannedSetTarget target,
        String liftName,
        PlannedWorkoutHistory.Snapshot history) {
      if (target == null) {
        return new MetricSeed("reps", "", "", "", "");
      }
      String weight = suggestedWeight(target, liftName, history);
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
        PlannedWorkoutFile.PlannedSetTarget target,
        String liftName,
        PlannedWorkoutHistory.Snapshot history) {
      return history.suggestedWeight(liftName, target);
    }
  }
}
