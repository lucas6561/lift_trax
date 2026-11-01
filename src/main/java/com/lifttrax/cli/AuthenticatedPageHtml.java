package com.lifttrax.cli;

import java.util.Optional;

/** Adds account identity and controls to an authenticated application page. */
final class AuthenticatedPageHtml {
  private AuthenticatedPageHtml() {}

  static String decorate(String html, Optional<WebAuth.User> user, Object configuredAccountLabel) {
    if (user.isEmpty() || !html.contains("<main class='container'>")) {
      return html;
    }
    String label =
        configuredAccountLabel instanceof String value && !value.isBlank()
            ? value
            : user.get().label();
    String accountCurrent =
        html.contains("data-current-section='account'") ? " aria-current='page'" : "";
    String accountBar =
        "<section class='auth-bar' aria-label='Account controls'><a href='/account'"
            + accountCurrent
            + ">Signed in as "
            + WebHtml.escapeHtml(label)
            + "</a><form method='post' action='/auth/logout'>"
            + "<button type='submit' class='secondary compact-btn'>Sign Out</button></form></section>";
    String accountScope = BrowserAccountScope.forUser(user.get().id());
    String scopedHtml =
        html.replace(
            "<main class='container'>",
            "<main class='container' data-account-scope='" + accountScope + "'>");
    String profileSlot = "<div class='app-profile-slot' data-profile-slot></div>";
    if (scopedHtml.contains(profileSlot)) {
      return scopedHtml.replace(profileSlot, accountBar);
    }
    return scopedHtml.replace(
        "<main class='container' data-account-scope='" + accountScope + "'>",
        "<main class='container' data-account-scope='" + accountScope + "'>" + accountBar);
  }
}
