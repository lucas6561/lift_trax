---
id: LT-0064
title: Add active workout draft and resume
status: idea
track: product
priority: high
effort: large
created: 2026-06-05
updated: 2026-06-05
owner: unassigned
depends_on: [LT-0025, LT-0027]
---

# LT-0064: Add active workout draft and resume

## Why

Real workouts get interrupted. A lifter should not lose entered set data if the browser refreshes, the phone sleeps, or they need to leave and continue later.

## Outcome

Follow-along workout sessions can save partial progress and resume later without duplicating completed executions.

## Scope

- In scope: draft session storage, resume route or action, partial set values, skipped work, substitution choices, completion cleanup, and tests.
- Out of scope: multi-device conflict resolution, coach review workflows, or long-term analytics beyond draft recovery.

## Acceptance criteria

- [ ] Entered follow-along values can be saved before the workout is fully completed.
- [ ] A user can resume an active draft from the app UI.
- [ ] Completing a draft writes final execution history once and clears or archives the draft state.
- [ ] Refreshing the session page does not silently discard entered values.
- [ ] Tests cover save, resume, complete, and stale or invalid draft handling.
- [ ] `qualityGate` passes.

## Notes

This is the usability bridge between the current follow-along session and the durable planned-versus-completed storage in `LT-0027`.
