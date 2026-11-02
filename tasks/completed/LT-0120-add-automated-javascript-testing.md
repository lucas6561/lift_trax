---
id: LT-0120
title: Add automated JavaScript testing
status: done
track: quality
priority: high
effort: large
created: 2026-11-01
updated: 2026-11-01
owner: unassigned
depends_on: [LT-0119]
---

# LT-0120: Add automated JavaScript testing

## Why

LiftTrax relies on browser JavaScript for active-workout progression, offline
draft persistence and recovery, preview-week navigation, work-along week and day
selection, exercise import selection, and service-worker behavior. The existing
Java tests often prove that expected script text was rendered, and the Chromium
acceptance check exercises important paths and fails on uncaught runtime errors,
but neither layer provides fast, focused coverage of JavaScript state changes,
DOM events, storage failures, or edge cases.

A script can therefore be present, syntactically valid, and free of uncaught
errors on the happy path while still handling an interaction incorrectly.

## Outcome

Maintained browser JavaScript has a deterministic automated test suite with
focused behavior tests and a smaller set of real-Chromium acceptance scenarios.
JavaScript regressions fail the same local and CI quality gate used by the Java
application, while server-rendered links, forms, and JavaScript-disabled
fallbacks continue to work.

## Scope

- In scope: inventorying maintained browser scripts; extracting testable modules
  or seams where necessary; JavaScript syntax and behavior tests; DOM event
  tests; storage and history edge cases; service-worker behavior; desktop and
  phone-sized Chromium scenarios; coverage reporting; Gradle and CI integration;
  contributor documentation.
- Out of scope: replacing the server-rendered application with a client-side
  framework, requiring JavaScript for core navigation or form submission,
  redesigning the affected interfaces, or treating Java string-presence
  assertions as proof of browser behavior.

## Acceptance criteria

- [x] Inventory every maintained browser script and map each behavior to a fast
      JavaScript test, a real-browser acceptance scenario, or a documented reason
      that it needs both.
- [x] Establish a pinned, reproducible JavaScript test command that works on
      Windows and CI. Prefer the Node built-in test runner and the existing
      Chromium harness; if dependencies are necessary, commit a lockfile and
      include them in dependency-vulnerability scanning.
- [x] Move shared or decision-heavy browser logic into directly testable
      JavaScript modules or equivalent seams without making the application
      dependent on JavaScript for its progressive-enhancement fallbacks.
- [x] Add focused tests for active-workout block progression, set editing,
      validation, draft serialization, account-scoped storage keys, reload
      recovery, successful cleanup, malformed or unavailable storage, and
      duplicate-submission protection.
- [x] Add focused tests for preview-week selection and boundaries, URL fragment
      and browser-history synchronization, focus behavior, work-along week/day
      synchronization, exercise import selection, and resume retry/error states.
- [x] Test service-worker installation, activation, fetch behavior, and cache
      contents, including proof that authenticated HTML and account data are not
      stored in the shared application shell cache.
- [x] DOM tests exercise actual event dispatch and observable state rather than
      asserting only that handler source text, selectors, or function names are
      present in rendered HTML.
- [x] Real Chromium checks cover representative desktop and 390-by-844 phone
      flows, keyboard operation, reload, back/forward navigation, and
      JavaScript-disabled fallbacks.
- [x] The harness fails on JavaScript syntax errors, uncaught exceptions,
      unhandled promise rejections, unexpected console errors, failed resource
      loads, and test timeouts with an actionable page and scenario name.
- [x] Produce JavaScript line and branch coverage for directly testable modules,
      document the initial baseline, and set a non-decreasing threshold. Do not
      inflate coverage with generated fixtures or the test harness itself.
- [x] Add a Gradle task for the fast JavaScript suite and make it a dependency of
      `qualityGate`; run the real-browser suite in CI with retained diagnostics
      such as console output and a failure screenshot.
- [x] Document how to run fast JavaScript tests, browser acceptance checks, and
      a single test or scenario locally.
- [x] Existing Java tests, the new JavaScript suite, the Chromium acceptance
      suite, and `./gradlew.bat qualityGate` pass.

## Notes

The initial inventory should include scripts rendered by `LiftImportHtml`,
`PlannedWorkoutHtml`, `PlannedWorkoutPreviewNavigationHtml`,
`PlannedWorkoutResumeHtml`, `PlannedWorkoutSessionHtml`, `WebHtml`, and
`WebAssetRoutes`, plus any browser behavior emitted by their collaborators.

`scripts/application-shell-browser-check.mjs` and
`scripts/preview-week-browser-check.mjs` are useful acceptance infrastructure,
but they should not become the only place where small JavaScript decisions are
tested. Keep acceptance scenarios product-oriented and put boundary combinations
in the fast suite.

## Implementation and verification

Completed on 2026-11-01. Maintained browser logic now lives in 13 production
JavaScript resources embedded by the existing server renderers. The inventory,
commands, coverage policy, and behavior mapping are in
[JavaScript testing](../../docs/javascript-testing.md).

- Added 96 focused Node/jsdom tests against production-rendered HTML, with
  syntax checks and an inventory guard against new inline JavaScript.
- Added reproducible Node/pnpm setup, a locked jsdom dependency, vulnerability
  scanning, Gradle quality-gate integration, CI acceptance, and retained reports.
- Fixed malformed/foreign draft recovery, concurrent block submissions,
  inactive-field validation, confirmed-save cleanup with blocked storage,
  history cancellation clearing restored set values, and no-script Work Along
  day submission. Public cache requests now explicitly reject foreign origins.
- Moved the small catalog, confirmation, and print handlers into the same
  tested inventory while preserving server links/forms and JSON paste fallback.

Verification:

- Full Windows quality gate passed: 404 Java tests, zero failures/errors,
  two environment skips; 92.637% JaCoCo instruction coverage; Spotless and PMD
  production/test analysis passed without new exclusions.
- JavaScript: 96 passed, zero failures; 85.31% line, 69.47% branch, 84.77%
  function coverage. Enforced non-decreasing floors: 84% lines and 69% branches.
- Chromium shell, preview-week, and workout-recovery acceptance passed at
  1280x900 and 390x844, including keyboard, history, reload/resume, block save,
  final cleanup, JavaScript-disabled links and forms, and real public-cache
  contents with no authenticated identity or training data in the offline page.
- Chromium harness probes detected an actual syntax error, uncaught exception,
  unhandled rejection, console error, missing resource, and actionable timeout.
- Locked dependency audit: no known vulnerabilities. Diff whitespace check passed.
- Verification used isolated H2 fixtures. Existing exports and the unrelated
  LT-0099 task move were preserved. Changes were not committed or pushed.

Local evidence: build/lt120-quality-verified.log, build/lt120-quality-complete.log,
build/lt120-verification2.log, build/lt120-recovery-final.log,
build/lt120-probes-verified.log, build/reports/javascript/lcov.info, and
build/browser-shell-check/ screenshots and scenario logs.

## Complete changed-path list

60 changed paths (the task move is shown as its old and new paths).

| Change | Path |
| --- | --- |
| Modified | `.github/workflows/hosted-deploy.yml` |
| Modified | `.github/workflows/quality.yml` |
| Modified | `.gitignore` |
| Modified | `JAVA_PROJECT.md` |
| Modified | `README.md` |
| Modified | `build.gradle` |
| Modified | `docs/code-organization-and-quality.md` |
| Modified | `scripts/application-shell-browser-check.mjs` |
| Modified | `src/main/java/com/lifttrax/cli/AddExecutionHtml.java` |
| Modified | `src/main/java/com/lifttrax/cli/ClasspathText.java` |
| Modified | `src/main/java/com/lifttrax/cli/ExecutionListHtml.java` |
| Modified | `src/main/java/com/lifttrax/cli/ExerciseCatalogImportHtml.java` |
| Modified | `src/main/java/com/lifttrax/cli/LiftImportHtml.java` |
| Modified | `src/main/java/com/lifttrax/cli/PlannedWorkoutHtml.java` |
| Modified | `src/main/java/com/lifttrax/cli/PlannedWorkoutPreviewNavigationHtml.java` |
| Modified | `src/main/java/com/lifttrax/cli/PlannedWorkoutPrintHtml.java` |
| Modified | `src/main/java/com/lifttrax/cli/PlannedWorkoutResumeHtml.java` |
| Modified | `src/main/java/com/lifttrax/cli/PlannedWorkoutSessionHtml.java` |
| Modified | `src/main/java/com/lifttrax/cli/SavedWorkoutHtml.java` |
| Modified | `src/main/java/com/lifttrax/cli/WebAssetRoutes.java` |
| Modified | `src/main/java/com/lifttrax/cli/WebHtml.java` |
| Modified | `src/main/java/com/lifttrax/cli/WebUiRenderer.java` |
| Removed/moved | `src/main/resources/com/lifttrax/cli/add-execution-fallback-script.html` |
| Modified | `src/main/resources/com/lifttrax/cli/planned-workout-resume-panel.html` |
| Removed/moved | `src/main/resources/com/lifttrax/cli/planned-workout-session-script.html` |
| Modified | `src/main/resources/com/lifttrax/cli/tabbed-layout.html` |
| Modified | `src/main/resources/planned-workout-import.html` |
| Modified | `src/test/java/com/lifttrax/cli/ApplicationShellBrowserFixture.java` |
| Modified | `src/test/java/com/lifttrax/cli/WebUiRendererTest.java` |
| Removed/moved | `tasks/backlog/LT-0120-add-automated-javascript-testing.md` |
| Added | `docs/javascript-testing.md` |
| Added | `package.json` |
| Added | `pnpm-lock.yaml` |
| Added | `scripts/browser-acceptance.mjs` |
| Added | `scripts/javascript-test.mjs` |
| Added | `scripts/javascript/dom.mjs` |
| Added | `scripts/javascript/history.test.mjs` |
| Added | `scripts/javascript/inventory.test.mjs` |
| Added | `scripts/javascript/navigation.test.mjs` |
| Added | `scripts/javascript/page-actions.test.mjs` |
| Added | `scripts/javascript/quick-log.test.mjs` |
| Added | `scripts/javascript/resume.test.mjs` |
| Added | `scripts/javascript/service-worker.test.mjs` |
| Added | `scripts/javascript/session.test.mjs` |
| Added | `scripts/workout-recovery-browser-check.mjs` |
| Added | `src/main/resources/com/lifttrax/cli/browser/add-execution.js` |
| Added | `src/main/resources/com/lifttrax/cli/browser/lift-import.js` |
| Added | `src/main/resources/com/lifttrax/cli/browser/page-actions.js` |
| Added | `src/main/resources/com/lifttrax/cli/browser/planned-session.js` |
| Added | `src/main/resources/com/lifttrax/cli/browser/preview-weeks.js` |
| Added | `src/main/resources/com/lifttrax/cli/browser/resume-panel.js` |
| Added | `src/main/resources/com/lifttrax/cli/browser/resume.js` |
| Added | `src/main/resources/com/lifttrax/cli/browser/saved-session.js` |
| Added | `src/main/resources/com/lifttrax/cli/browser/service-worker-registration.js` |
| Added | `src/main/resources/com/lifttrax/cli/browser/service-worker.js` |
| Added | `src/main/resources/com/lifttrax/cli/browser/tabbed-layout.js` |
| Added | `src/main/resources/com/lifttrax/cli/browser/work-along.js` |
| Added | `src/main/resources/com/lifttrax/cli/browser/workout-import.js` |
| Added | `src/test/java/com/lifttrax/cli/BrowserScriptFixtures.java` |
| Added | `tasks/completed/LT-0120-add-automated-javascript-testing.md` |
