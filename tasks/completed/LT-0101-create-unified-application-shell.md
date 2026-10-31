---
id: LT-0101
title: Create a unified application shell and task-based navigation
status: done
track: interface
priority: high
effort: large
created: 2026-10-30
updated: 2026-10-30
owner: unassigned
depends_on: [LT-0094, LT-0098]
---

# LT-0101: Create a unified application shell and task-based navigation

## Why

LiftTrax has a capable authenticated web UI, but its seven equal-weight root
tabs and separately wrapped detail pages make it feel like a collection of
tools instead of one application. The navigation should reflect the tasks a
lifter is trying to complete and remain usable on desktop and phone.

## Outcome

Authenticated pages share one responsive application shell with persistent
task-based navigation, clear current-section state, consistent page context,
and direct access to every existing capability.

## Scope

- In scope: shared authenticated shell; desktop and mobile navigation; Today,
  Train, Workouts, History, Exercises, and profile-based Account organization;
  secondary History and Workouts views; consistent page headings,
  breadcrumbs, messages, actions, and practical empty states; focused route,
  renderer, keyboard, accessibility, and real-browser verification.
- Out of scope: frontend frameworks, persistence or schema changes, LT-0020,
  the rest timer, analytics, and unrelated training features.

## Acceptance criteria

- [x] The dashboard, lift details, Account, exercise-catalog and shared-lift
  imports, saved workouts, workout previews, Work Along, resume, active workout,
  and saved-workout result pages use one reusable authenticated shell.
- [x] Desktop navigation persistently exposes Today, Train, Workouts, History,
  and Exercises; Account is available from the signed-in profile area.
- [x] A compact mobile navigation works at approximately 390x844, exposes the
  five primary task destinations, and does not obscure primary page actions.
- [x] The current section is conveyed visually and with accessible
  `aria-current` semantics; navigation and account controls are keyboard
  reachable with meaningful landmarks and labels.
- [x] Query and weekly/date history remain reachable as secondary History
  views rather than equal-weight primary destinations.
- [x] Saved workouts, workout import, and wave generation are grouped under
  Workouts; quick logging and Work Along entry/resume are reachable under
  Train; catalog management, lift detail, sharing, and imports are grouped
  under Exercises and Account as appropriate.
- [x] Existing direct URLs, refresh and browser history behavior,
  authentication, CSRF injection, account isolation, offline drafts, and
  service-worker registration continue to work.
- [x] Focused tests cover navigation, current-section state, deep links,
  account controls, shell coverage, and preservation of existing flows.
- [x] Desktop and 390x844 real-browser checks cover every primary destination,
  Work Along, lift details, both imports, Account, keyboard navigation, and
  basic accessibility semantics using isolated test data for write flows.
- [x] `./gradlew.bat qualityGate` passes.
- [x] The roadmap describes the unified task-based interface as the current
  product direction.

## Notes

The implementation keeps the server-rendered Java architecture and preserves
the existing `/?tab=...` URLs as stable deep links. Account is intentionally
placed in the profile area so the compact mobile navigation can focus on the
five daily training tasks.

Implemented and verified on 2026-10-30. Focused CLI/UI tests passed, followed
by the full 344-test quality gate with two intentional skips. A disposable
H2-backed fixture was exercised in Chromium at 1280x900 and 390x844 across all
primary destinations, Work Along, lift detail, both imports, Account,
back/forward/reload, keyboard focus order, accessibility landmarks, and service
worker registration. No physical-device-only acceptance gap remains.
