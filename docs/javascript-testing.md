# JavaScript verification

Install Node **24.22.1** and pnpm **11.25.0**, then run
`pnpm install --frozen-lockfile` from the repository root. `package.json` pins
the tools and jsdom; `pnpm-lock.yaml` locks transitive dependencies. CI runs
`pnpm audit --audit-level=high`. The production application needs no Node runtime.

```powershell
.\gradlew.bat javascriptTest
.\gradlew.bat javascriptStaticAnalysis
.\gradlew.bat qualityGate
.\gradlew.bat browserAcceptance
```

Use `./gradlew` on Linux. The fast suite is part of `qualityGate`. It generates
HTML using production renderers against disposable H2 data, checks every
browser script's syntax, and runs Node's built-in test runner with jsdom.
Neither fixture generation nor acceptance reads local configuration or the
user's Postgres database. All generated files stay under ignored `build/` paths.
The same gate also enforces risky-language, global, size/complexity, and
duplicate-code policy across production and harness sources; see
[JavaScript static analysis](javascript-static-analysis.md).

After generating fixtures, run a single test without aggregate coverage:

```powershell
node --test --test-name-pattern="preview synchronizes" scripts/javascript/navigation.test.mjs
```

Or use `node scripts/javascript-test.mjs --test-name-pattern="preview synchronizes"`
to retain coverage checks; a subset can fail the aggregate threshold. Regenerate
fixtures with `javascriptTest` after editing production scripts. The harness
rejects stale or unrecognized scripts instead of running a copied implementation.

DOM events, forms, validation, Storage, and history use jsdom. Network responses,
OS dialogs, missing layout APIs, and long retry delays have controlled
substitutes. Chromium verifies native keyboard input, layout, and HTTP behavior.

## Maintained script inventory

All maintained scripts are in `src/main/resources/com/lifttrax/cli/browser/`.
`ClasspathText.script` embeds those files unchanged; the service-worker route
returns its resource directly. An inventory test rejects new JavaScript
literals in Java/HTML and inline event attributes. New scripts need behavior
tests, an inventory entry, and a measured entry in the coverage report.

| Script | Fast behavior coverage | Real-browser coverage and reason |
| --- | --- | --- |
| `planned-session.js` | `session.test.mjs`: block progression/locking, sets, metrics, validation, serialization, owner scopes, reload, legacy migration, malformed/blocked/dropped storage, load-last success/account check/edit race, duplicate submission, bounded wake/finish retries, cleanup | `shell`, `workout-recovery`: desktop/phone edit, skip, reload, resume, block HTTP save and finish; both layers protect durable work |
| `add-execution.js` | `quick-log.test.mjs`: capture-handler integration, set copies/removal/clear, metric and loading variants | `workout-recovery`: native quick-log submit without JavaScript; both layers protect form fallback |
| `tabbed-layout.js` | `quick-log.test.mjs`, `history.test.mjs`: account-scoped quick-log storage/prefill/cleanup, filters, focus, wave controls, new-lift muscles, lazy history/account check, set-row edit/cancel/validation | `shell`: primary/secondary links, deep links, keyboard, history, responsive layout; native navigation/layout require Chromium |
| `preview-weeks.js` | `navigation.test.mjs`: file order, boundaries, direct/unknown fragments, history/pageshow, focus, single week | `preview-weeks`: Enter/Space/select keys, back/forward/reload, visible focus, no-script links at desktop/phone sizes; native focus/scroll need both layers |
| `work-along.js` | `navigation.test.mjs`: week/day synchronization, empty week, submitted day | `workout-recovery`: real chooser POST and no-script day selector |
| `lift-import.js` | `navigation.test.mjs`: select-all, partial/none/all, disabled duplicates, pageshow | `shell`: shared-import route; boundary combinations belong in fast tests |
| `resume.js` | `resume.test.mjs`: invalid/missing/foreign/blocked backup, failed request, retry, replacement page | `workout-recovery`: real authenticated reload/resume fetch |
| `resume-panel.js` | `resume.test.mjs`: ownership, newest-first order, safe text, damaged entries, discard cancellation/success/failure | `shell`, `workout-recovery`: dashboard entry and real resume link |
| `saved-session.js` | `resume.test.mjs`: key-specific cleanup, blocked storage | `workout-recovery`: confirmed completion cleanup |
| `workout-import.js` | `quick-log.test.mjs`: FileReader success/invalid JSON/reset/empty submission | `shell`: import reachability; deterministic file boundaries use fast tests |
| `page-actions.js` | `page-actions.test.mjs`: deletion confirmations, catalog muscle fields, print dispatch, catalog file success/failure/empty selection | `shell`: catalog/library navigation; OS print dispatch uses a substitute |
| `service-worker-registration.js` | `service-worker.test.mjs`: supported/unsupported API, rejected registration | `shell`: actual platform registration |
| `service-worker.js` | `service-worker.test.mjs`: install/activate, exact public assets, cache hit/miss, online/offline navigation, ignored POST/cross-origin/data requests; no authenticated HTML or account data is stored | `workout-recovery`: inspect real cache contents after authenticated training/account navigation; both layers enforce the privacy boundary |

## Coverage ratchet

The current verified baseline is **85.31% lines**, **69.47% branches**,
and **84.77% functions** across all 13 scripts, with **97 focused tests**.
The enforced floors are **84% lines** and **69% branches**.

The runner emits line, branch, and function coverage to the console and LCOV to
`build/reports/javascript/lcov.info`. Only production browser `.js` files count;
generated fixtures, tests, jsdom, and acceptance infrastructure are excluded.
Scripts missing from V8's report fail the full suite, preventing unexecuted new
files from silently improving its ratio. Keep the line and branch thresholds
in `scripts/javascript-test.mjs` non-decreasing. Do not lower thresholds or add
blanket exclusions to pass a change.

## Chromium acceptance

Chrome must be installed. The default is the standard Windows Chrome path or
`google-chrome` on Linux; set `CHROME_PATH` for another installation.
`browserAcceptance` starts an authenticated H2 fixture on an ephemeral port and
a fresh browser profile per suite, then closes both. It runs `shell`,
`preview-weeks`, `workout-recovery`, and `harness-probes`. The probes deliberately
trigger syntax/runtime errors, unhandled rejections, console errors, a missing
resource, and a timeout to verify detection. To run one suite locally:

```powershell
.\gradlew.bat browserAcceptance -PbrowserSuite=preview-weeks
```

Acceptance covers 1280-by-900 desktop and 390-by-844 phone flows. Syntax errors,
uncaught exceptions (including unhandled promise rejections), console errors,
HTTP errors, non-cancelled resource failures, command/event timeouts, and the
180-second suite deadline fail the run. Fragment navigation waits for observable
state because it has no page-load event. Failures include a scenario, page,
error details and screenshot under `build/browser-shell-check/`. CI retains
these files and JavaScript coverage alongside JUnit, PMD and JaCoCo reports.
The existing external stylesheet must be reachable; its load errors also fail.
