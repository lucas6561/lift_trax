package com.lifttrax.cli;

final class WebHtml {
    private WebHtml() {
    }

    static String wrapPage(String title, String body) {
        return """
                <!DOCTYPE html>
                <html lang='en'>
                <head>
                  <meta charset='utf-8'/>
                  <meta name='viewport' content='width=device-width, initial-scale=1'/>
                  <title>%s</title>
                  <style>
                    body { font-family: Inter, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; margin: 2rem auto; max-width: 1040px; padding: 0 1rem; background: radial-gradient(circle at top, #0b1736, #0a1022 45%%, #070d1a); color: #e5e7eb; }
                    a { color: #60a5fa; }
                    input, button, select { padding: 0.58rem 0.62rem; border-radius: 0.6rem; border: 1px solid #334155; background: #1a2338; color: #f9fafb; }
                    input:focus, select:focus, button:focus { outline: none; border-color: #60a5fa; box-shadow: 0 0 0 3px rgba(96,165,250,0.25); }
                    button { cursor: pointer; background: linear-gradient(180deg, #2563eb, #1d4ed8); border: 1px solid #1d4ed8; }
                    button.secondary { background: #1a2338; border-color: #475569; color: #cbd5e1; }
                    table { border-collapse: collapse; width: 100%%; margin-top: 1rem; }
                    th, td { border: 1px solid #374151; padding: 0.45rem; vertical-align: top; text-align: left; }
                    code { background: #1f2937; padding: 0.15rem 0.35rem; border-radius: 0.25rem; }
                    li { margin-bottom: 0.35rem; }
                    .tabs { display: flex; gap: 0.5rem; margin-bottom: 1rem; flex-wrap: wrap; }
                    .tab { border: 1px solid #334155; background: #0f172a; color: #d1d5db; padding: 0.45rem 0.8rem; border-radius: 999px; }
                    .tab.is-active { border-color: #60a5fa; color: #dbeafe; background: #0b2a57; }
                    .tab-panel { display: none; border: 1px solid #334155; border-radius: 0.9rem; padding: 1.1rem; background: rgba(12, 22, 41, 0.82); backdrop-filter: blur(4px); }
                    .tab-panel.is-active { display: block; }
                    .tab-filter-bar { display: flex; flex-wrap: wrap; gap: 0.6rem; margin-bottom: 1rem; align-items: flex-start; }
                    .tab-filter-bar label { display: flex; align-items: center; gap: 0.4rem; }
                    .js-filter-muscle { min-width: 9rem; }
                    .js-clear-filters { align-self: center; flex: 0 0 auto; white-space: nowrap; }
                    .query-form { display: flex; gap: 0.75rem; align-items: center; flex-wrap: wrap; margin-bottom: 0.75rem; }
                    .query-form label { display: flex; align-items: center; gap: 0.4rem; }
                    .query-output { background: #020617; border: 1px solid #334155; border-radius: 0.5rem; padding: 0.8rem; white-space: pre-wrap; }
                    .add-execution-form { display: flex; flex-direction: column; gap: 0.9rem; max-width: 820px; background: rgba(9,16,32,0.4); border: 1px solid #2f3f59; border-radius: 0.85rem; padding: 0.95rem; }
                    .add-execution-form fieldset { border: 1px solid #334155; border-radius: 0.7rem; padding: 0.7rem; }
                    .segmented { display: flex; flex-wrap: wrap; gap: 0.6rem; }
                    .stacked-row { display: flex; flex-wrap: wrap; gap: 0.7rem; align-items: center; }
                    .add-actions { display: flex; justify-content: flex-end; margin-bottom: 0.7rem; }
                    .new-lift-details { width: min(820px, 100%%); }
                    .new-lift-details > summary { cursor: pointer; color: #93c5fd; margin-bottom: 0.5rem; }
                    .new-lift-form { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 0.6rem; padding: 0.7rem; border: 1px solid #334155; border-radius: 0.7rem; background: rgba(9,16,32,0.5); }
                    .status { border-radius: 0.45rem; padding: 0.55rem 0.7rem; font-weight: 600; }
                    .status.success { border: 1px solid #15803d; color: #86efac; background: #052e16; }
                    .status.error { border: 1px solid #b91c1c; color: #fca5a5; background: #450a0a; }
                    .is-hidden { display: none; }
                  </style>
                </head>
                <body>
                %s
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
