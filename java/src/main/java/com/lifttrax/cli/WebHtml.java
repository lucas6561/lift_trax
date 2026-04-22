package com.lifttrax.cli;

/**
 * Core WebHtml component used by LiftTrax.
 */

final class WebHtml {
    private WebHtml() {
    }

    static String wrapPage(String title, String body) {
        return """
                <!DOCTYPE html>
                <html lang='en' data-theme='dark'>
                <head>
                  <meta charset='utf-8'/>
                  <meta name='viewport' content='width=device-width, initial-scale=1'/>
                  <title>%s</title>
                  <link rel='stylesheet' href='https://cdn.jsdelivr.net/npm/@picocss/pico@2/css/pico.min.css'/>
                  <style>
                    :root {
                      --pico-font-family: Inter, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
                    }
                    body {
                      background: radial-gradient(circle at top, #0b1736, #0a1022 45%%, #070d1a);
                      min-height: 100vh;
                    }
                    main.container {
                      max-width: 1080px;
                      padding-top: 1.2rem;
                      padding-bottom: 1.8rem;
                    }
                    .tabs { display: flex; gap: 0.5rem; margin-bottom: 1rem; flex-wrap: wrap; }
                    .tab { border: 1px solid var(--pico-muted-border-color); background: var(--pico-card-sectioning-background-color); color: var(--pico-color); padding: 0.45rem 0.8rem; border-radius: 999px; }
                    .tab.is-active { border-color: var(--pico-primary-border); color: var(--pico-primary-inverse); background: var(--pico-primary); }
                    .tab-panel { display: none; border: 1px solid var(--pico-muted-border-color); border-radius: 0.9rem; padding: 1.1rem; background: rgba(12, 22, 41, 0.82); backdrop-filter: blur(4px); }
                    .tab-panel.is-active { display: block; }
                    .tab-filter-bar { display: flex; flex-wrap: wrap; gap: 0.6rem; margin-bottom: 1rem; align-items: flex-start; }
                    .tab-filter-bar label { display: flex; align-items: center; gap: 0.4rem; }
                    .js-filter-muscle { min-width: 9rem; }
                    .js-clear-filters { align-self: center; flex: 0 0 auto; white-space: nowrap; }
                    .query-form { display: flex; gap: 0.75rem; align-items: center; flex-wrap: wrap; margin-bottom: 0.75rem; }
                    .query-form label { display: flex; align-items: center; gap: 0.4rem; }
                    .query-output { border: 1px solid var(--pico-muted-border-color); border-radius: 0.5rem; padding: 0.8rem; white-space: pre-wrap; }
                    .add-execution-form { display: flex; flex-direction: column; gap: 0.9rem; max-width: 820px; border: 1px solid var(--pico-muted-border-color); border-radius: 0.85rem; padding: 0.95rem; }
                    .add-execution-form fieldset { border: 1px solid var(--pico-muted-border-color); border-radius: 0.7rem; padding: 0.7rem; }
                    .segmented { display: flex; flex-wrap: wrap; gap: 0.6rem; }
                    .stacked-row { display: flex; flex-wrap: wrap; gap: 0.7rem; align-items: center; }
                    .add-actions { display: flex; justify-content: flex-end; margin-bottom: 0.7rem; }
                    .new-lift-details { width: min(820px, 100%%); }
                    .new-lift-details > summary { cursor: pointer; color: var(--pico-primary); margin-bottom: 0.5rem; }
                    .new-lift-form { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 0.6rem; padding: 0.7rem; border: 1px solid var(--pico-muted-border-color); border-radius: 0.7rem; }
                    .execution-lift-group { margin-bottom: 0.5rem; border: 1px solid var(--pico-muted-border-color); border-radius: 0.6rem; padding: 0.2rem 0.5rem; }
                    .execution-lift-toggle { cursor: pointer; display: block; width: 100%%; padding: 0.35rem 0.2rem; user-select: none; }
                    .execution-lift-toggle::-webkit-details-marker { margin-right: 0.35rem; }
                    .status { border-radius: 0.45rem; padding: 0.55rem 0.7rem; font-weight: 600; }
                    .status.success { border: 1px solid #15803d; color: #86efac; background: #052e16; }
                    .status.error { border: 1px solid #b91c1c; color: #fca5a5; background: #450a0a; }
                    .is-hidden { display: none; }
                  </style>
                </head>
                <body>
                <main class='container'>
                %s
                </main>
                </body>
                </html>
                """.formatted(escapeHtml(title), body);
    }

    static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
