---
id: LT-0077
title: Add exercise snapshots to execution history
status: idea
track: data
priority: high
effort: medium
created: 2026-06-07
updated: 2026-06-07
owner: unassigned
depends_on: [LT-0076, LT-0005]
---

# LT-0077: Add exercise snapshots to execution history

## Why

Before exercise catalog data can move into a separate database, completed executions need enough exercise information to stand on their own. Old history should still display the performed exercise and useful grouping metadata even if the catalog entry changes later.

## Outcome

Execution rows store a stable performed-exercise reference and snapshot fields needed for history rendering, filtering, and stats. Existing records are backfilled from the current `lifts` table without losing data.

## Scope

- In scope: execution-history schema changes, backfill migration, query updates, rendering/stats compatibility, tests, and developer notes.
- Out of scope: creating the catalog database, equipment-aware planning, starter exercise seeding, or changing planned-workout file schemas.

## Acceptance criteria

- [ ] A migration adds the execution-side exercise reference and snapshot fields chosen in `LT-0076`.
- [ ] Existing `lift_records` are backfilled from the current `lifts` table so history can be read without joining catalog data.
- [ ] Execution-history queries, recent-history summaries, lift detail pages, and stats still show the expected exercise names and metadata.
- [ ] Editing a catalog lift name or disabling a lift does not erase or mislabel previously recorded executions.
- [ ] Tests cover backfilled records, newly logged executions, renamed catalog exercises, and deleted or disabled catalog exercises where supported.
- [ ] `qualityGate` passes.

## Notes

This is the safety step before the physical database split. It should reduce reliance on `lift_records.lift_id -> lifts.id` for user-facing history.
