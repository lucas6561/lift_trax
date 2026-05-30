---
id: LT-0023
title: Define the workout file format v1
status: done
track: training-logic
priority: high
effort: large
created: 2026-05-27
updated: 2026-05-29
owner: unassigned
depends_on: [LT-0003]
---

# LT-0023: Define the workout file format v1

## Why

Generated workouts need a stable output format that the app can load and follow.

## Outcome

A versioned workout format describes planned training sessions, planned lifts, sets, targets, notes, swap options, and links back to the source program.

## Scope

- In scope: format design, examples, documentation, and compatibility notes.
- Out of scope: session UI implementation.

## Acceptance criteria

- [x] The format includes version and source program metadata.
- [x] The format can represent weeks, days, ordered work blocks, lifts, sets, reps, intensity, rest, notes, and substitutions.
- [x] The format distinguishes planned targets from completed results.
- [x] Example workout files exist.
- [x] The format can be loaded by future app code without requiring the original generator.

## Notes

This may be a separate file format or a persisted database model with import/export. Decide explicitly.

Completed as a separate JSON file format under
`shared/workouts/schema/workout.schema.v1.json`, with an example in
`shared/workouts/examples/conjugate-wave-v1.json` and authoring/display notes in
`docs/workout-file-format-v1.md`.

Java support lives in `PlannedWorkoutFile`, `PlannedWorkoutJson`, and
`PlannedWorkoutExporter`. Planned targets are separate from the optional
`completedWorkouts` array; database persistence for planned versus completed
work remains future LT-0027 scope.

Verification:

- `.\gradlew.bat test`
- `.\gradlew.bat qualityGate`
