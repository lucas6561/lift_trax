---
id: LT-0046
title: Add mobile thumb-zone controls
status: idea
track: interface
priority: high
effort: medium
created: 2026-05-30
updated: 2026-05-30
owner: unassigned
depends_on: [LT-0007, LT-0025]
---

# LT-0046: Add mobile thumb-zone controls

## Why

Workout logging often happens one-handed between sets. Primary actions should be reachable and stable on a phone.

## Outcome

The mobile workout logging and follow-along screens place high-frequency controls where they are easy to reach and hard to tap accidentally.

## Scope

- In scope: mobile action placement, stable button sizing, spacing, sticky controls where appropriate, and responsive route tests.
- Out of scope: native mobile app packaging or platform-specific gesture design.

## Acceptance criteria

- [ ] Primary logging actions are reachable near the lower portion of common phone viewports.
- [ ] Destructive actions are visually separated from high-frequency logging actions.
- [ ] Controls keep stable dimensions when labels or state change.
- [ ] Mobile route tests or screenshots verify the layout at representative widths.
- [ ] `qualityGate` passes.

## Notes

This should refine the mobile logging flow rather than replacing it with a separate app.
