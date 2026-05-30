---
id: LT-0037
title: Add route performance smoke tests
status: idea
track: quality
priority: medium
effort: medium
created: 2026-05-30
updated: 2026-05-30
owner: unassigned
depends_on: [LT-0001, LT-0024]
---

# LT-0037: Add route performance smoke tests

## Why

Dashboard and workout preview pages should stay fast as history, plans, and generated workouts grow. Simple performance checks can catch expensive regressions before the app feels sluggish.

## Outcome

Local smoke tests measure representative route response times against seeded data and flag obvious performance regressions.

## Scope

- In scope: representative data setup, response-time checks for key routes, lightweight thresholds, and documentation.
- Out of scope: full load testing, production monitoring, or precise browser performance profiling.

## Acceptance criteria

- [ ] Dashboard and planned-workout preview routes are covered by performance smoke tests.
- [ ] Tests run against enough data to make inefficient queries visible.
- [ ] Thresholds are documented and reasonable for local development.
- [ ] Failures identify the slow route and measured duration.
- [ ] `qualityGate` passes.

## Notes

This should stay lightweight so it does not make the normal quality gate painfully slow.
