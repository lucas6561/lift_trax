package com.lifttrax.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WebHtmlTest {

    @Test
    void wrapPageEscapesTitleButKeepsBodyMarkup() {
        String html = WebHtml.wrapPage("<LiftTrax>", "<section id='body'>ok</section>");

        assertTrue(html.contains("<title>&lt;LiftTrax&gt;</title>"));
        assertTrue(html.contains("<section id='body'>ok</section>"));
        assertTrue(html.contains("data-theme='dark'"));
    }
}
