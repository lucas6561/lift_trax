---
id: LT-0069
title: Fix phone execution tab weight visibility
status: ready
track: interface
priority: high
effort: small
created: 2026-06-05
updated: 2026-06-05
owner: unassigned
depends_on: [LT-0007]
---

# LT-0069: Fix phone execution tab weight visibility

## Why

The execution tab is cramped horizontally on a phone. During a workout, the weight values are one of the most important things to see, but the current narrow layout can hide or squeeze them out of view.

## Outcome

The execution tab remains readable on common phone widths, with weights visible without awkward horizontal scrolling or clipped columns.

## Scope

- In scope: execution tab mobile layout, set/history row density, weight column visibility, responsive CSS, and mobile verification.
- Out of scope: native mobile app packaging, broader navigation redesign, and changing execution data semantics.

## Acceptance criteria

- [ ] Weight values are visible at representative phone widths, including a 390px viewport.
- [ ] Execution rows or cards avoid horizontal clipping and do not require sideways scrolling for the primary lift, set, reps, and weight details.
- [ ] Dense secondary details wrap, collapse, or reorder without obscuring weights.
- [ ] A renderer, route, screenshot, or browser-backed check verifies the execution tab at a phone viewport.
- [ ] `qualityGate` passes.

## Notes

Start from the current execution tab layout and optimize for reading logged work during training. If a table cannot remain usable at phone widths, prefer a mobile-specific row/card treatment that keeps weight and reps prominent.
