---
id: LT-0037
title: Add route performance smoke tests
status: done
track: quality
priority: medium
effort: medium
created: 2026-05-30
updated: 2026-09-22
owner: codex
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

- [x] Dashboard and planned-workout preview routes are covered by performance smoke tests.
- [x] Tests run against enough data to make inefficient queries visible.
- [x] Thresholds are documented and reasonable for local development.
- [x] Failures identify the slow route and measured duration.
- [x] `qualityGate` passes.

## Notes

This should stay lightweight so it does not make the normal quality gate painfully slow.

Implemented `RoutePerformanceSmokeTest` for authenticated `GET /` and
`POST /planned-workout-preview` requests through the real HTTP server and hosted
datastore, using an isolated H2 database in PostgreSQL compatibility mode.
`RoutePerformanceFixture` seeds 100 lifts, 12,000 executions, and 48,000 sets.
The preview covers four weeks and 72 exercise appearances with existing history.

Each route receives two warmups and three measured requests. Median budgets
are 1,500 ms for the dashboard and 2,500 ms for preview. Response-content checks
reject fast errors or missing history. Latency failures include route, elapsed
samples, budget, and workload size. Setup is shared and excluded from timing.
See `docs/route-performance-smoke-tests.md` for workload details, scope limits,
report locations, and failure triage.

Focused tests and `./gradlew.bat qualityGate` passed on 2026-09-22. The focused
run measured medians of 50 ms for the dashboard and 138 ms for preview; these
are local smoke measurements, not production latency promises.
