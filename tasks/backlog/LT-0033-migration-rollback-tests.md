---
id: LT-0033
title: Add migration rollback tests
status: idea
track: quality
priority: medium
effort: medium
created: 2026-05-30
updated: 2026-05-30
owner: unassigned
depends_on: [LT-0005]
---

# LT-0033: Add migration rollback tests

## Why

Database migrations become risky as workout plans, completed sessions, users, and history data grow. Rollback coverage gives the project a safer path when a migration needs to be corrected.

## Outcome

Migration tests verify that schema upgrades and supported rollback paths preserve or intentionally transform data.

## Scope

- In scope: migration test harness, representative seed data, upgrade checks, rollback checks where supported, and migration failure diagnostics.
- Out of scope: a complete backup product flow, arbitrary downgrade support across all historic versions, or live production migration tooling.

## Acceptance criteria

- [ ] A test database can be migrated from a known earlier schema state.
- [ ] Representative lift, execution, plan, and workout data survives migrations as expected.
- [ ] Supported rollback behavior is tested or explicitly documented as unsupported.
- [ ] Migration failures identify the migration step and SQL involved.
- [ ] `qualityGate` passes.

## Notes

If the app chooses forward-only migrations, this task should still document that decision and test failure recovery boundaries.
