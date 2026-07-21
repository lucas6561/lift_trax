package com.lifttrax.cli;

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
        html.contains(".save-execution-btn { position: sticky; bottom: 0.35rem; width: 100%"));
    assertTrue(html.contains(".session-block-nav { grid-template-columns: 1fr; }"));
    assertTrue(html.contains(".session-block-nav .js-session-skip-block { width: 100%; }"));
    assertTrue(
        html.contains(".session-block-actions { display: grid; grid-template-columns: 1fr; }"));
    assertTrue(html.contains(".save-workout-session-btn { bottom: 4rem; width: 100%"));
    assertTrue(html.contains(".execution-row-actions { flex: 1 1 100%; width: 100%;"));
    assertTrue(html.contains(".execution-row-actions .danger { flex: 0 0 36%; margin-left: auto;"));
  }
}
