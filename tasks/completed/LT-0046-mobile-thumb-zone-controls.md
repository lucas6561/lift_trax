---
id: LT-0046
title: Add mobile thumb-zone controls
status: done
track: interface
priority: high
effort: medium
created: 2026-05-30
updated: 2026-06-09
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

- [x] Primary logging actions are reachable near the lower portion of common phone viewports.
- [x] Destructive actions are visually separated from high-frequency logging actions.
- [x] Controls keep stable dimensions when labels or state change.
- [x] Mobile route tests or screenshots verify the layout at representative widths.
- [x] `qualityGate` passes.

## Notes

This should refine the mobile logging flow rather than replacing it with a separate app.

Completed with shared mobile CSS that keeps Add Execution save controls sticky
near the lower viewport, moves follow-along block navigation into the mobile
thumb zone, keeps the completed-workout save action in the lower viewport, and
wraps execution row actions so delete is separated from edit/logging controls
with stable tap targets.

Verification:

- `.\gradlew.bat test --tests com.lifttrax.cli.WebHtmlTest --tests com.lifttrax.cli.WebUiRendererTest --tests com.lifttrax.cli.PlannedWorkoutSessionHtmlTest`
- `.\gradlew.bat qualityGate`
