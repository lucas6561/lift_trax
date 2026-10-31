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
  void placesMobileNavigationBetweenHeaderAndMainContentInKeyboardOrder() {
    String html =
        ApplicationShellHtml.wrapPage(
            "Train", ApplicationShellHtml.Section.TRAIN, "<h1>Quick logging</h1>");

    int skipLink = html.indexOf("<a class='skip-link'");
    int header = html.indexOf("<header class='app-header'>");
    int profile = html.indexOf("<div class='app-profile-slot'");
    int headerEnd = html.indexOf("</header>", header);
    int mobileNav = html.indexOf("<nav class='mobile-primary-navigation'");
    int frame = html.indexOf("<div class='app-frame'>");
    int desktopNav = html.indexOf("<nav class='primary-navigation'");
    int content = html.indexOf("<section class='app-content' id='main-content' tabindex='-1'>");

    assertTrue(skipLink >= 0 && skipLink < header);
    assertTrue(header < profile && profile < headerEnd);
    assertTrue(headerEnd < mobileNav && mobileNav < frame);
    assertTrue(frame < desktopNav && desktopNav < content);
    assertTrue(content < html.indexOf("<h1>Quick logging</h1>"));
    assertEquals(1, occurrences(html, "<nav class='mobile-primary-navigation'"));
    assertEquals(
        2, occurrences(html, "data-section='train' class='is-current' aria-current='page'"));
    for (var section : ApplicationShellHtml.Section.values()) {
      if (section != ApplicationShellHtml.Section.ACCOUNT) {
        assertEquals(2, occurrences(html, "data-section='" + section.key() + "'"));
        assertTrue(html.contains("href='" + section.href() + "' data-section='" + section.key()));
      }
    }
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
