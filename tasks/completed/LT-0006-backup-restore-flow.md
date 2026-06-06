---
id: LT-0006
title: Define backup and restore flow
status: done
track: data
priority: medium
effort: medium
created: 2026-05-27
updated: 2026-06-06
owner: unassigned
depends_on: [LT-0005]
---

# LT-0006: Define backup and restore flow

## Why

Training history is personal data. Users should be able to preserve it before changing machines, editing schema, or experimenting with new features.

## Outcome

The app has a simple backup and restore process for the local SQLite database.

## Scope

- In scope: backup format, restore behavior, validation, and user-facing documentation.
- Out of scope: cloud sync, account management, and automatic remote backups.

## Acceptance criteria

- [x] A database backup can be created from a known app command or UI action.
- [x] A backup can be restored with clear overwrite behavior.
- [x] Restore verifies that the database looks like a LiftTrax database.
- [x] Documentation explains where backups are stored and how to use them.

## Notes

Implemented plain SQLite `.db` backups with `backupDatabase` and `restoreDatabase` Gradle commands. Manual backups default to a `backups` directory next to the target database, and restore requires `--confirm-overwrite` before replacing an existing database. Restore validates required LiftTrax tables and rejects backups with a schema version newer than the app supports. When overwrite is confirmed, the existing target database is saved as a `pre-restore` backup before replacement.

Verification: `.\gradlew.bat qualityGate` passed on 2026-06-06.
