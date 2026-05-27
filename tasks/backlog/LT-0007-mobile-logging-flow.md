---
id: LT-0007
title: Improve mobile workout logging flow
status: idea
track: interface
priority: medium
effort: medium
created: 2026-05-27
updated: 2026-05-27
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

- [ ] The logging form fits a narrow viewport without awkward horizontal movement.
- [ ] Common fields are reachable quickly.
- [ ] Save success and validation errors are clear.
- [ ] A browser or rendered HTML check verifies the mobile layout.

## Notes

Keep the UI lightweight unless the app clearly outgrows server-rendered HTML.

