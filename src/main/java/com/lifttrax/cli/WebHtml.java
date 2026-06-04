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
                .daily-dashboard { display: flex; flex-direction: column; gap: 0.65rem; }
                .dashboard-hero { display: flex; justify-content: space-between; align-items: center; gap: 0.75rem; border: 1px solid rgba(34, 197, 94, 0.48); border-left: 4px solid #22c55e; border-radius: 0.6rem; padding: 0.7rem; background: linear-gradient(90deg, rgba(20, 83, 45, 0.34), rgba(30, 41, 59, 0.42)); }
                .dashboard-hero h2 { margin-bottom: 0.18rem; }
                .dashboard-date { color: #facc15; font-weight: 700; margin-bottom: 0.12rem; }
                .dashboard-primary-action { min-width: 6rem; text-align: center; }
                .dashboard-section { border: 1px solid var(--pico-muted-border-color); border-radius: 0.55rem; padding: 0.55rem; background: rgba(7, 13, 26, 0.54); }
                .dashboard-section h3 { margin-bottom: 0.42rem; }
                .dashboard-work-list,
                .dashboard-history-list { display: grid; gap: 0.45rem; list-style: none; padding: 0; margin: 0; }
                .dashboard-work-item,
                .dashboard-history-item { display: grid; grid-template-columns: minmax(170px, 1fr) auto auto; gap: 0.55rem; align-items: center; border: 1px solid rgba(148, 163, 184, 0.34); border-radius: 0.5rem; padding: 0.5rem; background: rgba(15, 23, 42, 0.72); }
                .dashboard-work-item p,
                .dashboard-history-item p { margin-bottom: 0; }
                .dashboard-pill { border: 1px solid rgba(250, 204, 21, 0.48); border-radius: 999px; padding: 0.14rem 0.45rem; color: #fde68a; font-size: 0.78rem; white-space: nowrap; }
                .dashboard-empty { border-style: dashed; border-color: rgba(250, 204, 21, 0.5); }
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
                .quick-log-presets { display: flex; flex-wrap: wrap; gap: 0.35rem; align-items: center; padding: 0.4rem; border: 1px solid rgba(56, 189, 248, 0.45); border-radius: 0.55rem; background: rgba(14, 116, 144, 0.12); }
                .quick-log-label { margin-right: 0.1rem; font-weight: 700; color: #bae6fd; }
                .individual-sets-details { border: 1px dashed var(--pico-muted-border-color); border-radius: 0.45rem; padding: 0.35rem 0.45rem; }
                .individual-sets-details > summary { cursor: pointer; color: var(--pico-primary); }
                .individual-sets-details[open] > summary { margin-bottom: 0.42rem; }
                .segmented { display: flex; flex-wrap: wrap; gap: 0.4rem; }
                .stacked-row { display: flex; flex-wrap: wrap; gap: 0.45rem; align-items: center; }
                .add-actions { display: flex; justify-content: flex-end; margin-bottom: 0.35rem; }
                .new-lift-details { width: min(820px, 100%%); }
                .new-lift-details > summary { cursor: pointer; color: var(--pico-primary); margin-bottom: 0.5rem; }
                .new-lift-form { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 0.45rem; padding: 0.5rem; border: 1px solid var(--pico-muted-border-color); border-radius: 0.7rem; }
                .execution-lift-group { margin-bottom: 0.3rem; border: 1px solid var(--pico-muted-border-color); border-radius: 0.5rem; padding: 0.14rem 0.4rem; }
                .execution-lift-toggle { cursor: pointer; display: block; width: 100%%; padding: 0.22rem 0.16rem; user-select: none; }
                .execution-lift-toggle::-webkit-details-marker { margin-right: 0.35rem; }
                .lift-trends { border: 1px solid var(--pico-muted-border-color); border-radius: 0.5rem; padding: 0.58rem; margin: 0.55rem 0; background: rgba(7, 13, 26, 0.54); }
                .lift-trends-header { display: flex; justify-content: space-between; gap: 0.55rem; align-items: baseline; flex-wrap: wrap; }
                .lift-trends-header h2,
                .lift-trends-header p { margin-bottom: 0.32rem; }
                .lift-trend-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(170px, 1fr)); gap: 0.5rem; }
                .lift-trend-card { min-width: 0; border: 1px solid rgba(148, 163, 184, 0.34); border-radius: 0.5rem; padding: 0.48rem; background: rgba(15, 23, 42, 0.72); }
                .lift-trend-label { margin-bottom: 0.16rem; color: #fde68a; font-size: 0.78rem; font-weight: 700; }
                .lift-trend-value { margin-bottom: 0.12rem; font-weight: 700; overflow-wrap: anywhere; }
                .lift-trend-detail { margin-bottom: 0; overflow-wrap: anywhere; }
                .status { border-radius: 0.4rem; padding: 0.35rem 0.5rem; font-weight: 600; margin-bottom: 0.4rem; }
                .status.success { border: 1px solid #15803d; color: #86efac; background: #052e16; }
                .status.error { border: 1px solid #b91c1c; color: #fca5a5; background: #450a0a; }
                .muted { color: var(--pico-muted-color); }
                .planned-import-form { max-width: 760px; margin: 0.25rem 0 0.55rem; }
                .planned-import-layout { display: grid; grid-template-columns: minmax(260px, 1fr) minmax(220px, 0.85fr); gap: 0.75rem; align-items: stretch; }
                .planned-file-zone { display: grid; grid-template-columns: auto 1fr; gap: 0.6rem; align-items: center; border: 1px dashed #38bdf8; border-radius: 0.65rem; padding: 0.75rem; background: rgba(14, 116, 144, 0.12); }
                .planned-file-input { position: absolute; inline-size: 1px; block-size: 1px; opacity: 0; pointer-events: none; }
                .planned-file-picker { display: inline-flex; align-items: center; justify-content: center; min-height: 2.1rem; padding: 0.28rem 0.68rem; border-radius: 0.45rem; border: 1px solid #22c55e; color: #dcfce7; background: rgba(21, 128, 61, 0.32); cursor: pointer; margin: 0; white-space: nowrap; }
                .planned-file-picker:hover { border-color: #86efac; background: rgba(22, 163, 74, 0.42); }
                .planned-file-meta { min-width: 0; display: flex; flex-direction: column; gap: 0.12rem; }
                .planned-file-meta strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
                .planned-import-actions { display: flex; flex-direction: column; justify-content: center; gap: 0.45rem; border-left: 1px solid var(--pico-muted-border-color); padding-left: 0.75rem; }
                .planned-import-actions .status,
                .planned-import-actions p { margin-bottom: 0; }
                .planned-import-actions .success { color: #86efac; }
                .planned-import-actions .error { color: #fca5a5; }
                .planned-output-form { margin: 0.45rem 0 0.65rem; }
                .planned-output-buttons { align-items: stretch; }
                .planned-week { margin-top: 0.75rem; }
                .planned-day { border-top: 1px solid var(--pico-muted-border-color); padding-top: 0.5rem; margin-top: 0.55rem; }
                .planned-block { border: 1px solid var(--pico-muted-border-color); border-radius: 0.5rem; padding: 0.5rem; margin-bottom: 0.5rem; background: rgba(9, 16, 30, 0.7); }
                .planned-block header h4 { margin-bottom: 0.15rem; }
                .planned-exercise-list { margin-bottom: 0; }
                .planned-exercise-list li { margin-bottom: 0.5rem; }
                .planned-history { display: flex; flex-wrap: wrap; gap: 0.35rem 0.8rem; margin-top: 0.18rem; color: var(--pico-muted-color); }
                .planned-notes { margin-bottom: 0.45rem; }
                .planned-day .compact-actions { margin-top: 0.4rem; }
                .planned-session-form { display: flex; flex-direction: column; gap: 0.65rem; }
                .session-date { max-width: 15rem; }
                .session-block { border: 1px solid var(--pico-muted-border-color); border-radius: 0.6rem; padding: 0.58rem; background: rgba(9, 16, 30, 0.7); }
                .session-block.is-current { border-color: rgba(34, 197, 94, 0.58); }
                .session-block-label { display: inline-block; margin-bottom: 0.22rem; color: #86efac; font-size: 0.76rem; font-weight: 700; text-transform: uppercase; letter-spacing: 0.04em; }
                .session-exercise { border: 1px solid rgba(148, 163, 184, 0.34); border-radius: 0.5rem; padding: 0.5rem; margin-top: 0.5rem; background: rgba(15, 23, 42, 0.72); }
                .session-exercise > header { display: flex; justify-content: space-between; align-items: end; gap: 0.5rem; }
                .session-exercise > header h3 { margin-bottom: 0.42rem; }
                .session-exercise.is-skipped,
                .session-set.is-skipped { opacity: 0.62; }
                .session-set { border: 1px dashed var(--pico-muted-border-color); border-radius: 0.45rem; padding: 0.4rem; margin: 0.4rem 0; }
                .session-set p { margin-bottom: 0.28rem; }
                .session-set-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(115px, 1fr)); gap: 0.35rem; }
                .session-set-grid label { min-width: 0; }
                .session-set-grid input,
                .session-set-grid select { width: 100%%; }
                .save-workout-session-btn { position: sticky; bottom: 0.35rem; margin-bottom: 0; box-shadow: 0 0 0 2px rgba(7, 13, 26, 0.82); }
                @media (max-width: 720px) {
                  .dashboard-hero { align-items: stretch; flex-direction: column; }
                  .dashboard-work-item,
                  .dashboard-history-item { grid-template-columns: 1fr; align-items: start; }
                  .dashboard-primary-action,
                  .dashboard-work-item .compact-btn,
                  .dashboard-history-item .compact-btn { width: 100%%; }
                  .planned-import-layout { grid-template-columns: 1fr; }
                  .planned-import-actions { border-left: 0; border-top: 1px solid var(--pico-muted-border-color); padding-left: 0; padding-top: 0.65rem; }
                  main.container { padding-left: 0.4rem; padding-right: 0.4rem; }
                  .tabs { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); }
                  .tab { min-width: 0; width: 100%%; }
                  .tab-panel { padding: 0.45rem; }
                  .tab-filter-bar { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); align-items: end; }
                  .tab-filter-bar label { min-width: 0; flex-direction: column; align-items: stretch; gap: 0.2rem; }
                  .tab-filter-bar label:first-child { grid-column: 1 / -1; }
                  .tab-filter-bar input,
                  .tab-filter-bar select { min-width: 0; width: 100%%; }
                  .js-clear-filters { align-self: end; width: 100%%; }
                  .add-execution-form { min-width: 0; border: 0; padding: 0; }
                  .add-execution-form fieldset { min-width: 0; }
                  .add-execution-form .stacked-row { align-items: stretch; }
                  .add-execution-form .stacked-row > label { flex: 1 1 8rem; min-width: 0; }
                  .add-execution-form .stacked-row input,
                  .add-execution-form .stacked-row select { width: 100%%; }
                  .add-execution-form .segmented { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0.32rem; }
                  .add-execution-form .segmented label { display: flex; gap: 0.35rem; align-items: center; min-width: 0; margin: 0; padding: 0.38rem; border: 1px solid rgba(148, 163, 184, 0.34); border-radius: 0.42rem; background: rgba(15, 23, 42, 0.72); }
                  .add-execution-form .segmented input[type='radio'],
                  .add-execution-form .segmented input[type='checkbox'] { flex: 0 0 auto; width: auto; margin: 0; }
                  .quick-log-presets { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); }
                  .quick-log-label { grid-column: 1 / -1; }
                  .quick-log-presets .compact-btn { width: 100%%; }
                  .save-execution-btn { position: sticky; bottom: 0.35rem; width: 100%%; margin-bottom: 0; box-shadow: 0 0 0 2px rgba(7, 13, 26, 0.82); }
                  .session-exercise > header { align-items: stretch; flex-direction: column; }
                  .session-exercise > header label { width: 100%%; }
                  .session-set-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
                  .save-workout-session-btn { width: 100%%; }
                }
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
