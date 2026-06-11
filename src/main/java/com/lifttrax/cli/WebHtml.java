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
                  --pico-font-size: 84%;
                  --pico-line-height: 1.25;
                  --pico-form-element-spacing-vertical: 0.28rem;
                  --pico-form-element-spacing-horizontal: 0.45rem;
                  --pico-spacing: 0.65rem;
                }
                body {
                  background: radial-gradient(circle at top, #0b1736, #0a1022 45%, #070d1a);
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
                .danger { color: #fca5a5; border-color: rgba(248, 113, 113, 0.55); }
                .query-output { border: 1px solid var(--pico-muted-border-color); border-radius: 0.45rem; padding: 0.55rem; white-space: pre-wrap; }
                .add-execution-form { display: flex; flex-direction: column; gap: 0.48rem; max-width: 900px; border: 1px solid var(--pico-muted-border-color); border-radius: 0.65rem; padding: 0.58rem; }
                .add-execution-form fieldset { border: 1px solid var(--pico-muted-border-color); border-radius: 0.55rem; padding: 0.4rem; margin-bottom: 0.2rem; }
                .quick-log-presets { display: flex; flex-wrap: wrap; gap: 0.35rem; align-items: center; padding: 0.4rem; border: 1px solid rgba(56, 189, 248, 0.45); border-radius: 0.55rem; background: rgba(14, 116, 144, 0.12); }
                .quick-log-label { margin-right: 0.1rem; font-weight: 700; color: #bae6fd; }
                .set-entry-mode { border-color: rgba(34, 197, 94, 0.42); background: rgba(20, 83, 45, 0.14); }
                .set-entry-mode-choice { margin-bottom: 0.4rem; }
                .individual-sets-details { border: 1px dashed var(--pico-muted-border-color); border-radius: 0.45rem; padding: 0.35rem 0.45rem; }
                .individual-sets-details > summary { cursor: pointer; color: var(--pico-primary); }
                .individual-sets-details[open] > summary { margin-bottom: 0.42rem; }
                .segmented { display: flex; flex-wrap: wrap; gap: 0.4rem; }
                .stacked-row { display: flex; flex-wrap: wrap; gap: 0.45rem; align-items: center; }
                .add-actions { display: flex; justify-content: flex-end; margin-bottom: 0.35rem; }
                .new-lift-details { width: min(820px, 100%); }
                .new-lift-details > summary { cursor: pointer; color: var(--pico-primary); margin-bottom: 0.5rem; }
                .new-lift-form { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 0.45rem; padding: 0.5rem; border: 1px solid var(--pico-muted-border-color); border-radius: 0.7rem; }
                .execution-lift-group { margin-bottom: 0.3rem; border: 1px solid var(--pico-muted-border-color); border-radius: 0.5rem; padding: 0.14rem 0.4rem; }
                .execution-lift-toggle { cursor: pointer; display: block; width: 100%; padding: 0.22rem 0.16rem; user-select: none; }
                .execution-lift-toggle::-webkit-details-marker { margin-right: 0.35rem; }
                .execution-list { list-style: none; padding-left: 0; margin-left: 0; }
                .execution-item { min-width: 0; border-bottom: 1px solid rgba(148, 163, 184, 0.18); padding: 0.18rem 0; }
                .execution-row-actions { display: flex; gap: 0.35rem; align-items: center; flex: 0 0 auto; }
                .execution-row-actions .compact-btn { min-inline-size: 4.4rem; min-height: 2rem; }
                .execution-row-actions .danger { margin-left: 0.35rem; }
                .js-exec-view,
                .execution-edit-form,
                .js-edit-sets,
                .js-set-row { min-width: 0; }
                .execution-text { min-width: 0; overflow-wrap: anywhere; }
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
                .planned-workalong-form { max-width: 620px; display: flex; flex-direction: column; gap: 0.55rem; }
                .planned-workalong-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0.55rem; }
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
                .session-block-nav { position: sticky; top: 0.35rem; z-index: 2; display: grid; grid-template-columns: minmax(150px, 1fr) minmax(130px, 1fr) auto; gap: 0.55rem; align-items: center; border: 1px solid rgba(34, 197, 94, 0.58); border-radius: 0.6rem; padding: 0.48rem; background: rgba(7, 13, 26, 0.96); box-shadow: 0 0 0 2px rgba(7, 13, 26, 0.82); }
                .session-block-summary { display: flex; flex-direction: column; gap: 0.08rem; min-width: 0; }
                .session-block-summary span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
                .session-block-nav progress { margin: 0; }
                .session-block-actions { display: flex; gap: 0.35rem; }
                .session-block-actions button { min-height: 2.35rem; margin: 0; white-space: nowrap; }
                .session-block { border: 1px solid var(--pico-muted-border-color); border-radius: 0.6rem; padding: 0.58rem; background: rgba(9, 16, 30, 0.7); }
                .session-block.is-current { border-color: rgba(34, 197, 94, 0.58); }
                .session-block-label { display: inline-block; margin-bottom: 0.22rem; color: #86efac; font-size: 0.76rem; font-weight: 700; text-transform: uppercase; letter-spacing: 0.04em; }
                .session-exercise { border: 1px solid rgba(148, 163, 184, 0.34); border-radius: 0.5rem; padding: 0.5rem; margin-top: 0.5rem; background: rgba(15, 23, 42, 0.72); }
                .session-exercise.is-swapped { border-color: rgba(56, 189, 248, 0.72); }
                .session-exercise > header { display: flex; justify-content: space-between; align-items: end; gap: 0.5rem; }
                .session-exercise > header h3 { margin-bottom: 0.42rem; }
                .session-swap-controls { margin: 0.15rem 0 0.45rem; }
                .session-swap-controls button { margin-bottom: 0; }
                .session-swap-panel { border-left: 2px solid #38bdf8; margin-top: 0.4rem; padding: 0.25rem 0 0.1rem 0.55rem; }
                .session-swap-panel label,
                .session-swap-panel p { margin-bottom: 0.28rem; }
                .session-lift-note { border-left: 3px solid #22c55e; padding: 0.32rem 0.5rem; margin: 0.35rem 0 0.45rem; background: rgba(20, 83, 45, 0.18); color: #dcfce7; overflow-wrap: anywhere; }
                .session-history { display: flex; flex-wrap: wrap; gap: 0.32rem 0.75rem; border: 1px solid rgba(250, 204, 21, 0.36); border-radius: 0.45rem; padding: 0.36rem 0.46rem; margin: 0.35rem 0 0.45rem; background: rgba(113, 63, 18, 0.18); color: #fde68a; }
                .session-history span { overflow-wrap: anywhere; }
                .session-exercise.is-skipped,
                .session-set.is-skipped { opacity: 0.62; }
                .session-set { border: 1px dashed var(--pico-muted-border-color); border-radius: 0.45rem; padding: 0.4rem; margin: 0.4rem 0; }
                .session-set p { margin-bottom: 0.28rem; }
                .session-set-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(115px, 1fr)); gap: 0.35rem; }
                .session-set-grid label { min-width: 0; }
                .session-set-grid input,
                .session-set-grid select { width: 100%; }
                .save-execution-btn,
                .save-workout-session-btn { min-height: 2.75rem; }
                .save-workout-session-btn { position: sticky; bottom: 0.35rem; margin-bottom: 0; box-shadow: 0 0 0 2px rgba(7, 13, 26, 0.82); }
                @media (max-width: 720px) {
                  .dashboard-hero { align-items: stretch; flex-direction: column; }
                  .dashboard-work-item,
                  .dashboard-history-item { grid-template-columns: 1fr; align-items: start; }
                  .dashboard-primary-action,
                  .dashboard-work-item .compact-btn,
                  .dashboard-history-item .compact-btn { width: 100%; }
                  .planned-import-layout { grid-template-columns: 1fr; }
                  .planned-import-actions { border-left: 0; border-top: 1px solid var(--pico-muted-border-color); padding-left: 0; padding-top: 0.65rem; }
                  .planned-workalong-grid { grid-template-columns: 1fr; }
                  main.container { padding-left: 0.4rem; padding-right: 0.4rem; }
                  .tabs { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); }
                  .tab { min-width: 0; width: 100%; }
                  .tab-panel { padding: 0.45rem; }
                  .tab-filter-bar { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); align-items: end; }
                  .tab-filter-bar label { min-width: 0; flex-direction: column; align-items: stretch; gap: 0.2rem; }
                  .tab-filter-bar label:first-child { grid-column: 1 / -1; }
                  .tab-filter-bar input,
                  .tab-filter-bar select { min-width: 0; width: 100%; }
                  .js-clear-filters { align-self: end; width: 100%; }
                  .add-execution-form { min-width: 0; border: 0; padding: 0; }
                  .add-execution-form fieldset { min-width: 0; }
                  .add-execution-form .stacked-row { align-items: stretch; }
                  .add-execution-form .stacked-row > label { flex: 1 1 8rem; min-width: 0; }
                  .add-execution-form .stacked-row input,
                  .add-execution-form .stacked-row select { width: 100%; }
                  .add-execution-form .segmented { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0.32rem; }
                  .add-execution-form .segmented label { display: flex; gap: 0.35rem; align-items: center; min-width: 0; margin: 0; padding: 0.38rem; border: 1px solid rgba(148, 163, 184, 0.34); border-radius: 0.42rem; background: rgba(15, 23, 42, 0.72); }
                  .add-execution-form .segmented input[type='radio'],
                  .add-execution-form .segmented input[type='checkbox'] { flex: 0 0 auto; width: auto; margin: 0; }
                  .quick-log-presets { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); }
                  .quick-log-label { grid-column: 1 / -1; }
                  .quick-log-presets .compact-btn { width: 100%; }
                  .save-execution-btn { position: sticky; bottom: 0.35rem; width: 100%; margin-bottom: 0; box-shadow: 0 0 0 2px rgba(7, 13, 26, 0.82); }
                  .execution-list { display: grid; gap: 0.38rem; }
                  .execution-item { margin: 0 !important; padding: 0.42rem; border: 1px solid rgba(148, 163, 184, 0.34); border-radius: 0.5rem; background: rgba(15, 23, 42, 0.72); }
                  .js-exec-view { align-items: flex-start !important; flex-wrap: wrap !important; }
                  .execution-text { flex: 1 1 100% !important; white-space: normal !important; overflow: visible !important; text-overflow: clip !important; }
                  .execution-row-actions { flex: 1 1 100%; width: 100%; padding-top: 0.32rem; border-top: 1px solid rgba(148, 163, 184, 0.22); }
                  .execution-row-actions .compact-btn { flex: 1 1 0; min-height: 2.45rem; }
                  .execution-row-actions .danger { flex: 0 0 36%; margin-left: auto; }
                  .execution-edit-meta { display: grid !important; grid-template-columns: 1fr; gap: 0.38rem; overflow-x: visible !important; width: 100%; }
                  .execution-edit-meta label,
                  .execution-edit-meta input { width: 100%; min-width: 0; }
                  .js-set-row { display: grid !important; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0.35rem !important; align-items: end !important; overflow-x: visible !important; }
                  .js-set-row select,
                  .js-set-row input { width: 100% !important; min-width: 0; }
                  .js-set-weight,
                  .js-remove-set { grid-column: 1 / -1; }
                  .session-exercise > header { align-items: stretch; flex-direction: column; }
                  .session-exercise > header label { width: 100%; }
                  .planned-session-form { padding-bottom: 4.4rem; }
                  .session-block-nav { position: sticky; top: auto; bottom: 0.35rem; z-index: 4; grid-template-columns: 1fr; }
                  .session-block-actions { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); }
                  .session-block-actions button { width: 100%; }
                  .session-set-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
                  .save-workout-session-btn { bottom: 4rem; width: 100%; }
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
