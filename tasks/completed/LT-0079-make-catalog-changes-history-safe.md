---
id: LT-0079
title: Make catalog changes history-safe
status: done
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

- [x] Rename, region/main-lift changes, muscle changes, and disable operations
      affect current catalog views without changing existing execution snapshots.
- [x] Delete or merge behavior either detaches/tombstones the live reference
      safely or refuses with a specific actionable reason; it never cascades
      completed executions.
- [x] New executions resolve only an enabled, account-owned catalog entry and
      write the current metadata snapshot.
- [x] History, statistics, account export, and planned-workout history hints
      remain correct after every supported catalog mutation.
- [x] UI confirmation and error text explain whether history is retained and
      which current catalog entry is affected.
- [x] Tests cover rename, reclassification, disable, delete/merge, duplicate
      provenance, and cross-account mutation attempts.
- [x] `qualityGate` passes.

## Notes

This replaces the obsolete physical cutover task. All reads and writes already
use the Postgres catalog; this task completes safe lifecycle behavior.

## Completion - 2026-10-27

Catalog edits and disabling affect current definitions; completed snapshots
remain unchanged. Delete locks and detaches the live reference in a transaction,
retaining executions and sets. Duplicate-name rename/implicit merge is refused
with a specific explanation. Normal logging and Work Along require an enabled
owned definition; Work Along checks disabled selections before beginning writes.

Current history hints follow the live identity through a rename. Historical
statistics, dated filters, exports, and detail remain available after deletion.
Recent-history links use a scoped execution handle to select the original
performed identity, including when a deleted name is reused. UI confirmation and
success text explicitly explain retained history. Mutation, rename/reuse,
duplicate provenance, account-isolation, and rollback tests pass.

Validation: `./gradlew.bat qualityGate` passed with 337 tests, zero failures or
skips, and 92.34% instruction coverage. The run included the real Postgres
backfill and recovery tests against disposable loopback databases.
