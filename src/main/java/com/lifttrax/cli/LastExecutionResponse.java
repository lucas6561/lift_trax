package com.lifttrax.cli;

import com.lifttrax.db.TrainingDataStore;
import com.lifttrax.db.TrainingDataStoreProvider;
import com.lifttrax.models.LiftExecution;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.util.Map;

/** Read-only execution values for filling a Work Along entry from account history. */
record LastExecutionResponse(int status, Map<String, Object> payload) {
  static void handle(HttpExchange exchange, TrainingDataStoreProvider rootDb) throws IOException {
    if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())
        || !"json".equals(WebServerCli.parseQuery(exchange.getRequestURI()).get("format"))) {
      WebServerCli.handleLoadLastExecution(exchange, rootDb);
      return;
    }
    LastExecutionResponse response;
    try {
      response =
          load(
              WebServerCli.databaseFor(exchange, rootDb),
              WebServerCli.parseQuery(exchange.getRequestURI()));
    } catch (Exception e) {
      response =
          new LastExecutionResponse(
              400, Map.of("error", "Could not load the last execution. Try again."));
    }
    WebServerCli.sendJson(exchange, response.status(), response.payload());
  }

  static LastExecutionResponse load(TrainingDataStore db, Map<String, String> query)
      throws Exception {
    String liftName = query.getOrDefault("lift", "").trim();
    if (liftName.isBlank()) {
      return new LastExecutionResponse(
          400, Map.of("error", "Choose a lift before loading its last execution."));
    }
    LiftExecution last =
        db.getLastExecution(liftName, query.containsKey("warmup"), query.containsKey("deload"));
    if (last == null || last.sets().isEmpty()) {
      return new LastExecutionResponse(
          404, Map.of("error", "No prior execution for this lift and warm-up/deload selection."));
    }
    return new LastExecutionResponse(
        200,
        Map.of(
            "sets", last.sets().stream().map(ExecutionSetFormValues::from).toList(),
            "weight", WeightInputParser.parseWeightPrefill(last.sets().get(0).weight()),
            "notes", last.notes() == null ? "" : last.notes(),
            "date", last.date().toString()));
  }
}
