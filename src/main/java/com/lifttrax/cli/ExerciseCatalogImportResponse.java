package com.lifttrax.cli;

import com.lifttrax.db.TrainingDataStore;
import com.lifttrax.db.TrainingDataStoreProvider;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.util.Map;

/** Authenticated preview and confirmed save of an account-owned exercise catalog file. */
final class ExerciseCatalogImportResponse {
  private ExerciseCatalogImportResponse() {}

  static void handle(HttpExchange exchange, TrainingDataStoreProvider rootDb) throws IOException {
    String document = "";
    String error = "";
    String success = "";
    com.lifttrax.catalog.CatalogImportPlan plan = null;
    try {
      TrainingDataStore db = WebServerCli.databaseFor(exchange, rootDb);
      if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
        Map<String, String> form = WebServerCli.parseForm(exchange.getRequestBody());
        document = form.getOrDefault("catalogJson", "");
        var entries = com.lifttrax.catalog.ExerciseCatalogFile.parse(document);
        if ("confirm".equals(form.get("action"))) {
          int written = db.importCatalog(entries, form.getOrDefault("fingerprint", ""));
          success = "Saved " + written + " catalog changes. Completed history retained.";
        } else if ("preview".equals(form.get("action"))) {
          plan = com.lifttrax.catalog.CatalogImportPlan.preview(entries, db.listCatalogEntries());
        } else {
          throw new IllegalArgumentException("Choose Preview catalog before confirming changes.");
        }
      }
    } catch (IllegalArgumentException e) {
      error = e.getMessage();
    } catch (Exception e) {
      error =
          "Database write or read failed. No catalog changes were saved. Preview again before retrying.";
    }
    exchange.getResponseHeaders().set("Cache-Control", "no-store");
    WebServerCli.sendHtml(
        exchange, ExerciseCatalogImportHtml.render(document, plan, error, success));
  }
}
