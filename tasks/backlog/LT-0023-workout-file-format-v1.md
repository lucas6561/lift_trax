---
id: LT-0023
title: Define the workout file format v1
status: ready
track: training-logic
priority: high
effort: large
created: 2026-05-27
updated: 2026-05-27
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

- [ ] The format includes version and source program metadata.
- [ ] The format can represent weeks, days, ordered work blocks, lifts, sets, reps, intensity, rest, notes, and substitutions.
- [ ] The format distinguishes planned targets from completed results.
- [ ] Example workout files exist.
- [ ] The format can be loaded by future app code without requiring the original generator.

## Notes

This may be a separate file format or a persisted database model with import/export. Decide explicitly.
