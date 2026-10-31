package com.lifttrax.cli;

import com.lifttrax.db.TrainingDataStore;
import com.lifttrax.db.TrainingDataStoreProvider;
import com.lifttrax.models.Lift;
import com.lifttrax.models.LiftExecution;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Renders current catalog detail and the immutable performed metadata behind its history. */
final class LiftDetailResponse {
  private LiftDetailResponse() {}

  static void handle(HttpExchange exchange, TrainingDataStoreProvider rootDb) throws IOException {
    if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
      WebServerCli.sendText(exchange, 405, "Method Not Allowed");
      return;
    }

    Map<String, String> query = WebServerCli.parseQuery(exchange.getRequestURI());
    String name = query.get("name");
    if (name == null || name.isBlank()) {
      WebServerCli.sendHtml(
          exchange,
          ApplicationShellHtml.wrapPage(
              "Missing lift",
              ApplicationShellHtml.Section.EXERCISES,
              "<h1>Missing lift name</h1><p><a href='/?tab=exercises'>Back to Exercises</a></p>"));
      return;
    }

    try {
      TrainingDataStore db = WebServerCli.databaseFor(exchange, rootDb);
      boolean historical = query.containsKey("historyExecutionId");
      List<com.lifttrax.db.LiftExecutionRow> performed =
          historical
              ? db.historyForExecution(Integer.parseInt(query.get("historyExecutionId")))
              : db.liftHistory(name);
      Lift lift;
      if (historical) {
        if (performed.isEmpty()) {
          throw new IllegalArgumentException("Historical execution not found.");
        }
        lift = performed.get(performed.size() - 1).lift();
      } else {
        try {
          lift = db.getLift(name);
        } catch (IllegalArgumentException missing) {
          if (performed.isEmpty()) {
            throw missing;
          }
          lift = performed.get(performed.size() - 1).lift();
        }
      }
      List<LiftExecution> executions =
          performed.stream().map(com.lifttrax.db.LiftExecutionRow::execution).toList();

      StringBuilder body = new StringBuilder();
      body.append("<h1>")
          .append(WebHtml.escapeHtml(lift.name()))
          .append("</h1>")
          .append("<p><strong>Region:</strong> ")
          .append(WebHtml.escapeHtml(lift.region().toString()))
          .append("</p>")
          .append("<p><strong>Main type:</strong> ")
          .append(WebHtml.escapeHtml(WebUiRenderer.formatMainType(lift)))
          .append("</p>")
          .append("<p><strong>Muscles:</strong> ")
          .append(
              WebHtml.escapeHtml(
                  WebUiRenderer.joinList(lift.muscles().stream().map(Object::toString).toList())))
          .append("</p>")
          .append("<p><strong>Notes:</strong> ")
          .append(WebHtml.escapeHtml(lift.notes() == null ? "" : lift.notes()))
          .append("</p>")
          .append(WebUiRenderer.renderLiftTrendSummary(executions, LocalDate.now()))
          .append("<h2>Executions</h2>");

      if (executions.isEmpty()) {
        body.append("<p>No executions recorded.</p>");
      } else {
        body.append(
            "<table><thead><tr><th>Date</th><th>Performed exercise</th><th>Sets</th><th>Notes</th></tr></thead><tbody>");
        for (com.lifttrax.db.LiftExecutionRow row : performed) {
          LiftExecution execution = row.execution();
          body.append("<tr><td>")
              .append(WebHtml.escapeHtml(WebUiRenderer.DATE_FORMAT.format(execution.date())))
              .append("</td><td>")
              .append(WebHtml.escapeHtml(row.lift().name()))
              .append("</td><td>")
              .append(WebHtml.escapeHtml(WebUiRenderer.formatSets(execution.sets())))
              .append("</td><td>")
              .append(WebHtml.escapeHtml(execution.notes() == null ? "" : execution.notes()))
              .append("</td></tr>");
        }
        body.append("</tbody></table>");
      }

      WebServerCli.sendHtml(
          exchange,
          ApplicationShellHtml.wrapPage(
              lift.name(),
              ApplicationShellHtml.Section.EXERCISES,
              "Exercises",
              "/?tab=exercises",
              body.toString()));
    } catch (Exception e) {
      WebServerCli.sendHtml(
          exchange,
          ApplicationShellHtml.wrapPage(
              "Error",
              ApplicationShellHtml.Section.EXERCISES,
              "<h1>Error loading lift</h1><pre>"
                  + WebHtml.escapeHtml(e.getMessage())
                  + "</pre><p><a href='/?tab=exercises'>Back to Exercises</a></p>"));
    }
  }
}
