---
id: LT-0088
title: Import local database to hosted account
status: done
track: data
priority: high
effort: medium
created: 2026-06-08
updated: 2026-06-26
owner: codex
depends_on: [LT-0083, LT-0085, LT-0087]
---

# LT-0088: Import local database to hosted account

## Why

Existing users should not have to abandon local training history when moving to
the hosted app.

## Outcome

A signed-in user can import a validated local LiftTrax database into their
hosted account without mixing data between users.

## Scope

- In scope: import format, validation, schema-version checks, ownership
  assignment, duplicate handling, import preview, rollback behavior, and tests.
- Out of scope: continuous sync between local and hosted databases.

## Acceptance criteria

- [x] Import validates that the source is a supported LiftTrax data set.
- [x] Imported records are assigned to the signed-in user or selected lifter
      profile.
- [x] Duplicate or repeated imports have predictable behavior.
- [x] Import failure leaves existing hosted data unchanged.
- [x] Tests cover supported imports, unsupported schema versions, duplicate
      imports, and cross-user isolation.

## Notes

Implemented `HostedLocalDatabaseImportService` and `ImportHostedDatabaseCli`.
The service validates source SQLite databases with `DatabaseBackupService`,
records a SHA-256 source fingerprint in `local_imports`, tracks imported source
`lift_records` in `local_import_records`, and imports inside a hosted database
transaction.

The CLI supports a credentials-free preview:

```powershell
.\gradlew.bat importHostedDatabase --args="data/lifts.db --preview"
```

and hosted import:

```powershell
.\gradlew.bat importHostedDatabase --args="data/lifts.db --user <auth-user-id>"
```

Verification:

- `.\gradlew.bat test --tests com.lifttrax.db.HostedPostgresTrainingDataStoreTest --tests com.lifttrax.cli.DatabaseBackupCliTest`
