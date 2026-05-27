---
id: LT-0024
title: Load planned workouts into the app
status: idea
track: product
priority: high
effort: medium
created: 2026-05-27
updated: 2026-05-27
owner: unassigned
depends_on: [LT-0023]
---

# LT-0024: Load planned workouts into the app

## Why

The app needs to consume generated workouts before it can guide a lifter through them.

## Outcome

Users can import or select a planned workout and see the planned work in the application.

## Scope

- In scope: loader, validation, persistence decision, route or CLI entry point, and tests.
- Out of scope: full workout execution UI.

## Acceptance criteria

- [ ] Valid workout files can be loaded.
- [ ] Invalid workout files show useful errors.
- [ ] Loaded workouts preserve order, targets, notes, and swap options.
- [ ] Tests cover loading, validation, and display.

## Notes

This task should be thin and focused. The follow-along session comes next.
