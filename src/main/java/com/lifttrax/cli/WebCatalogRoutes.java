package com.lifttrax.cli;

import static com.lifttrax.cli.WebServerCli.*;

import com.lifttrax.db.TrainingDataStore;
import com.lifttrax.db.TrainingDataStoreProvider;
import com.lifttrax.models.LiftRegion;
import com.lifttrax.models.LiftType;
import com.lifttrax.models.Muscle;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Handles exercise catalog mutations. */
final class WebCatalogRoutes {
  private WebCatalogRoutes() {}

  static void handleUpdateLift(HttpExchange exchange, TrainingDataStoreProvider rootDb)
      throws IOException {
    if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
      sendText(exchange, 405, "Method Not Allowed");
      return;
    }

    String redirectBase = "/?tab=executions";
    try {
      TrainingDataStore db = databaseFor(exchange, rootDb);
      Map<String, String> form = parseForm(exchange.getRequestBody());
      redirectBase = buildExecutionRedirectBase(form);
      String currentName = form.getOrDefault("currentName", "").trim();
      String newName = form.getOrDefault("name", "").trim();
      if (currentName.isBlank() || newName.isBlank()) {
        redirect(exchange, redirectBase + "&statusType=error&status=Lift%20name%20is%20required");
        return;
      }

      LiftRegion region = LiftRegion.fromString(form.getOrDefault("region", "UPPER"));
      LiftType main = LiftType.fromDbValue(form.getOrDefault("main", "none"));
      List<Muscle> muscles = parseMuscles(form.get("muscles"));
      String notes = form.getOrDefault("notes", "");

      db.updateLift(currentName, newName, region, main, muscles, notes);
      redirect(
          exchange,
          redirectBase
              + "&statusType=success&status="
              + WebUiRenderer.urlEncode("Updated lift: " + newName));
    } catch (Exception e) {
      redirect(
          exchange,
          redirectBase
              + "&statusType=error&status="
              + WebUiRenderer.urlEncode("Failed to update lift: " + e.getMessage()));
    }
  }

  static String buildExecutionRedirectBase(Map<String, String> form) {
    String tab = form.getOrDefault("tab", "executions").trim();
    StringBuilder redirectBase = new StringBuilder("/?tab=");
    redirectBase.append(WebUiRenderer.urlEncode(tab.isBlank() ? "executions" : tab));
    String lastWeekStart = form.getOrDefault("lastWeekStart", "").trim();
    String lastWeekEnd = form.getOrDefault("lastWeekEnd", "").trim();
    if (!lastWeekStart.isBlank()) {
      redirectBase.append("&lastWeekStart=").append(WebUiRenderer.urlEncode(lastWeekStart));
    }
    if (!lastWeekEnd.isBlank()) {
      redirectBase.append("&lastWeekEnd=").append(WebUiRenderer.urlEncode(lastWeekEnd));
    }
    return redirectBase.toString();
  }

  static String appendFocusTarget(String redirectUrl, String focusTarget) {
    return redirectUrl
        + (redirectUrl.contains("?") ? "&" : "?")
        + "focus="
        + WebUiRenderer.urlEncode(focusTarget);
  }

  static void handleAddLift(HttpExchange exchange, TrainingDataStoreProvider rootDb)
      throws IOException {
    if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
      sendText(exchange, 405, "Method Not Allowed");
      return;
    }

    String targetTab = "add-execution";
    try {
      TrainingDataStore db = databaseFor(exchange, rootDb);
      Map<String, String> form = parseForm(exchange.getRequestBody());
      targetTab = "exercises".equals(form.getOrDefault("tab", "")) ? "exercises" : "add-execution";
      String name = form.getOrDefault("name", "").trim();
      if (name.isBlank()) {
        redirect(
            exchange,
            "/?tab=" + targetTab + "&statusType=error&status=Lift%20name%20is%20required");
        return;
      }

      LiftRegion region = LiftRegion.fromString(form.getOrDefault("region", "UPPER"));
      LiftType main = LiftType.fromDbValue(form.getOrDefault("main", "none"));
      List<Muscle> muscles = parseMuscles(form.get("muscles"));
      String notes = form.getOrDefault("notes", "");

      db.addLift(name, region, main, muscles, notes);
      redirect(
          exchange,
          "/?tab="
              + targetTab
              + "&statusType=success&status="
              + WebUiRenderer.urlEncode("Created lift: " + name)
              + "&prefillLift="
              + WebUiRenderer.urlEncode(name));
    } catch (Exception e) {
      redirect(
          exchange,
          "/?tab="
              + targetTab
              + "&statusType=error&status="
              + WebUiRenderer.urlEncode("Failed to create lift: " + e.getMessage()));
    }
  }

  static void handleSetLiftEnabled(HttpExchange exchange, TrainingDataStoreProvider rootDb)
      throws IOException {
    if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
      sendText(exchange, 405, "Method Not Allowed");
      return;
    }
    String redirectBase = "/?tab=executions";
    try {
      TrainingDataStore db = databaseFor(exchange, rootDb);
      Map<String, String> form = parseForm(exchange.getRequestBody());
      redirectBase = buildExecutionRedirectBase(form);
      String lift = form.getOrDefault("lift", "").trim();
      if (lift.isBlank()) {
        redirect(exchange, redirectBase + "&statusType=error&status=Lift%20is%20required");
        return;
      }
      boolean enabled = "1".equals(form.getOrDefault("enabled", "1"));
      db.setLiftEnabled(lift, enabled);
      String status = enabled ? "Enabled" : "Disabled";
      redirect(
          exchange,
          redirectBase
              + "&statusType=success&status="
              + WebUiRenderer.urlEncode(status + " lift: " + lift));
    } catch (Exception e) {
      redirect(
          exchange,
          redirectBase
              + "&statusType=error&status="
              + WebUiRenderer.urlEncode("Failed to update lift status: " + e.getMessage()));
    }
  }

  static List<Muscle> parseMuscles(String value) {
    if (value == null || value.isBlank()) {
      return List.of();
    }
    return List.of(value.split(",")).stream()
        .map(String::trim)
        .filter(item -> !item.isBlank())
        .map(Muscle::fromString)
        .collect(Collectors.toList());
  }
}
