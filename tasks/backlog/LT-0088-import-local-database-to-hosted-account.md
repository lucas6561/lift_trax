---
id: LT-0088
title: Import local database to hosted account
status: blocked
track: data
priority: high
effort: medium
created: 2026-06-08
updated: 2026-06-15
owner: unassigned
depends_on: [LT-0083, LT-0085, LT-0087]
---

# LT-0088: Import local database to hosted account

## Why

Existing users should not have to abandon local training history when moving to
the hosted app.

## Outcome

A signed-in user can import a validated local LiftTrax database or export bundle
into their hosted account without mixing data between users.

## Scope

- In scope: import format, validation, schema-version checks, ownership
  assignment, duplicate handling, import preview, rollback behavior, and tests.
- Out of scope: continuous sync between local and hosted databases.

## Acceptance criteria

- [ ] Import validates that the source is a supported LiftTrax data set.
- [ ] Imported records are assigned to the signed-in user or selected lifter
      profile.
- [ ] Duplicate or repeated imports have predictable behavior.
- [ ] Import failure leaves existing hosted data unchanged.
- [ ] Tests cover supported imports, unsupported schema versions, duplicate
      imports, and cross-user isolation.

## Notes

This can build from the existing backup and restore validation concepts, but the
hosted import path needs user ownership and rollback semantics.

Use `docs/hosted-user-data-schema.md` for the local `lifts.db` to hosted account
mapping, including the `local_imports` batch record and all-or-nothing import
expectation.

2026-06-15 hosted batch note:

- blocked until `LT-0087` can write core catalog, execution, and execution-set
  records through the hosted adapter;
- duplicate detection should use `local_imports.source_fingerprint` plus source
  local record IDs, as described in `docs/hosted-user-data-schema.md`;
- import preview must stay user-scoped and avoid exposing another account's
  existing hosted records.
