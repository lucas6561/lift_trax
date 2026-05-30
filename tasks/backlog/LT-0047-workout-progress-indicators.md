---
id: LT-0047
title: Add workout progress indicators
status: idea
track: interface
priority: high
effort: medium
created: 2026-05-30
updated: 2026-05-30
owner: unassigned
depends_on: [LT-0025]
---

# LT-0047: Add workout progress indicators

## Why

During a longer workout, users should know how much work is done, what is next, and whether they are still on plan.

## Outcome

The follow-along workout screen shows clear progress through days, blocks, exercises, and sets.

## Scope

- In scope: completion state, current exercise or block indicator, remaining planned work, and tests for progress calculations.
- Out of scope: gamification, badges, or long-term progress analytics.

## Acceptance criteria

- [ ] The active workout view marks completed, current, and upcoming work.
- [ ] Progress updates after sets are logged, edited, or removed.
- [ ] Planned versus completed differences are visible without overwhelming the logging flow.
- [ ] Tests cover progress calculations for normal and partially completed workouts.
- [ ] `qualityGate` passes.

## Notes

This should make the session feel guided without turning it into a cluttered dashboard.
