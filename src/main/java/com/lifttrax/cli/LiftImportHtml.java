package com.lifttrax.cli;

import com.lifttrax.models.Lift;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Renders the shared lift picker without exposing account identities or execution history. */
final class LiftImportHtml {
  private LiftImportHtml() {}

  static String render(
      List<String> users,
      String source,
      List<Lift> lifts,
      List<Lift> ownLifts,
      String message,
      String messageType) {
    StringBuilder body =
        new StringBuilder(
            """
        <h1>Import lifts from another user</h1>
        <p>Choose a shared lift list, then select the lifts to copy into your own list.
        Existing lifts are skipped. Workout history is not copied.</p>
        <p><a href='/?tab=add-execution'>Back to Add Execution</a> · <a href='/account'>Manage my sharing</a></p>
        """);
    if (!message.isBlank()) {
      body.append("<p role='status' class='status ")
          .append("error".equals(messageType) ? "error" : "success")
          .append("'>")
          .append(WebHtml.escapeHtml(message))
          .append("</p>");
    }
    if (users.isEmpty()) {
      body.append(
          "<p>No other users are sharing lifts yet. Ask them to enable <strong>Share my lift list</strong> in Account settings.</p>");
    } else {
      body.append("<form method='get' action='/import-lifts' class='query-form'>")
          .append(
              "<label>User <select name='source' required><option value=''>Choose a user</option>");
      for (String user : users) {
        body.append("<option value='")
            .append(WebHtml.escapeHtml(user))
            .append("'")
            .append(user.equals(source) ? " selected" : "")
            .append(">")
            .append(WebHtml.escapeHtml(user))
            .append("</option>");
      }
      body.append("</select></label><button type='submit'>Show Lifts</button></form>");
    }
    if (!source.isBlank() && users.contains(source)) {
      body.append("<h2>Lifts from ").append(WebHtml.escapeHtml(source)).append("</h2>");
      if (lifts.isEmpty()) {
        body.append("<p>This user has no enabled lifts to import.</p>");
      } else {
        appendPicker(body, source, lifts, ownLifts);
      }
    }
    return WebHtml.wrapPage("Import Lifts", body.toString());
  }

  private static void appendPicker(
      StringBuilder body, String source, List<Lift> lifts, List<Lift> ownLifts) {
    Set<String> existing =
        ownLifts.stream().map(lift -> normalizedName(lift.name())).collect(Collectors.toSet());
    body.append(
            "<form id='lift-import-form' method='post' action='/import-lifts'><input type='hidden' name='source' value='")
        .append(WebHtml.escapeHtml(source))
        .append("'><fieldset><legend>Select lifts</legend>")
        .append(
            """
            <label style='display:flex;align-items:center;gap:12px;min-height:44px;'>
              <input id='select-all-lifts' type='checkbox' style='width:22px;height:22px;flex-shrink:0;' disabled>
              <span>Select All</span>
            </label>
            """);
    int available = 0;
    for (int i = 0; i < lifts.size(); i++) {
      Lift lift = lifts.get(i);
      boolean duplicate = existing.contains(normalizedName(lift.name()));
      if (!duplicate) {
        available++;
      }
      body.append(
              "<div style='padding:12px 0;border-bottom:1px solid var(--pico-muted-border-color);'>")
          .append("<label style='display:flex;align-items:center;gap:12px;min-height:44px;'>")
          .append(
              "<input type='checkbox' style='width:22px;height:22px;flex-shrink:0;' name='lift_")
          .append(i)
          .append("' value='")
          .append(WebHtml.escapeHtml(lift.name()))
          .append("'")
          .append(duplicate ? " disabled" : "")
          .append("> <span>")
          .append(WebHtml.escapeHtml(lift.name()))
          .append(duplicate ? " — Already in your list" : "")
          .append("</span></label>")
          .append("<p class='muted'>")
          .append(WebHtml.escapeHtml(lift.region().toString()))
          .append(" · ")
          .append(WebHtml.escapeHtml(lift.main() == null ? "" : lift.main().toDbValue()))
          .append(" · ")
          .append(
              WebHtml.escapeHtml(
                  String.join(", ", lift.muscles().stream().map(Enum::name).toList())))
          .append("</p>");
      if (lift.notes() != null && !lift.notes().isBlank()) {
        body.append("<details><summary>Lift notes</summary><p style='white-space:pre-wrap;'>")
            .append(WebHtml.escapeHtml(lift.notes()))
            .append("</p></details>");
      }
      body.append("</div>");
    }
    body.append("</fieldset><button type='submit'")
        .append(available == 0 ? " disabled" : "")
        .append(">Import Selected Lifts</button></form>")
        .append(
            """
            <script>
              (() => {
                const form = document.getElementById('lift-import-form');
                const selectAll = document.getElementById('select-all-lifts');
                const lifts = Array.from(form.querySelectorAll("input[type='checkbox'][name^='lift_']:not(:disabled)"));
                const updateSelection = () => {
                  const selected = lifts.filter(lift => lift.checked).length;
                  selectAll.disabled = lifts.length === 0;
                  selectAll.checked = lifts.length > 0 && selected === lifts.length;
                  selectAll.indeterminate = selected > 0 && selected < lifts.length;
                };
                selectAll.addEventListener('change', () => {
                  lifts.forEach(lift => { lift.checked = selectAll.checked; });
                  updateSelection();
                });
                lifts.forEach(lift => lift.addEventListener('change', updateSelection));
                window.addEventListener('pageshow', updateSelection);
                updateSelection();
              })();
            </script>
            """);
  }

  private static String normalizedName(String name) {
    return name.trim().toLowerCase(Locale.ROOT);
  }
}
