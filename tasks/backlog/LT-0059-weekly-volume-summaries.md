---
id: LT-0059
title: Add weekly volume summaries
status: idea
track: product
priority: medium
effort: medium
created: 2026-05-30
updated: 2026-05-30
owner: unassigned
depends_on: [LT-0027]
---

# LT-0059: Add weekly volume summaries

## Why

Users and coaches need a quick way to understand how much work was completed across a week, movement pattern, lift, or training block.

## Outcome

LiftTrax summarizes weekly training volume from completed workouts and shows it in a readable history or dashboard view.

## Scope

- In scope: weekly grouping, total sets, total reps, tonnage where meaningful, movement or lift grouping, display, and tests.
- Out of scope: complex periodization analytics, fatigue modeling, or coach reporting exports.

## Acceptance criteria

- [ ] Completed workouts can be grouped into calendar or training weeks.
- [ ] Weekly summaries include useful totals such as sets, reps, and tonnage where applicable.
- [ ] Summaries can be grouped by lift or movement category when data exists.
- [ ] Tests cover empty weeks, partial weeks, and multi-lift weeks.
- [ ] `qualityGate` passes.

## Notes

This creates a foundation for trend views and deload recommendations.
