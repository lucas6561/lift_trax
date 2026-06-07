---
id: LT-0080
title: Update backup and restore for split databases
status: idea
track: data
priority: high
effort: medium
created: 2026-06-07
updated: 2026-06-07
owner: unassigned
depends_on: [LT-0006, LT-0079]
---

# LT-0080: Update backup and restore for split databases

## Why

After execution history and exercise catalog data live in separate database files, backup and restore need to treat them as one coherent local data set. Restoring only one side could leave logged history, catalog exercises, and future equipment metadata out of sync.

## Outcome

Backup and restore flows validate, copy, and restore the execution database and exercise catalog database together, with clear user-facing and developer documentation.

## Scope

- In scope: backup format or directory structure, validation of both databases, restore ordering, pre-restore backups, failure behavior, CLI updates, docs, and tests.
- Out of scope: cloud sync, per-table restore, live multi-user conflict resolution, or public release packaging.

## Acceptance criteria

- [ ] Manual backup captures both the execution database and exercise catalog database in a predictable location.
- [ ] Restore validates both database files before replacing local data.
- [ ] Restore either replaces both databases successfully or leaves the current local data intact with a clear error.
- [ ] Pre-restore safety backups include both databases.
- [ ] CLI help and README guidance explain the split-database backup and restore behavior.
- [ ] Tests cover successful backup/restore, missing catalog database, missing execution database, mismatched or unsupported schema versions, and restore failure rollback behavior.
- [ ] `qualityGate` passes.

## Notes

Build on `LT-0006` instead of creating a parallel backup system. This should also inform `LT-0017` release/update expectations.
