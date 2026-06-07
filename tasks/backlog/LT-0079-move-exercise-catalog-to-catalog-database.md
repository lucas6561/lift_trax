---
id: LT-0079
title: Move exercise catalog to catalog database
status: idea
track: data
priority: high
effort: large
created: 2026-06-07
updated: 2026-06-07
owner: unassigned
depends_on: [LT-0077, LT-0078]
---

# LT-0079: Move exercise catalog to catalog database

## Why

Once execution history can preserve its own exercise snapshots and the catalog database exists, the app should stop treating the execution database as the source of truth for editable exercise data.

## Outcome

Lift creation, editing, enable/disable, listing, workout builder exercise selection, planned-workout substitution choices, and starter exercise reads use the exercise catalog database. Execution history remains in the execution database and continues to render correctly.

## Scope

- In scope: migration or copy strategy for existing `lifts`, app startup cutover, query/service changes, UI and CLI compatibility, tests, and docs.
- Out of scope: adding new equipment-aware planning behavior, changing completed-workout persistence beyond what is required for catalog references, or implementing multi-user ownership.

## Acceptance criteria

- [ ] Existing local `lifts` data is copied or migrated into the exercise catalog database without duplication.
- [ ] New and edited exercises are written to the catalog database, not the execution database.
- [ ] Execution logging can resolve a catalog exercise for a new record while preserving the execution snapshot required by `LT-0077`.
- [ ] Dashboard, execution list, lift detail, wave builder, planned-workout preview, and work-along session flows still find the exercise data they need.
- [ ] A catalog exercise can be disabled or renamed without breaking old execution history.
- [ ] Tests cover fresh databases, existing single-database upgrades, renamed exercises, disabled exercises, and normal logging after the split.
- [ ] Documentation describes the new database layout and the migration path for existing users.
- [ ] `qualityGate` passes.

## Notes

This task should be implemented after the decision and snapshot tasks so history preservation is not solved during the cutover under pressure.

Check whether `LT-0072` should seed into the catalog database if it has not already shipped by the time this task starts.
