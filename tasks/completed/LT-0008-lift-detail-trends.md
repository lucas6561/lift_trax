---
id: LT-0008
title: Add lift detail trends
status: done
track: interface
priority: medium
effort: medium
created: 2026-05-27
updated: 2026-05-30
owner: unassigned
depends_on: []
---

# LT-0008: Add lift detail trends

## Why

Lift detail pages should help users understand whether a lift is progressing, stalling, or simply being trained inconsistently.

## Outcome

Each lift detail page shows useful trend summaries based on logged execution history.

## Scope

- In scope: recent bests, volume or frequency summaries, and clear empty states.
- Out of scope: advanced charts, predictive analytics, and bodyweight normalization.

## Acceptance criteria

- [x] Lift detail pages show recent performance summaries.
- [x] Empty or sparse history has a helpful display state.
- [x] Calculations are covered by focused tests.
- [x] The UI remains readable on desktop and mobile widths.

## Notes

Start with summaries before adding charting dependencies.

Implemented 2026-05-30 with a Recent Trends section on lift detail pages, including last trained, recent best set, 90-day frequency, 90-day volume, and empty/sparse/stale-history states.

Verification: `./gradlew.bat qualityGate` passed on 2026-05-30. Local rendered-page smoke passed at `/lift?name=Back%20Squat` with a temporary seeded database.
