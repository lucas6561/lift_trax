package com.lifttrax.cli;

import static com.lifttrax.cli.WebAccountRoutes.userIdFor;
import static com.lifttrax.cli.WebExecutionRequestParser.*;
import static com.lifttrax.cli.WebServerCli.*;

import com.lifttrax.db.TrainingDataStore;
import com.lifttrax.db.TrainingDataStoreProvider;
import com.lifttrax.models.Lift;
import com.lifttrax.workout.PlannedWorkoutFile;
import com.lifttrax.workout.PlannedWorkoutJson;
import com.lifttrax.workout.PlannedWorkoutMarkdownWriter;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Handles saved and planned workout requests. */
final class WebWorkoutRoutes {
  private WebWorkoutRoutes() {}

  static void handleSaveWorkout(HttpExchange exchange, TrainingDataStoreProvider rootDb)
      throws IOException {
    try {
      TrainingDataStore db = databaseFor(exchange, rootDb);
      Map<String, String> form = parseForm(exchange.getRequestBody());
      // Validate with the same versioned reader used by preview and Work Along before persisting.
      PlannedWorkoutFile workout =
          PlannedWorkoutJson.readString(form.getOrDefault("plannedWorkoutJson", ""));
      db.saveWorkout(workout.metadata().name(), PlannedWorkoutJson.writeString(workout));
      savedWorkoutRedirect(exchange, "success", "Workout saved. Open it below whenever you train.");
    } catch (Exception e) {
      savedWorkoutRedirect(exchange, "error", "Could not save workout: " + e.getMessage());
    }
  }

  static void handleSavedWorkout(HttpExchange exchange, TrainingDataStoreProvider rootDb)
      throws IOException {
    try {
      TrainingDataStore db = databaseFor(exchange, rootDb);
      Map<String, String> query = parseQuery(exchange.getRequestURI());
      PlannedWorkoutFile workout =
          PlannedWorkoutJson.readString(db.getSavedWorkoutJson(query.getOrDefault("id", "")));
      String body =
          "work-along".equals(query.get("view"))
              ? PlannedWorkoutHtml.renderWorkAlongPage(workout)
              : PlannedWorkoutHtml.renderPage(workout, db);
      sendHtml(
          exchange,
          ApplicationShellHtml.wrapPage(
              workout.metadata().name(),
              ApplicationShellHtml.Section.WORKOUTS,
              "Workouts",
              "/?tab=import-workout",
              body));
    } catch (Exception e) {
      savedWorkoutRedirect(exchange, "error", "Could not load workout: " + e.getMessage());
    }
  }

  static void handleManageSavedWorkout(
      HttpExchange exchange, TrainingDataStoreProvider rootDb, boolean delete) throws IOException {
    try {
      TrainingDataStore db = databaseFor(exchange, rootDb);
      Map<String, String> form = parseForm(exchange.getRequestBody());
      String id = form.getOrDefault("savedWorkoutId", "");
      if (delete) {
        db.deleteSavedWorkout(id);
      } else {
        db.renameSavedWorkout(id, form.getOrDefault("name", ""));
      }
      savedWorkoutRedirect(
          exchange,
          "success",
          delete
              ? "Saved workout deleted. Your logged training results were kept."
              : "Workout renamed.");
    } catch (Exception e) {
      savedWorkoutRedirect(exchange, "error", "Could not update saved workout: " + e.getMessage());
    }
  }

  private static void savedWorkoutRedirect(HttpExchange exchange, String type, String message)
      throws IOException {
    redirect(
        exchange,
        "/?tab=import-workout&statusType=" + type + "&status=" + WebUiRenderer.urlEncode(message));
  }

  static void handlePlannedWorkoutPreview(HttpExchange exchange, TrainingDataStoreProvider rootDb)
      throws IOException {
    String method = exchange.getRequestMethod();
    if (!"POST".equalsIgnoreCase(method) && !"GET".equalsIgnoreCase(method)) {
      sendText(exchange, 405, "Method Not Allowed");
      return;
    }

    try {
      TrainingDataStore db = databaseFor(exchange, rootDb);
      Map<String, String> values =
          "POST".equalsIgnoreCase(method)
              ? parseForm(exchange.getRequestBody())
              : parseQuery(exchange.getRequestURI());
      PlannedWorkoutFile workoutFile = loadPlannedWorkout(values);
      sendHtml(
          exchange,
          ApplicationShellHtml.wrapPage(
              workoutFile.metadata().name(),
              ApplicationShellHtml.Section.WORKOUTS,
              "Workouts",
              "/?tab=import-workout",
              PlannedWorkoutHtml.renderPage(workoutFile, db)));
    } catch (Exception e) {
      sendHtml(
          exchange,
          ApplicationShellHtml.wrapPage(
              "Planned Workout Import Error",
              ApplicationShellHtml.Section.WORKOUTS,
              "<p><a href='/?tab=import-workout'>Back to Workouts</a></p>"
                  + "<h1>Import Error</h1><p class='status error'>"
                  + WebHtml.escapeHtml(e.getMessage())
                  + "</p>"));
    }
  }

  private static PlannedWorkoutFile loadPlannedWorkout(Map<String, String> values)
      throws IOException {
    String pasted = values.getOrDefault("plannedWorkoutJson", "").trim();
    if (!pasted.isBlank()) {
      return PlannedWorkoutJson.readString(pasted);
    }

    String path = values.getOrDefault("plannedWorkoutPath", "").trim();
    if (!path.isBlank()) {
      return PlannedWorkoutJson.readPath(Path.of(path));
    }

    throw new IllegalArgumentException("Paste a workout file or enter a file path.");
  }

  static void handlePlannedWorkoutWorkAlong(HttpExchange exchange) throws IOException {
    if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
      sendText(exchange, 405, "Method Not Allowed");
      return;
    }

    try {
      PlannedWorkoutFile workoutFile = loadPlannedWorkout(parseForm(exchange.getRequestBody()));
      sendHtml(
          exchange,
          ApplicationShellHtml.wrapPage(
              "Work Along",
              ApplicationShellHtml.Section.TRAIN,
              "Workouts",
              "/?tab=import-workout",
              PlannedWorkoutHtml.renderWorkAlongPage(workoutFile)));
    } catch (Exception e) {
      sendPlannedWorkoutError(exchange, "Could not prepare workout", e);
    }
  }

  static void handlePlannedWorkoutPrint(HttpExchange exchange, TrainingDataStoreProvider rootDb)
      throws IOException {
    if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
      sendText(exchange, 405, "Method Not Allowed");
      return;
    }

    try {
      TrainingDataStore db = databaseFor(exchange, rootDb);
      PlannedWorkoutFile workoutFile = loadPlannedWorkout(parseForm(exchange.getRequestBody()));
      sendHtml(exchange, PlannedWorkoutPrintHtml.renderPage(workoutFile, db));
    } catch (Exception e) {
      sendPlannedWorkoutError(exchange, "Could not create print view", e);
    }
  }

  static void handlePlannedWorkoutMarkdown(HttpExchange exchange, TrainingDataStoreProvider rootDb)
      throws IOException {
    if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
      sendText(exchange, 405, "Method Not Allowed");
      return;
    }

    try {
      TrainingDataStore db = databaseFor(exchange, rootDb);
      PlannedWorkoutFile workoutFile = loadPlannedWorkout(parseForm(exchange.getRequestBody()));
      String markdown =
          String.join("\n", PlannedWorkoutMarkdownWriter.createMarkdown(workoutFile, db));
      sendDownload(
          exchange,
          "text/markdown; charset=utf-8",
          downloadName(workoutFile, ".md"),
          markdown + "\n");
    } catch (Exception e) {
      sendPlannedWorkoutError(exchange, "Could not create Markdown", e);
    }
  }

  static void handlePlannedWorkoutJson(HttpExchange exchange) throws IOException {
    if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
      sendText(exchange, 405, "Method Not Allowed");
      return;
    }

    try {
      PlannedWorkoutFile workoutFile = loadPlannedWorkout(parseForm(exchange.getRequestBody()));
      sendDownload(
          exchange,
          "application/json; charset=utf-8",
          downloadName(workoutFile, ".json"),
          PlannedWorkoutJson.writeString(workoutFile));
    } catch (Exception e) {
      sendPlannedWorkoutError(exchange, "Could not save workout JSON", e);
    }
  }

  private static String downloadName(PlannedWorkoutFile workoutFile, String extension) {
    String slug =
        workoutFile
            .metadata()
            .name()
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("(^-|-$)", "");
    return (slug.isBlank() ? "planned-workout" : slug) + extension;
  }

  static void handlePlannedWorkoutSession(HttpExchange exchange, TrainingDataStoreProvider rootDb)
      throws IOException {
    if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
      if (WebAuth.currentUser(exchange).isPresent()) {
        prepareAccountLabel(exchange, rootDb);
      }
      Map<String, String> query = parseQuery(exchange.getRequestURI());
      String draftKey = query.getOrDefault("draft", "").trim();
      sendHtml(
          exchange,
          ApplicationShellHtml.wrapPage(
              "Resume Workout",
              ApplicationShellHtml.Section.TRAIN,
              PlannedWorkoutSessionHtml.renderResumePage(draftKey, userIdFor(exchange))));
      return;
    }
    if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
      sendText(exchange, 405, "Method Not Allowed");
      return;
    }

    try {
      TrainingDataStore db = databaseFor(exchange, rootDb);
      Map<String, String> form = parseForm(exchange.getRequestBody());
      PlannedWorkoutFile workoutFile = loadPlannedWorkout(form);
      int weekNumber = parsePositiveInt(form.getOrDefault("weekNumber", ""), "Week number");
      String dayOfWeek = form.getOrDefault("dayOfWeek", "").trim();
      List<Lift> lifts = new ArrayList<>(db.listLifts());
      lifts.sort(Comparator.comparing(Lift::name));
      sendHtml(
          exchange,
          ApplicationShellHtml.wrapPage(
              "Train " + dayOfWeek,
              ApplicationShellHtml.Section.TRAIN,
              PlannedWorkoutSessionHtml.renderPage(
                  workoutFile,
                  weekNumber,
                  dayOfWeek,
                  lifts,
                  LocalDate.now(),
                  db,
                  userIdFor(exchange))));
    } catch (Exception e) {
      sendPlannedWorkoutError(exchange, "Could not start workout", e);
    }
  }

  static void handleSavePlannedWorkoutSession(
      HttpExchange exchange, TrainingDataStoreProvider rootDb) throws IOException {
    if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
      sendText(exchange, 405, "Method Not Allowed");
      return;
    }

    try {
      TrainingDataStore db = databaseFor(exchange, rootDb);
      Map<String, String> form = parseForm(exchange.getRequestBody());
      PlannedWorkoutFile workoutFile = loadPlannedWorkout(form);
      int weekNumber = parsePositiveInt(form.getOrDefault("weekNumber", ""), "Week number");
      String dayOfWeek = form.getOrDefault("dayOfWeek", "").trim();
      LocalDate date =
          LocalDate.parse(form.getOrDefault("sessionDate", LocalDate.now().toString()));
      int previouslyLoggedCount =
          parseNonNegativeInt(form.getOrDefault("savedSessionLoggedCount", "0"));
      int previouslySkippedExercises =
          parseNonNegativeInt(form.getOrDefault("savedSessionSkippedExercises", "0"));
      int previouslySkippedSets =
          parseNonNegativeInt(form.getOrDefault("savedSessionSkippedSets", "0"));
      PlannedWorkoutSessionService.SaveSummary summary =
          PlannedWorkoutSessionService.saveSubmittedResults(
              db,
              workoutFile,
              weekNumber,
              dayOfWeek,
              date,
              form.getOrDefault("sessionResultsJson", "[]"),
              false,
              form.getOrDefault("submissionId", ""));
      sendHtml(
          exchange,
          ApplicationShellHtml.wrapPage(
              "Workout Saved",
              ApplicationShellHtml.Section.TRAIN,
              PlannedWorkoutSessionHtml.renderSavedPage(
                  workoutFile,
                  weekNumber,
                  dayOfWeek,
                  date,
                  summary,
                  previouslyLoggedCount,
                  previouslySkippedExercises,
                  previouslySkippedSets,
                  userIdFor(exchange))));
    } catch (Exception e) {
      sendPlannedWorkoutError(exchange, "Could not save workout", e);
    }
  }

  static void handleSavePlannedWorkoutBlock(HttpExchange exchange, TrainingDataStoreProvider rootDb)
      throws IOException {
    if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
      sendText(exchange, 405, "Method Not Allowed");
      return;
    }

    try {
      TrainingDataStore db = databaseFor(exchange, rootDb);
      Map<String, String> form = parseForm(exchange.getRequestBody());
      PlannedWorkoutFile workoutFile = loadPlannedWorkout(form);
      int weekNumber = parsePositiveInt(form.getOrDefault("weekNumber", ""), "Week number");
      String dayOfWeek = form.getOrDefault("dayOfWeek", "").trim();
      LocalDate date =
          LocalDate.parse(form.getOrDefault("sessionDate", LocalDate.now().toString()));
      PlannedWorkoutSessionService.SaveSummary summary =
          PlannedWorkoutSessionService.saveSubmittedResults(
              db,
              workoutFile,
              weekNumber,
              dayOfWeek,
              date,
              form.getOrDefault("sessionResultsJson", "[]"),
              false,
              form.getOrDefault("submissionId", ""));
      sendJson(
          exchange,
          200,
          Map.of(
              "loggedExecutionCount",
              summary.loggedExecutionCount(),
              "skippedExercises",
              summary.skippedExercises(),
              "skippedSets",
              summary.skippedSets()));
    } catch (Exception e) {
      sendJson(exchange, 400, Map.of("error", e.getMessage()));
    }
  }

  private static void sendPlannedWorkoutError(
      HttpExchange exchange, String heading, Exception error) throws IOException {
    sendHtml(
        exchange,
        ApplicationShellHtml.wrapPage(
            heading,
            ApplicationShellHtml.Section.WORKOUTS,
            "<p><a href='/?tab=import-workout'>Back to Workouts</a></p>"
                + "<h1>"
                + WebHtml.escapeHtml(heading)
                + "</h1><p class='status error'>"
                + WebHtml.escapeHtml(error.getMessage())
                + "</p><p class='muted'>Use your browser back button to keep editing the session.</p>"));
  }
}
