package com.lifttrax.cli;

import static com.lifttrax.cli.WebExecutionRequestParser.*;
import static com.lifttrax.cli.WebServerCli.*;

import com.lifttrax.db.AccountProfile;
import com.lifttrax.db.TrainingDataStore;
import com.lifttrax.db.TrainingDataStoreProvider;
import com.lifttrax.models.Lift;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Handles account, import, and index-page requests. */
final class WebAccountRoutes {
  private WebAccountRoutes() {}

  static String userIdFor(HttpExchange exchange) {
    return WebAuth.currentUser(exchange).map(WebAuth.User::id).orElse("local-user");
  }

  static TrainingDataStore databaseFor(HttpExchange exchange, TrainingDataStoreProvider db)
      throws Exception {
    Optional<WebAuth.User> currentUser = WebAuth.currentUser(exchange);
    if (currentUser.isEmpty()) {
      return db.forUser("local-user");
    }
    WebAuth.User user = currentUser.get();
    prepareAccountLabel(exchange, db);
    return db.forUser(user.id());
  }

  static void prepareAccountLabel(HttpExchange exchange, TrainingDataStoreProvider db)
      throws IOException {
    WebAuth.User user = WebAuth.currentUser(exchange).orElseThrow();
    try {
      AccountProfile account = db.accountFor(user.id(), user.email());
      WebAuth.setAccountLabel(exchange, account.displayLabel());
    } catch (Exception e) {
      throw new IOException("Could not load the signed-in LiftTrax account.", e);
    }
  }

  static void handleAccount(HttpExchange exchange, TrainingDataStoreProvider db)
      throws IOException {
    handleAccount(exchange, db, false);
  }

  static void handleAccount(
      HttpExchange exchange, TrainingDataStoreProvider db, boolean localPasswords)
      throws IOException {
    WebAuth.User user = WebAuth.currentUser(exchange).orElseThrow();
    try {
      AccountProfile account = db.accountFor(user.id(), user.email());
      String message = "";
      String messageType = "success";
      if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
        Map<String, String> form = parseForm(exchange.getRequestBody());
        try {
          if ("lift-sharing".equals(form.get("action"))) {
            db.setLiftCatalogShared(user.id(), "true".equals(form.get("shareLifts")));
            message = "Lift sharing saved.";
          } else {
            account = db.updateUsername(user.id(), form.getOrDefault("username", ""));
            message = "Username saved.";
          }
        } catch (IllegalArgumentException e) {
          message = e.getMessage();
          messageType = "error";
        }
      }
      WebAuth.setAccountLabel(exchange, account.displayLabel());
      sendHtml(
          exchange,
          AccountPageHtml.render(
              account,
              user,
              message,
              messageType,
              db.isLiftCatalogShared(user.id()),
              localPasswords));
    } catch (Exception e) {
      sendHtml(
          exchange,
          ApplicationShellHtml.wrapPage(
              "Account Error",
              ApplicationShellHtml.Section.ACCOUNT,
              "<h1>Account Error</h1><p class='status error'>Account settings are temporarily unavailable.</p>"));
    }
  }

  static void handleExerciseCatalogImport(HttpExchange exchange, TrainingDataStoreProvider rootDb)
      throws IOException {
    ExerciseCatalogImportResponse.handle(exchange, rootDb);
  }

  static void handleImportLifts(HttpExchange exchange, TrainingDataStoreProvider rootDb)
      throws IOException {
    String userId = WebAuth.currentUser(exchange).orElseThrow().id();
    try {
      TrainingDataStore db = databaseFor(exchange, rootDb);
      Map<String, String> values =
          "POST".equalsIgnoreCase(exchange.getRequestMethod())
              ? parseForm(exchange.getRequestBody())
              : parseQuery(exchange.getRequestURI());
      String source = values.getOrDefault("source", "").trim().toLowerCase(Locale.ROOT);
      String message = "";
      String messageType = "success";
      if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
        List<String> names =
            values.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("lift_"))
                .map(Map.Entry::getValue)
                .distinct()
                .toList();
        try {
          int imported = rootDb.importSharedLifts(userId, source, names);
          redirect(
              exchange,
              "/?tab=add-execution&statusType=success&status="
                  + WebUiRenderer.urlEncode(
                      "Imported "
                          + imported
                          + " lifts. Skipped "
                          + (names.size() - imported)
                          + " already in your list."));
          return;
        } catch (IllegalArgumentException e) {
          message = e.getMessage();
          messageType = "error";
        }
      }
      List<Lift> shared = List.of();
      if (!source.isBlank()) {
        try {
          shared = rootDb.sharedLifts(userId, source);
        } catch (IllegalArgumentException e) {
          message = e.getMessage();
          messageType = "error";
        }
      }
      sendHtml(
          exchange,
          LiftImportHtml.render(
              rootDb.sharedLiftUsers(userId),
              source,
              shared,
              db.listLifts(),
              message,
              messageType));
    } catch (Exception e) {
      sendHtml(
          exchange,
          ApplicationShellHtml.wrapPage(
              "Import Lifts",
              ApplicationShellHtml.Section.HISTORY,
              "Exercise Catalog",
              "/?tab=exercises",
              "<h1>Import Lifts</h1><p class='status error'>Could not import lifts. Please try again.</p>"
                  + "<p><a href='/import-lifts'>Back to import</a></p>"));
    }
  }

  static void handleIndex(HttpExchange exchange, TrainingDataStoreProvider rootDb)
      throws IOException {
    if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
      sendText(exchange, 405, "Method Not Allowed");
      return;
    }

    try {
      TrainingDataStore db = databaseFor(exchange, rootDb);
      Map<String, String> query = parseQuery(exchange.getRequestURI());
      String search = query.getOrDefault("q", "").trim().toLowerCase(Locale.ROOT);
      String queryLift = query.getOrDefault("queryLift", "").trim();
      String activeTab = WebUiRenderer.normalizeTab(query.getOrDefault("tab", "dashboard"));
      String statusMessage = query.getOrDefault("status", "").trim();
      String statusType = query.getOrDefault("statusType", "").trim();
      int waveWeeks = parseBoundedInt(query.get("waveWeeks"), 7, 1, 24);
      LocalDate today = LocalDate.now();
      LocalDate lastWeekStart = parseDateOrDefault(query.get("lastWeekStart"), today.minusDays(6));
      LocalDate lastWeekEnd = parseDateOrDefault(query.get("lastWeekEnd"), today);
      String lastWeekNav = query.getOrDefault("lastWeekNav", "").trim();
      if (!lastWeekNav.isBlank()) {
        switch (lastWeekNav) {
          case "prev" -> {
            lastWeekStart = lastWeekStart.minusDays(7);
            lastWeekEnd = lastWeekEnd.minusDays(7);
          }
          case "next" -> {
            lastWeekStart = lastWeekStart.plusDays(7);
            lastWeekEnd = lastWeekEnd.plusDays(7);
          }
          case "current" -> {
            lastWeekStart = today.minusDays(6);
            lastWeekEnd = today;
          }
          case "last" -> {
            lastWeekEnd = today.minusDays(7);
            lastWeekStart = lastWeekEnd.minusDays(6);
          }
          default -> {}
        }
      }
      WebUiRenderer.AddExecutionPrefill prefill = parsePrefill(query);

      List<Lift> lifts;
      try {
        lifts = new ArrayList<>(WebUiRenderer.loadIndexLifts(db, activeTab));
        lifts.sort(Comparator.comparing(Lift::name));
      } catch (Exception listError) {
        lifts = List.of();
        statusType = "error";
        statusMessage = "No lifts table found. Initialize or provide a populated database.";
      }

      String body =
          WebUiRenderer.renderIndexBody(
              db,
              lifts,
              search,
              queryLift,
              activeTab,
              statusMessage,
              statusType,
              prefill,
              lastWeekStart,
              lastWeekEnd,
              waveWeeks,
              query,
              PlannedWorkoutSessionHtml.renderResumePanel(userIdFor(exchange)));
      ApplicationShellHtml.Section section = ApplicationShellHtml.Section.forTab(activeTab);
      sendHtml(exchange, ApplicationShellHtml.wrapPage(section.label(), section, body));
    } catch (Exception e) {
      String message =
          e.getMessage() == null
              ? e.getClass().getName()
              : e.getClass().getName() + ": " + e.getMessage();
      sendHtml(
          exchange,
          ApplicationShellHtml.wrapPage(
              "Error",
              ApplicationShellHtml.Section.TODAY,
              "<h1>Error</h1><pre>" + WebHtml.escapeHtml(message) + "</pre>"));
    }
  }
}
