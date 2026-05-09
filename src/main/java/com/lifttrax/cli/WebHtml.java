package com.lifttrax.cli;

/** Shared HTML helpers for the lightweight embedded web UI. */
final class WebHtml {
  private static final String PAGE_TEMPLATE =
      """
            <!DOCTYPE html>
            <html lang='en' data-theme='dark'>
            <head>
              <meta charset='utf-8'/>
              <meta name='viewport' content='width=device-width, initial-scale=1'/>
              <title>%s</title>
              <link rel='stylesheet' href='https://cdn.jsdelivr.net/npm/@picocss/pico@2/css/pico.min.css'/>
              <style>
            %s
              </style>
            </head>
            <body>
            <main class='container'>
            %s
            </main>
            </body>
            </html>
            """;

  private static final String BASE_STYLES =
      """
                :root {
                  --pico-font-family: Inter, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
                  --pico-font-size: 84%%;
                  --pico-line-height: 1.25;
                  --pico-form-element-spacing-vertical: 0.28rem;
                  --pico-form-element-spacing-horizontal: 0.45rem;
                  --pico-spacing: 0.65rem;
                }
                body {
                  background: radial-gradient(circle at top, #0b1736, #0a1022 45%%, #070d1a);
                  min-height: 100vh;
                }
                main.container {
                  max-width: 1080px;
                  padding-top: 0.5rem;
                  padding-bottom: 0.7rem;
                }
                h1, h2, h3 {
                  margin-top: 0.2rem;
                  margin-bottom: 0.45rem;
                  line-height: 1.15;
                }
                h2 { font-size: 1.35rem; }
                p, li { margin-bottom: 0.3rem; }
                label { margin-bottom: 0.2rem; }
                input, select, textarea, button { margin-bottom: 0.3rem; }
                .tabs { display: flex; gap: 0.35rem; margin-bottom: 0.55rem; flex-wrap: wrap; }
                .tab { border: 1px solid var(--pico-muted-border-color); background: var(--pico-card-sectioning-background-color); color: var(--pico-color); padding: 0.24rem 0.52rem; border-radius: 999px; font-size: 0.8rem; }
                .tab.is-active { border-color: var(--pico-primary-border); color: var(--pico-primary-inverse); background: var(--pico-primary); }
                .tab-panel { display: none; border: 1px solid var(--pico-muted-border-color); border-radius: 0.7rem; padding: 0.62rem; background: rgba(12, 22, 41, 0.82); backdrop-filter: blur(4px); }
                .tab-panel.is-active { display: block; }
                .tab-filter-bar { display: flex; flex-wrap: wrap; gap: 0.45rem; margin-bottom: 0.55rem; align-items: flex-start; }
                .tab-filter-bar label { display: flex; align-items: center; gap: 0.4rem; }
                .js-filter-muscle { min-width: 7rem; }
                .js-clear-filters { align-self: center; flex: 0 0 auto; white-space: nowrap; }
                .query-form { display: flex; gap: 0.42rem; align-items: center; flex-wrap: wrap; margin-bottom: 0.45rem; }
                .query-form label { display: flex; align-items: center; gap: 0.4rem; }
                .compact-actions button,
                .compact-btn {
                  width: auto;
                  flex: 0 0 auto;
                  padding: 0.2rem 0.5rem;
                  font-size: 0.8rem;
                  line-height: 1.1;
                  margin-bottom: 0;
                }
                .query-output { border: 1px solid var(--pico-muted-border-color); border-radius: 0.45rem; padding: 0.55rem; white-space: pre-wrap; }
                .add-execution-form { display: flex; flex-direction: column; gap: 0.48rem; max-width: 900px; border: 1px solid var(--pico-muted-border-color); border-radius: 0.65rem; padding: 0.58rem; }
                .add-execution-form fieldset { border: 1px solid var(--pico-muted-border-color); border-radius: 0.55rem; padding: 0.4rem; margin-bottom: 0.2rem; }
                .segmented { display: flex; flex-wrap: wrap; gap: 0.4rem; }
                .stacked-row { display: flex; flex-wrap: wrap; gap: 0.45rem; align-items: center; }
                .add-actions { display: flex; justify-content: flex-end; margin-bottom: 0.35rem; }
                .new-lift-details { width: min(820px, 100%%); }
                .new-lift-details > summary { cursor: pointer; color: var(--pico-primary); margin-bottom: 0.5rem; }
                .new-lift-form { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 0.45rem; padding: 0.5rem; border: 1px solid var(--pico-muted-border-color); border-radius: 0.7rem; }
                .execution-lift-group { margin-bottom: 0.3rem; border: 1px solid var(--pico-muted-border-color); border-radius: 0.5rem; padding: 0.14rem 0.4rem; }
                .execution-lift-toggle { cursor: pointer; display: block; width: 100%%; padding: 0.22rem 0.16rem; user-select: none; }
                .execution-lift-toggle::-webkit-details-marker { margin-right: 0.35rem; }
                .status { border-radius: 0.4rem; padding: 0.35rem 0.5rem; font-weight: 600; margin-bottom: 0.4rem; }
                .status.success { border: 1px solid #15803d; color: #86efac; background: #052e16; }
                .status.error { border: 1px solid #b91c1c; color: #fca5a5; background: #450a0a; }
                .is-hidden { display: none; }
            """;

  private WebHtml() {}

  /** Wraps page body markup in a shared themed layout shell. */
  static String wrapPage(String title, String body) {
    return PAGE_TEMPLATE.formatted(escapeHtml(title), BASE_STYLES, body);
  }

  /** Escapes unsafe HTML characters for safe interpolation into attributes and text nodes. */
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
