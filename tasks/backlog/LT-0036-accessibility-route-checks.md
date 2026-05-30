---
id: LT-0036
title: Add accessibility route checks
status: idea
track: quality
priority: medium
effort: medium
created: 2026-05-30
updated: 2026-05-30
owner: unassigned
depends_on: [LT-0009]
---

# LT-0036: Add accessibility route checks

## Why

LiftTrax should be usable during training on different devices and input methods. Basic accessibility checks can catch missing labels, poor structure, and navigation regressions early.

## Outcome

Important web routes have automated accessibility checks and a small documented standard for forms, buttons, headings, and focus behavior.

## Scope

- In scope: automated checks for rendered routes, semantic labels, heading structure, focusable controls, and accessible validation errors.
- Out of scope: a complete manual accessibility audit or redesigning all visual styling in one pass.

## Acceptance criteria

- [ ] A practical accessibility checking approach is added to route or browser tests.
- [ ] Dashboard, workout preview, and key form routes are covered.
- [ ] Failures name the route and accessibility issue clearly.
- [ ] The project documents accessibility expectations for new UI work.
- [ ] `qualityGate` passes.

## Notes

Keep the first pass small enough to maintain. The goal is to catch obvious regressions and establish a habit.
