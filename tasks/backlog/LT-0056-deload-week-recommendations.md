---
id: LT-0056
title: Add deload week recommendations
status: idea
track: training-logic
priority: medium
effort: medium
created: 2026-05-30
updated: 2026-05-30
owner: unassigned
depends_on: [LT-0027, LT-0059]
---

# LT-0056: Add deload week recommendations

## Why

Users benefit from noticing when recent workload, missed reps, or program structure suggests backing off. A simple recommendation can make long-term training more sustainable.

## Outcome

The app can identify candidate deload weeks or deload suggestions based on recent training data and program context.

## Scope

- In scope: documented recommendation rules, recent workload inputs, missed-work indicators, display in planning or summary views, and tests.
- Out of scope: automatic program rewrites, medical recommendations, or fully personalized recovery modeling.

## Acceptance criteria

- [ ] Deload recommendation rules are documented in plain language.
- [ ] Recent volume or completion data feeds the recommendation.
- [ ] Recommendations are shown as suggestions, not automatic changes.
- [ ] Tests cover recommendation and non-recommendation scenarios.
- [ ] `qualityGate` passes.

## Notes

This should wait until weekly summaries and planned-versus-completed data are reliable.
