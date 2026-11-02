package com.lifttrax.cli;

import com.lifttrax.db.TrainingDataStore;
import com.lifttrax.workout.PlannedWorkoutFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Generates disposable DOM inputs using production renderers, never copied HTML fixtures. */
final class BrowserScriptFixtures {
  private BrowserScriptFixtures() {}

  static PlannedWorkoutFile withAssistanceBlock(PlannedWorkoutFile source) {
    return new PlannedWorkoutFile(
        source.schemaVersion(),
        source.metadata(),
        source.source(),
        source.weeks().stream()
            .map(
                week ->
                    new PlannedWorkoutFile.PlannedWorkoutWeek(
                        week.weekNumber(),
                        week.days().stream()
                            .map(
                                day ->
                                    new PlannedWorkoutFile.PlannedWorkoutDay(
                                        day.dayOfWeek(),
                                        day.title(),
                                        List.of(
                                            day.blocks().get(0),
                                            new PlannedWorkoutFile.PlannedWorkoutBlock(
                                                2,
                                                "Assistance",
                                                "accessory",
                                                null,
                                                false,
                                                day.blocks().get(0).exercises(),
                                                List.of())),
                                        day.notes()))
                            .toList()))
            .toList(),
        source.completedWorkouts());
  }

  static void write(TrainingDataStore store, PlannedWorkoutFile workout, Path output)
      throws Exception {
    Files.createDirectories(output);
    LocalDate date = LocalDate.of(2026, 11, 1);
    var lifts = store.listLifts();
    var week = workout.weeks().get(0);
    String day = week.days().get(0).dayOfWeek();
    page(output, "preview", PlannedWorkoutHtml.renderPage(workout, store));
    page(output, "work-along", PlannedWorkoutHtml.renderWorkAlongPage(workout));
    page(output, "workout-import", PlannedWorkoutHtml.renderImportPanel());
    page(output, "catalog-import", ExerciseCatalogImportHtml.render("", null, "", ""));
    page(output, "print", PlannedWorkoutPrintHtml.renderPage(workout, store));
    Files.writeString(
        output.resolve("execution-rows.html"),
        WebUiRenderer.renderExecutionRows(store, "Browser Squat"));
    page(
        output,
        "lift-import",
        LiftImportHtml.render(List.of("coach"), "coach", lifts, List.of(), "", ""));
    page(output, "resume-panel", PlannedWorkoutResumeHtml.renderResumePanel("browser-owner"));
    String key =
        PlannedWorkoutSessionHtml.draftKey(workout, week.weekNumber(), day, "browser-owner");
    page(output, "resume", PlannedWorkoutResumeHtml.renderResumePage(key, "browser-owner"));
    page(
        output,
        "session",
        PlannedWorkoutSessionHtml.renderPage(
            workout, week.weekNumber(), day, lifts, date, store, "browser-owner"));
    page(
        output,
        "saved",
        PlannedWorkoutSessionHtml.renderSavedPage(
            workout,
            week.weekNumber(),
            day,
            date,
            new PlannedWorkoutSessionService.SaveSummary(List.of(), 0, 0),
            0,
            0,
            0,
            "browser-owner"));
    for (String tab :
        List.of("add-execution", "executions", "exercises", "waves", "query", "import-workout")) {
      page(
          output,
          tab,
          WebUiRenderer.renderIndexBody(
              store,
              lifts,
              "",
              "",
              tab,
              "",
              "",
              WebUiRenderer.AddExecutionPrefill.empty(),
              date.minusDays(7),
              date,
              4,
              Map.of()));
    }
  }

  private static void page(Path output, String name, String body) throws Exception {
    String html =
        (body.stripLeading().startsWith("<!DOCTYPE") ? body : WebHtml.wrapPage(name, body))
            .replace(
                "<main class='container'>",
                "<main class='container' data-account-scope='browser-account'>");
    Files.writeString(output.resolve(name + ".html"), html);
  }
}
