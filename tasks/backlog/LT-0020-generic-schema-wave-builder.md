---
id: LT-0020
title: Create a generic schema-to-wave builder
status: idea
track: training-logic
priority: high
effort: large
created: 2026-05-27
updated: 2026-05-27
owner: unassigned
depends_on: [LT-0019, LT-0023]
---

# LT-0020: Create a generic schema-to-wave builder

## Why

The wave builder should generate workouts from the program schema rather than requiring a custom implementation for each programming style.

## Outcome

A generic builder converts a validated program schema into the versioned workout output format.

## Scope

- In scope: builder API, deterministic generation, randomization controls, progression application, and tests against example programs.
- Out of scope: drag-and-drop editor.

## Acceptance criteria

- [ ] The builder accepts only validated schema objects.
- [ ] The builder generates workouts in the workout file format v1.
- [ ] Generation can be deterministic when given a seed.
- [ ] Tests verify output for representative conjugate and hypertrophy examples.
- [ ] Existing markdown output can still be produced from generated workouts or is intentionally replaced.

## Notes

Keep domain logic independent from CLI and web rendering.
