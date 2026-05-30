---
id: LT-0051
title: Detect personal records
status: idea
track: product
priority: high
effort: medium
created: 2026-05-30
updated: 2026-05-30
owner: unassigned
depends_on: [LT-0027]
---

# LT-0051: Detect personal records

## Why

Personal records are motivating and useful signals. The app should identify notable performances instead of making the user discover them manually.

## Outcome

Completed workouts can detect and display personal records for relevant lift metrics.

## Scope

- In scope: record detection rules, top weight, top reps at weight, estimated 1RM where available, display in workout summary, and tests.
- Out of scope: social sharing, badges, leaderboards, or coach notifications.

## Acceptance criteria

- [ ] The app can compare a completed set against prior history for the same lift.
- [ ] New personal records are identified after a workout is logged or updated.
- [ ] Record labels explain what record was achieved.
- [ ] Tests cover first-record, new-record, tie, and non-record cases.
- [ ] `qualityGate` passes.

## Notes

Record rules should be simple and predictable before adding more advanced achievement logic.
