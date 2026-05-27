---
id: LT-0026
title: Add exercise swap rules
status: idea
track: training-logic
priority: medium
effort: medium
created: 2026-05-27
updated: 2026-05-27
owner: unassigned
depends_on: [LT-0023, LT-0025]
---

# LT-0026: Add exercise swap rules

## Why

Real training often requires substituting an exercise while preserving the intent of the planned work.

## Outcome

The app supports exercise substitutions based on rules from the workout format or program schema.

## Scope

- In scope: swap rule model, allowed alternatives, UI flow, persistence of original and chosen exercise, and tests.
- Out of scope: automatic recommendation ranking unless a simple rule already provides it.

## Acceptance criteria

- [ ] Planned workouts can define allowed substitutions.
- [ ] The session UI allows a valid exercise swap.
- [ ] Invalid swaps are blocked or clearly marked.
- [ ] Completed data records both the planned exercise and the performed exercise.
- [ ] Tests cover valid and invalid swaps.

## Notes

Swaps should support coach intent, such as same movement pattern, muscle group, equipment, or lift type.
