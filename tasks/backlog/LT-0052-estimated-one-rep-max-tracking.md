---
id: LT-0052
title: Add estimated 1RM tracking
status: idea
track: product
priority: high
effort: medium
created: 2026-05-30
updated: 2026-05-30
owner: unassigned
depends_on: [LT-0008, LT-0027]
---

# LT-0052: Add estimated 1RM tracking

## Why

Estimated 1RM gives users a simple way to see strength trends across rep ranges without maxing out constantly.

## Outcome

Lift detail and workout history views show estimated 1RM values and trends based on logged sets.

## Scope

- In scope: formula selection, calculation service, display in lift detail views, history summaries, and tests.
- Out of scope: choosing training maxes automatically or prescribing future loads from estimated 1RM.

## Acceptance criteria

- [ ] The selected estimated 1RM formula is documented.
- [ ] Calculations handle bodyweight, missing reps, missing weight, and invalid values safely.
- [ ] Lift detail views show recent and best estimated 1RM values.
- [ ] Tests cover representative rep and weight combinations.
- [ ] `qualityGate` passes.

## Notes

Keep the first version transparent. Users should know this is an estimate, not a measured max.
