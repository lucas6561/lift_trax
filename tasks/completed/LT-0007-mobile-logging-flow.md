---
id: LT-0007
title: Improve mobile workout logging flow
status: done
track: interface
priority: medium
effort: medium
created: 2026-05-27
updated: 2026-05-31
owner: unassigned
depends_on: [LT-0001]
---

# LT-0007: Improve mobile workout logging flow

## Why

The web UI is meant to be reachable from other devices on the same network, so logging from a phone during training should be fast and forgiving.

## Outcome

The add-execution flow works comfortably on a phone, with fewer taps and clear feedback after saving.

## Scope

- In scope: responsive layout, form grouping, save feedback, and rendering tests.
- Out of scope: native app packaging and offline service workers.

## Acceptance criteria

- [x] The logging form fits a narrow viewport without awkward horizontal movement.
- [x] Common fields are reachable quickly.
- [x] Save success and validation errors are clear.
- [x] A browser or rendered HTML check verifies the mobile layout.

## Notes

Keep the UI lightweight unless the app clearly outgrows server-rendered HTML.

Completed with phone-friendly add-execution layout rules, compact `1RM Single`,
`Bands Only`, and `Bar + Bands` quick setups that reuse the existing form
controls, a collapsed individual-set editor, a sticky mobile save action, and
live-region save and validation feedback.

Verification:

- `.\gradlew.bat clean test --tests com.lifttrax.cli.WebServerCliTest --no-daemon`
- `.\gradlew.bat qualityGate --no-daemon`
- Local web UI smoke against disposable `build/tmp/lt7-mobile-smoke.db`: saved
  and loaded back a band-only `red` 1RM and a `225 lb+blue` 1RM, confirmed the
  validation live region, and captured success and validation layouts through a
  fixed `390px` phone viewport.
