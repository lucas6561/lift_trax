package com.lifttrax.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ApplicationShellHtmlTest {
  @Test
  void rendersKeyboardReachableDesktopAndMobilePrimaryNavigation() {
    String html =
        ApplicationShellHtml.wrapPage(
            "Weekly history",
            ApplicationShellHtml.Section.HISTORY,
            "History",
            "/?tab=executions",
            "<h1>Weekly history</h1>");

    assertTrue(html.contains("class='skip-link' href='#main-content'"));
    assertTrue(html.contains("class='primary-navigation' aria-label='Primary'"));
    assertTrue(html.contains("class='mobile-primary-navigation' aria-label='Primary mobile'"));
    assertEquals(
        2, occurrences(html, "data-section='history' class='is-current' aria-current='page'"));
    assertEquals(2, occurrences(html, "data-section='today'"));
    assertTrue(html.contains("<div class='app-profile-slot' data-profile-slot></div>"));
    assertTrue(html.contains("aria-label='Breadcrumb'"));
    assertTrue(html.contains("id='main-content' tabindex='-1'"));
    assertFalse(html.contains("data-section='account'"));
  }

  @Test
  void mapsStableDeepLinkTabsToTaskSections() {
    assertEquals(
        ApplicationShellHtml.Section.TODAY, ApplicationShellHtml.Section.forTab("dashboard"));
    assertEquals(
        ApplicationShellHtml.Section.TRAIN, ApplicationShellHtml.Section.forTab("add-execution"));
    assertEquals(
        ApplicationShellHtml.Section.WORKOUTS,
        ApplicationShellHtml.Section.forTab("import-workout"));
    assertEquals(
        ApplicationShellHtml.Section.WORKOUTS, ApplicationShellHtml.Section.forTab("waves"));
    assertEquals(
        ApplicationShellHtml.Section.HISTORY, ApplicationShellHtml.Section.forTab("executions"));
    assertEquals(
        ApplicationShellHtml.Section.HISTORY, ApplicationShellHtml.Section.forTab("query"));
    assertEquals(
        ApplicationShellHtml.Section.HISTORY, ApplicationShellHtml.Section.forTab("last-week"));
    assertEquals(
        ApplicationShellHtml.Section.EXERCISES, ApplicationShellHtml.Section.forTab("exercises"));
    assertEquals(
        ApplicationShellHtml.Section.TODAY, ApplicationShellHtml.Section.forTab("unknown"));
  }

  private static int occurrences(String value, String needle) {
    return value.split(java.util.regex.Pattern.quote(needle), -1).length - 1;
  }
}
