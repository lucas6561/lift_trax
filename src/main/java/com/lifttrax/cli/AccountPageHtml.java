package com.lifttrax.cli;

import com.lifttrax.db.AccountProfile;
import java.util.Locale;

/** Renders the account page independently from HTTP and persistence concerns. */
final class AccountPageHtml {
  private AccountPageHtml() {}

  static String render(
      AccountProfile account, WebAuth.User user, String message, String messageType) {
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
            <p><a href='/'>Back to LiftTrax</a></p>
            """
            .formatted(status, WebHtml.escapeHtml(value));
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
