package com.lifttrax.cli;

import com.lifttrax.db.AccountProfile;
import com.lifttrax.db.TrainingDataStore;
import com.lifttrax.db.TrainingDataStoreProvider;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;

/** Authenticated HTTP response for downloading one account's training data. */
final class AccountExportResponse {
  private AccountExportResponse() {}

  static void handle(HttpExchange exchange, TrainingDataStoreProvider rootDb) throws IOException {
    Map<String, String> query = WebServerCli.parseQuery(exchange.getRequestURI());
    String format = query.getOrDefault("format", "json").trim();
    if (query.keySet().stream().anyMatch(key -> !"format".equals(key))
        || !"json".equalsIgnoreCase(format)) {
      WebServerCli.sendJson(
          exchange,
          400,
          Map.of(
              "error",
              "Invalid account export request. Only the optional format=json parameter is supported."));
      return;
    }

    try {
      WebAuth.User user = WebAuth.currentUser(exchange).orElseThrow();
      TrainingDataStore db = WebServerCli.databaseFor(exchange, rootDb);
      AccountProfile account = rootDb.accountFor(user.id(), user.email());
      Instant exportedAt = Instant.now();
      WebServerCli.sendDownload(
          exchange,
          "application/json; charset=utf-8",
          AccountExportWriter.fileName(exportedAt),
          AccountExportWriter.write(db, account, exportedAt));
    } catch (Exception e) {
      WebServerCli.sendJson(
          exchange, 500, Map.of("error", "Account data could not be exported. Please try again."));
    }
  }
}
