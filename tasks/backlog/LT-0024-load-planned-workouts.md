---
id: LT-0024
title: Load planned workouts into the app
status: done
track: product
priority: high
effort: medium
created: 2026-05-27
updated: 2026-05-29
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

- [x] Valid workout files can be loaded.
- [x] Invalid workout files show useful errors.
- [x] Loaded workouts preserve order, targets, notes, and swap options.
- [x] Tests cover loading, validation, and display.

## Notes

This task should be thin and focused. The follow-along session comes next.

Completed as a preview-only import path. The Workout Waves tab can save planned
workout JSON from generated waves. The Import Workout tab opens a planned
workout file through the browser file picker and previews it through
`/planned-workout-preview`.

Display rendering is handled by `PlannedWorkoutHtml`, with grouped set targets,
week/day/block structure, notes, swap options, and matching local lift history
using the same `Last:` and `Best 1RM:` labels shown by wave generation. Full
follow-along execution and persistence remain LT-0025/LT-0027 scope.

Verification:

- `.\gradlew.bat test`
- `.\gradlew.bat qualityGate`
