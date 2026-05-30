---
id: LT-0055
title: Add readiness-based adjustments
status: idea
track: training-logic
priority: medium
effort: large
created: 2026-05-30
updated: 2026-05-30
owner: unassigned
depends_on: [LT-0020, LT-0027]
---

# LT-0055: Add readiness-based adjustments

## Why

Some training days need adjustment based on fatigue, soreness, or RPE. The app should eventually support controlled plan changes without losing the original intent.

## Outcome

Users can enter a simple readiness or RPE signal, and the app can suggest bounded adjustments to planned work.

## Scope

- In scope: readiness input model, adjustment rules, suggested load or volume changes, plan-versus-completed tracking, and tests.
- Out of scope: medical advice, automated coaching beyond documented rules, or machine-learning recommendations.

## Acceptance criteria

- [ ] The app captures a simple readiness or RPE signal during a workout.
- [ ] Adjustment rules are documented and bounded.
- [ ] Suggested changes preserve the original planned work for comparison.
- [ ] Users can accept or ignore suggested adjustments.
- [ ] `qualityGate` passes.

## Notes

This should be conservative. Suggestions should be explainable and reversible.
