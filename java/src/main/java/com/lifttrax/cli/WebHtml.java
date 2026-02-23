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
                    body { font-family: sans-serif; margin: 2rem auto; max-width: 980px; padding: 0 1rem; background: #111827; color: #f9fafb; }
                    a { color: #60a5fa; }
                    input, button, select { padding: 0.5rem; border-radius: 0.35rem; border: 1px solid #374151; background: #1f2937; color: #f9fafb; }
                    button { cursor: pointer; }
                    table { border-collapse: collapse; width: 100%%; margin-top: 1rem; }
                    th, td { border: 1px solid #374151; padding: 0.45rem; vertical-align: top; text-align: left; }
                    code { background: #1f2937; padding: 0.15rem 0.35rem; border-radius: 0.25rem; }
                    li { margin-bottom: 0.35rem; }
                    .tabs { display: flex; gap: 0.5rem; margin-bottom: 1rem; flex-wrap: wrap; }
                    .tab { border: 1px solid #374151; background: #111827; color: #d1d5db; padding: 0.45rem 0.8rem; border-radius: 999px; }
                    .tab.is-active { border-color: #60a5fa; color: #60a5fa; }
                    .tab-panel { display: none; border: 1px solid #374151; border-radius: 0.6rem; padding: 1rem; background: #0f172a; }
                    .tab-panel.is-active { display: block; }
                    .tab-filter-bar { display: flex; flex-wrap: wrap; gap: 0.6rem; margin-bottom: 0.75rem; align-items: flex-start; }
                    .tab-filter-bar label { display: flex; align-items: center; gap: 0.4rem; }
                    .js-filter-muscle { min-width: 9rem; }
                    .js-clear-filters { align-self: center; flex: 0 0 auto; white-space: nowrap; }
                    .query-form { display: flex; gap: 0.75rem; align-items: center; flex-wrap: wrap; margin-bottom: 0.75rem; }
                    .query-form label { display: flex; align-items: center; gap: 0.4rem; }
                    .query-output { background: #020617; border: 1px solid #334155; border-radius: 0.5rem; padding: 0.8rem; white-space: pre-wrap; }
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
