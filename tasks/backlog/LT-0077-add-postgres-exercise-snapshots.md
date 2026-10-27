---
id: LT-0077
title: Add exercise snapshots to Postgres execution history
status: idea
track: data
priority: high
effort: medium
created: 2026-06-07
updated: 2026-10-27
owner: unassigned
depends_on: [LT-0076, LT-0092]
---

# LT-0077: Add exercise snapshots to Postgres execution history

## Why

Hosted executions still render through a live join to
`exercise_catalog_entries`. Renaming, reclassifying, or eventually deleting a
catalog entry must not rewrite the meaning of completed training.

## Outcome

Each Postgres execution stores the performed exercise identity and display
metadata required for history, statistics, account export, and recovery.
Existing rows are backfilled without changing what users see.

## Scope

- In scope: ordered Postgres migration, snapshot backfill, new-write behavior,
  history and stats queries, account export, tests, and documentation.
- Out of scope: catalog-file import, equipment rules, a second database, or
  changing planned-workout schemas.

## Acceptance criteria

- [ ] A migration adds the snapshot fields selected by `LT-0076`, including at
      least performed name, region, main-lift classification, and muscles.
- [ ] Existing executions are backfilled from their current account-owned
      catalog rows, with counts and null/error handling verified before the
      migration completes.
- [ ] New and edited executions persist snapshots in the same transaction as
      the execution and ordered sets.
- [ ] History, recent summaries, lift detail, statistics, and account export
      read performed-exercise data from the snapshot where appropriate.
- [ ] Renaming, disabling, or reclassifying a catalog entry does not relabel
      an older execution.
- [ ] Tests cover backfill, every new-write path, account isolation, old-history
      rendering, export, and malformed legacy rows.
- [ ] `qualityGate` passes.

## Notes

The live catalog reference may remain for navigation and current metadata, but
completed history must not depend on it to explain the performed exercise.
