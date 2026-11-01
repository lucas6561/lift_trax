package com.lifttrax.cli;

import static com.lifttrax.cli.WebCatalogRoutes.*;
import static com.lifttrax.cli.WebExecutionRequestParser.*;
import static com.lifttrax.cli.WebServerCli.*;

import com.lifttrax.db.TrainingDataStore;
import com.lifttrax.db.TrainingDataStoreProvider;
import com.lifttrax.models.ExecutionSet;
import com.lifttrax.models.LiftExecution;
import com.lifttrax.models.SetMetric;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Handles execution recording and mutation requests. */
final class WebExecutionRoutes {
  private WebExecutionRoutes() {}

  static void handleAddExecution(HttpExchange exchange, TrainingDataStoreProvider rootDb)
      throws IOException {
    if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
      sendText(exchange, 405, "Method Not Allowed");
      return;
    }

    try {
      TrainingDataStore db = databaseFor(exchange, rootDb);
      Map<String, String> form = parseForm(exchange.getRequestBody());
      String liftName = form.getOrDefault("lift", "").trim();
      if (liftName.isBlank()) {
        redirect(
            exchange,
            appendFocusTarget(
                "/?tab=add-execution&statusType=error&status=Lift%20is%20required", "add-lift"));
        return;
      }

      LocalDate date =
          Optional.ofNullable(form.get("date"))
              .filter(value -> !value.isBlank())
              .map(LocalDate::parse)
              .orElse(LocalDate.now());

      String detailedSets = form.getOrDefault("detailedSets", "[]").trim();
      List<ExecutionSet> sets = parseDetailedSets(detailedSets);
      if (sets.isEmpty()) {
        if (!detailedSets.isBlank() && !detailedSets.matches("\\[\\s*\\]")) {
          throw new IllegalArgumentException("Detailed sets must contain valid actual values");
        }
        int setCount = parsePositiveInt(form.getOrDefault("setCount", "1"), "Set count");
        String weight = form.getOrDefault("weight", "").trim();
        Float rpe = parseOptionalFloat(form.get("rpe"));
        boolean missed = isMissed(form);
        SetMetric metric = parseMetric(form);
        sets = new ArrayList<>();
        for (int i = 0; i < setCount; i++) {
          sets.add(new ExecutionSet(metric, weight, rpe, missed));
        }
      }

      LiftExecution execution =
          new LiftExecution(
              null,
              date,
              sets,
              form.containsKey("warmup"),
              form.containsKey("deload"),
              form.getOrDefault("notes", ""));
      db.addLiftExecution(liftName, execution);
      StringBuilder redirectUrl =
          new StringBuilder("/?tab=add-execution&statusType=success&status=Execution%20saved");
      redirectUrl.append("&prefillLift=").append(WebUiRenderer.urlEncode(liftName));
      redirectUrl.append("&prefillDate=").append(WebUiRenderer.urlEncode(date.toString()));
      redirectUrl.append("&prefillWarmup=").append(form.containsKey("warmup"));
      redirectUrl.append("&prefillDeload=").append(form.containsKey("deload"));
      redirect(exchange, appendFocusTarget(redirectUrl.toString(), "add-weight"));
    } catch (Exception e) {
      redirect(
          exchange,
          appendFocusTarget(
              "/?tab=add-execution&statusType=error&status="
                  + WebUiRenderer.urlEncode("Failed to save execution: " + e.getMessage()),
              "add-weight"));
    }
  }

  static void handleLoadLastExecution(HttpExchange exchange, TrainingDataStoreProvider rootDb)
      throws IOException {
    if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
      sendText(exchange, 405, "Method Not Allowed");
      return;
    }

    try {
      TrainingDataStore db = databaseFor(exchange, rootDb);
      Map<String, String> query = parseQuery(exchange.getRequestURI());
      String liftName = query.getOrDefault("lift", "").trim();
      if (liftName.isBlank()) {
        redirect(
            exchange,
            appendFocusTarget(
                "/?tab=add-execution&statusType=error&status=Lift%20is%20required%20for%20Load%20Last",
                "add-lift"));
        return;
      }
      LocalDate selectedDate =
          Optional.ofNullable(query.get("date"))
              .filter(value -> !value.isBlank())
              .map(
                  value -> {
                    try {
                      return LocalDate.parse(value);
                    } catch (Exception ignored) {
                      return LocalDate.now();
                    }
                  })
              .orElse(LocalDate.now());
      boolean warmup = query.containsKey("warmup");
      boolean deload = query.containsKey("deload");

      LiftExecution last = db.getLastExecution(liftName, warmup, deload);
      if (last == null) {
        String criteria = "warmup=" + warmup + ", deload=" + deload;
        redirect(
            exchange,
            appendFocusTarget(
                buildLoadLastNoPriorRedirect(
                    query, liftName, selectedDate, warmup, deload, criteria),
                "add-weight"));
        return;
      }

      StringBuilder redirectUrl =
          new StringBuilder(
              "/?tab=add-execution&statusType=success&status=Loaded%20last%20execution");
      redirectUrl.append("&prefillLift=").append(WebUiRenderer.urlEncode(liftName));
      redirectUrl
          .append("&prefillWeight=")
          .append(
              WebUiRenderer.urlEncode(
                  last.sets().isEmpty() ? "" : safe(last.sets().get(0).weight())));
      redirectUrl.append("&prefillSetCount=").append(last.sets().size());
      redirectUrl.append("&prefillDate=").append(WebUiRenderer.urlEncode(selectedDate.toString()));
      redirectUrl.append("&prefillWarmup=").append(last.warmup());
      redirectUrl.append("&prefillDeload=").append(last.deload());
      redirectUrl.append("&prefillNotes=").append(WebUiRenderer.urlEncode(safe(last.notes())));
      if (!last.sets().isEmpty()) {
        ExecutionSet first = last.sets().get(0);
        redirectUrl.append("&prefillMissed=").append(first.missed());
        redirectUrl
            .append("&prefillRpe=")
            .append(
                WebUiRenderer.urlEncode(
                    first.rpe() == null ? "" : String.format(Locale.ROOT, "%s", first.rpe())));
        applyMetricPrefill(redirectUrl, first.metric());
      }

      redirect(exchange, appendFocusTarget(redirectUrl.toString(), "save-execution"));
    } catch (Exception e) {
      redirect(
          exchange,
          appendFocusTarget(
              "/?tab=add-execution&statusType=error&status="
                  + WebUiRenderer.urlEncode("Failed to load execution: " + e.getMessage()),
              "add-weight"));
    }
  }

  static void handleExecutionsFragment(HttpExchange exchange, TrainingDataStoreProvider rootDb)
      throws IOException {
    if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
      sendText(exchange, 405, "Method Not Allowed");
      return;
    }

    try {
      TrainingDataStore db = databaseFor(exchange, rootDb);
      Map<String, String> query = parseQuery(exchange.getRequestURI());
      String liftName = query.getOrDefault("lift", "").trim();
      if (liftName.isBlank()) {
        sendText(exchange, 400, "Missing lift");
        return;
      }
      String body = WebUiRenderer.renderExecutionRows(db, liftName);
      sendHtml(exchange, body);
    } catch (Exception e) {
      String message =
          e.getMessage() == null
              ? e.getClass().getName()
              : e.getClass().getName() + ": " + e.getMessage();
      sendHtml(exchange, "<div class='status error'>" + WebHtml.escapeHtml(message) + "</div>");
    }
  }

  static void handleUpdateExecution(HttpExchange exchange, TrainingDataStoreProvider rootDb)
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
      String liftName = form.getOrDefault("lift", "").trim();
      int executionId = parsePositiveInt(form.getOrDefault("executionId", ""), "Execution ID");
      if (liftName.isBlank()) {
        redirect(exchange, redirectBase + "&statusType=error&status=Lift%20is%20required");
        return;
      }

      LiftExecution existing = db.getExecution(liftName, executionId);
      if (existing == null) {
        redirect(
            exchange,
            redirectBase
                + "&statusType=error&status="
                + WebUiRenderer.urlEncode("Execution not found for " + liftName));
        return;
      }

      LocalDate date =
          Optional.ofNullable(form.get("date"))
              .filter(value -> !value.isBlank())
              .map(LocalDate::parse)
              .orElse(existing.date());
      String notes = form.getOrDefault("notes", "");
      String detailedSetsJson = form.getOrDefault("detailedSets", "").trim();
      List<ExecutionSet> sets = existing.sets();
      if (!detailedSetsJson.isBlank()) {
        List<ExecutionSet> parsed = parseDetailedSets(detailedSetsJson);
        if (parsed.isEmpty()) {
          throw new IllegalArgumentException(
              "Detailed sets must be valid JSON and contain at least one set");
        }
        sets = parsed;
      }

      LiftExecution updated =
          new LiftExecution(
              existing.id(),
              date,
              sets,
              form.containsKey("warmup"),
              form.containsKey("deload"),
              notes);
      db.updateLiftExecution(executionId, updated);
      redirect(
          exchange,
          redirectBase
              + "&statusType=success&status="
              + WebUiRenderer.urlEncode("Execution updated")
              + "&focus=execution-list");
    } catch (Exception e) {
      redirect(
          exchange,
          redirectBase
              + "&statusType=error&status="
              + WebUiRenderer.urlEncode("Failed to update execution: " + e.getMessage())
              + "&focus=execution-list");
    }
  }

  static void handleDeleteExecution(HttpExchange exchange, TrainingDataStoreProvider rootDb)
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
      int executionId = parsePositiveInt(form.getOrDefault("executionId", ""), "Execution ID");
      db.deleteLiftExecution(executionId);
      redirect(
          exchange,
          redirectBase
              + "&statusType=success&status="
              + WebUiRenderer.urlEncode("Execution deleted")
              + "&focus=execution-list");
    } catch (Exception e) {
      redirect(
          exchange,
          redirectBase
              + "&statusType=error&status="
              + WebUiRenderer.urlEncode("Failed to delete execution: " + e.getMessage())
              + "&focus=execution-list");
    }
  }

  static void handleDeleteLift(HttpExchange exchange, TrainingDataStoreProvider rootDb)
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
      db.deleteLift(lift);
      redirect(
          exchange,
          redirectBase
              + "&statusType=success&status="
              + WebUiRenderer.urlEncode(
                  "Deleted catalog lift: " + lift + ". Completed history retained."));
    } catch (Exception e) {
      redirect(
          exchange,
          redirectBase
              + "&statusType=error&status="
              + WebUiRenderer.urlEncode("Failed to delete lift: " + e.getMessage()));
    }
  }
}
