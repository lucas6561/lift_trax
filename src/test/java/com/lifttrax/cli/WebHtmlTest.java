package com.lifttrax.cli;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WebHtmlTest {

  @Test
  void wrapPageEscapesTitleButKeepsBodyMarkup() {
    String html = WebHtml.wrapPage("<LiftTrax>", "<section id='body'>ok</section>");

    assertTrue(html.contains("<title>&lt;LiftTrax&gt;</title>"));
    assertTrue(html.contains("<section id='body'>ok</section>"));
    assertTrue(html.contains("data-theme='dark'"));
  }

  @Test
  void mobileStylesKeepLoggingActionsReadableAtPhoneWidths() {
    String html = WebHtml.wrapPage("LiftTrax", "<form class='planned-session-form'></form>");

    assertTrue(html.contains("@media (max-width: 720px)"));
    assertTrue(
        html.contains(
            ".save-execution-btn { position: sticky; bottom: max(0.35rem, env(safe-area-inset-bottom)); width: 100%"));
    assertTrue(html.contains(".session-block-nav { grid-template-columns: 1fr; }"));
    assertTrue(html.contains(".session-block-nav .js-session-skip-block { width: 100%; }"));
    assertTrue(
        html.contains(".session-block-actions { display: grid; grid-template-columns: 1fr; }"));
    assertTrue(
        html.contains(
            ".save-workout-session-btn { bottom: max(0.35rem, env(safe-area-inset-bottom)); width: 100%"));
    assertTrue(html.contains(".execution-row-actions { flex: 1 1 100%; width: 100%;"));
    assertTrue(html.contains(".execution-row-actions .danger { flex: 0 0 36%; margin-left: auto;"));
  }

  @Test
  void mobileNavigationUsesPageFlowWithoutBottomNavigationReservations() {
    String html = WebHtml.wrapPage("LiftTrax", "");
    String mobileStyles = html.substring(html.indexOf("@media (max-width: 720px)"));

    assertTrue(
        mobileStyles.contains(".mobile-primary-navigation { display: block; position: static;"));
    assertFalse(html.contains(".mobile-primary-navigation { display: block; position: fixed;"));
    assertFalse(html.contains(".mobile-primary-navigation { display: block; position: sticky;"));
    assertFalse(mobileStyles.contains("body { padding-bottom:"));
    assertFalse(html.contains("scroll-padding-bottom: 5.5rem"));
    assertFalse(mobileStyles.contains("bottom: 4.45rem"));
    assertFalse(mobileStyles.contains("bottom: 4rem"));
    assertTrue(
        mobileStyles.contains(
            "html { scroll-padding-bottom: calc(3.5rem + env(safe-area-inset-bottom)); }"));
  }

  @Test
  void desktopSidebarAndSavePlacementRemainUnchanged() {
    String html = WebHtml.wrapPage("LiftTrax", "");
    String desktopStyles = html.substring(0, html.indexOf("@media (max-width: 720px)"));

    assertTrue(desktopStyles.contains(".mobile-primary-navigation { display: none; }"));
    assertTrue(
        desktopStyles.contains(".app-frame { display: grid; grid-template-columns: 11.5rem"));
    assertTrue(desktopStyles.contains(".app-sidebar { position: sticky; top: 4.6rem; }"));
    assertTrue(
        desktopStyles.contains(".save-workout-session-btn { position: sticky; bottom: 0.35rem;"));
    assertTrue(html.contains(".app-sidebar { display: none; }"));
  }

  @Test
  void workAlongBlockNavigationScrollsWithThePage() {
    String html = WebHtml.wrapPage("LiftTrax", "<form class='planned-session-form'></form>");

    assertTrue(html.contains(".session-block-nav { display: grid;"));
    assertFalse(html.contains(".session-block-nav { position: sticky;"));
  }

  @Test
  void workAlongSetEntryUsesACompactPhoneGrid() {
    String html = WebHtml.wrapPage("LiftTrax", "<form class='planned-session-form'></form>");

    assertTrue(
        html.contains(
            ".session-entry-primary { display: grid; grid-template-columns: minmax(180px, 1.4fr)"));
    assertTrue(
        html.contains(
            ".session-entry-primary { grid-template-columns: repeat(3, minmax(0, 1fr)); }"));
    assertTrue(html.contains(".session-entry-weight { grid-column: 1 / -1; }"));
    assertTrue(html.contains(".session-entry-more > summary { min-height: 2.9rem; }"));
  }
}
