---
id: LT-0054
title: Add bodyweight tracking
status: idea
track: product
priority: medium
effort: medium
created: 2026-05-30
updated: 2026-05-30
owner: unassigned
depends_on: [LT-0005]
---

# LT-0054: Add bodyweight tracking

## Why

Bodyweight changes can affect performance, especially for bodyweight movements and strength-to-weight context.

## Outcome

Users can log bodyweight by date and see it alongside relevant workout and lift history.

## Scope

- In scope: bodyweight data model, date-based logging, edit/delete behavior, display in history summaries, and tests.
- Out of scope: nutrition tracking, body composition tracking, or automated scale integration.

## Acceptance criteria

- [ ] Users can add, edit, and delete bodyweight entries by date.
- [ ] Workout history can show the nearest relevant bodyweight entry where useful.
- [ ] Bodyweight units are clear and consistent with the rest of the app.
- [ ] Migration and route tests cover bodyweight persistence.
- [ ] `qualityGate` passes.

## Notes

This should stay optional and quiet for users who do not track bodyweight.
