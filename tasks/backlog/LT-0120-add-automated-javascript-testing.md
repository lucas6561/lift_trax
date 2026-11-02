---
id: LT-0120
title: Add automated JavaScript testing
status: ready
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

- [ ] Inventory every maintained browser script and map each behavior to a fast
      JavaScript test, a real-browser acceptance scenario, or a documented reason
      that it needs both.
- [ ] Establish a pinned, reproducible JavaScript test command that works on
      Windows and CI. Prefer the Node built-in test runner and the existing
      Chromium harness; if dependencies are necessary, commit a lockfile and
      include them in dependency-vulnerability scanning.
- [ ] Move shared or decision-heavy browser logic into directly testable
      JavaScript modules or equivalent seams without making the application
      dependent on JavaScript for its progressive-enhancement fallbacks.
- [ ] Add focused tests for active-workout block progression, set editing,
      validation, draft serialization, account-scoped storage keys, reload
      recovery, successful cleanup, malformed or unavailable storage, and
      duplicate-submission protection.
- [ ] Add focused tests for preview-week selection and boundaries, URL fragment
      and browser-history synchronization, focus behavior, work-along week/day
      synchronization, exercise import selection, and resume retry/error states.
- [ ] Test service-worker installation, activation, fetch behavior, and cache
      contents, including proof that authenticated HTML and account data are not
      stored in the shared application shell cache.
- [ ] DOM tests exercise actual event dispatch and observable state rather than
      asserting only that handler source text, selectors, or function names are
      present in rendered HTML.
- [ ] Real Chromium checks cover representative desktop and 390-by-844 phone
      flows, keyboard operation, reload, back/forward navigation, and
      JavaScript-disabled fallbacks.
- [ ] The harness fails on JavaScript syntax errors, uncaught exceptions,
      unhandled promise rejections, unexpected console errors, failed resource
      loads, and test timeouts with an actionable page and scenario name.
- [ ] Produce JavaScript line and branch coverage for directly testable modules,
      document the initial baseline, and set a non-decreasing threshold. Do not
      inflate coverage with generated fixtures or the test harness itself.
- [ ] Add a Gradle task for the fast JavaScript suite and make it a dependency of
      `qualityGate`; run the real-browser suite in CI with retained diagnostics
      such as console output and a failure screenshot.
- [ ] Document how to run fast JavaScript tests, browser acceptance checks, and
      a single test or scenario locally.
- [ ] Existing Java tests, the new JavaScript suite, the Chromium acceptance
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
