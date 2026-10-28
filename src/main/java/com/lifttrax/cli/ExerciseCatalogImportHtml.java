package com.lifttrax.cli;

import com.lifttrax.catalog.CatalogImportPlan;

/** Explicit catalog-file preview and confirmation, separate from workout imports. */
final class ExerciseCatalogImportHtml {
  private ExerciseCatalogImportHtml() {}

  static String render(String document, CatalogImportPlan plan, String error, String success) {
    StringBuilder body =
        new StringBuilder(
            "<h1>Import exercise catalog</h1><p><a href='/'>Back to LiftTrax</a></p>");
    body.append(
        "<p>Choose a LiftTrax exercise-catalog JSON file. Preview changes, then confirm to save them to your account. Completed history is retained.</p>");
    if (!error.isBlank()) {
      body.append("<p class='status error'>").append(WebHtml.escapeHtml(error)).append("</p>");
    }
    if (!success.isBlank()) {
      body.append("<p class='status success'>").append(WebHtml.escapeHtml(success)).append("</p>");
    }
    body.append(
            "<form method='post' action='/import-exercise-catalog'><input type='hidden' name='action' value='preview'/>")
        .append(
            "<label>Catalog file <input type='file' accept='.json,application/json' onchange=\"this.files[0]?.text().then(text => document.getElementById('catalog-json').value = text)\"/></label>")
        .append(
            "<label>Catalog JSON <textarea id='catalog-json' name='catalogJson' rows='14' required>")
        .append(WebHtml.escapeHtml(document))
        .append("</textarea></label><button type='submit'>Preview catalog</button></form>");
    if (plan != null) {
      body.append(
          "<h2>Preview</h2><table><thead><tr><th>Exercise</th><th>Action</th><th>Details</th></tr></thead><tbody>");
      for (CatalogImportPlan.Row row : plan.rows()) {
        body.append("<tr><td>")
            .append(WebHtml.escapeHtml(row.incoming().lift().name()))
            .append("</td><td>")
            .append(row.action().name().toLowerCase(java.util.Locale.ROOT))
            .append("</td><td>")
            .append(WebHtml.escapeHtml(row.reason()));
        if (row.existingName() != null) {
          body.append(" Current entry: ").append(WebHtml.escapeHtml(row.existingName()));
        }
        body.append("</td></tr>");
      }
      body.append("</tbody></table>");
      if (plan.hasConflicts()) {
        body.append(
            "<p class='status error'>Resolve conflicting rows in the file and preview again. Nothing has been saved.</p>");
      } else {
        body.append(
                "<form method='post' action='/import-exercise-catalog'><input type='hidden' name='action' value='confirm'/><input type='hidden' name='fingerprint' value='")
            .append(WebHtml.escapeHtml(plan.fingerprint()))
            .append("'/><input type='hidden' name='catalogJson' value='")
            .append(WebHtml.escapeHtml(document))
            .append("'/><button type='submit'>Confirm and save catalog</button></form>");
      }
    }
    return WebHtml.wrapPage("Import exercise catalog", body.toString());
  }
}
