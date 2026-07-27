package com.lifttrax.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lifttrax.db.AccountProfile;
import org.junit.jupiter.api.Test;

class AccountPageHtmlTest {

  @Test
  void blankAccountSuggestsANormalizedBoundedUsername() {
    assertEquals("", AccountPageHtml.suggestedUsername(null));
    assertEquals("lucas-smith-", AccountPageHtml.suggestedUsername(" Lucas Smith! "));
    assertEquals(
        30, AccountPageHtml.suggestedUsername("abcdefghijklmnopqrstuvwxyz-1234567890").length());

    String html =
        AccountPageHtml.render(
            new AccountProfile("auth-id", "", "email@example.test"),
            new WebAuth.User("auth-id", "email@example.test", "Lucas Smith"),
            "Try <again>",
            "error' data-bad='1");

    assertTrue(html.contains("value='lucas-smith'"));
    assertTrue(html.contains("Try &lt;again&gt;"));
    assertTrue(html.contains("error&#39; data-bad=&#39;1"));
  }
}
