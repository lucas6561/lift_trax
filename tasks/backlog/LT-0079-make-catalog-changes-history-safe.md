---
id: LT-0079
title: Make catalog changes history-safe
status: idea
track: data
priority: high
effort: medium
created: 2026-06-07
updated: 2026-10-27
owner: unassigned
depends_on: [LT-0077, LT-0078]
---

# LT-0079: Make catalog changes history-safe

## Why

Once executions have immutable exercise snapshots and catalog entries have
clear identities, catalog maintenance should no longer be constrained by old
history. Users need predictable rename, disable, merge, and delete behavior
without losing or relabeling completed training.

## Outcome

Catalog mutation flows operate on current account-owned definitions while old
executions remain readable and auditable from their snapshots.

## Scope

- In scope: rename/reclassification/disable/delete rules, duplicate and merge
  handling, query changes that separate current catalog views from historical
  views, UI messages, account export, and tests.
- Out of scope: a physical database move, public marketplace behavior,
  cross-account editing, or rewriting historical snapshots to match a new name.

## Acceptance criteria

- [ ] Rename, region/main-lift changes, muscle changes, and disable operations
      affect current catalog views without changing existing execution snapshots.
- [ ] Delete or merge behavior either detaches/tombstones the live reference
      safely or refuses with a specific actionable reason; it never cascades
      completed executions.
- [ ] New executions resolve only an enabled, account-owned catalog entry and
      write the current metadata snapshot.
- [ ] History, statistics, account export, and planned-workout history hints
      remain correct after every supported catalog mutation.
- [ ] UI confirmation and error text explain whether history is retained and
      which current catalog entry is affected.
- [ ] Tests cover rename, reclassification, disable, delete/merge, duplicate
      provenance, and cross-account mutation attempts.
- [ ] `qualityGate` passes.

## Notes

This replaces the obsolete physical cutover task. All reads and writes already
use the Postgres catalog; this task completes safe lifecycle behavior.
