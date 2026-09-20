package com.lifttrax.cli;

import com.lifttrax.db.AccountProfile;
import java.util.Locale;

/** Renders the account page independently from HTTP and persistence concerns. */
final class AccountPageHtml {
  private AccountPageHtml() {}

  static String render(
      AccountProfile account, WebAuth.User user, String message, String messageType) {
    return render(account, user, message, messageType, false);
  }

  static String render(
      AccountProfile account,
      WebAuth.User user,
      String message,
      String messageType,
      boolean shareLifts) {
    String value =
        account.username().isBlank()
            ? suggestedUsername(user.suggestedUsername())
            : account.username();
    String status =
        message.isBlank()
            ? ""
            : "<p class='status "
                + WebHtml.escapeHtml(messageType)
                + "'>"
                + WebHtml.escapeHtml(message)
                + "</p>";
    String body =
        """
            <h1>Account</h1>
            <p class='muted'>Choose the memorable username used by LiftTrax displays and operator commands. Your sign-in ID remains the private ownership key.</p>
            %s
            <form method='post' action='/account' class='query-form' style='display:block;'>
              <label>Username <input name='username' value='%s' required minlength='3' maxlength='30' pattern='[A-Za-z0-9][A-Za-z0-9_-]{2,29}' autocomplete='username'></label>
              <p class='muted'>3-30 letters, numbers, underscores, or hyphens. Usernames are stored in lowercase.</p>
              <button type='submit'>Save Username</button>
            </form>
            <h2>Lift sharing</h2>
            <p>Let other signed-in users copy your enabled lifts, including their names, regions, types, muscles, and lift notes. Workout history stays private. Copies already imported remain in the other user's list when sharing is turned off.</p>
            <form method='post' action='/account' class='query-form' style='display:block;'>
              <input type='hidden' name='action' value='lift-sharing'>
              <label><input type='checkbox' name='shareLifts' value='true' style='width:auto;' %s> Share my lift list</label>
              <p class='muted'>Save a username first so others can find your list.</p>
              <button type='submit'>Save Sharing</button>
            </form>
            <p><a href='/import-lifts'>Import lifts from another user</a></p>
            <p><a href='/'>Back to LiftTrax</a></p>
            """
            .formatted(status, WebHtml.escapeHtml(value), shareLifts ? "checked" : "");
    return WebHtml.wrapPage("Account", body);
  }

  static String suggestedUsername(String suggestion) {
    if (suggestion == null) {
      return "";
    }
    String normalized = suggestion.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "-");
    return normalized.length() > 30 ? normalized.substring(0, 30) : normalized;
  }
}
