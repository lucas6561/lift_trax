---
id: LT-0006
title: Define backup and restore flow
status: idea
track: data
priority: medium
effort: medium
created: 2026-05-27
updated: 2026-05-27
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

- [ ] A database backup can be created from a known app command or UI action.
- [ ] A backup can be restored with clear overwrite behavior.
- [ ] Restore verifies that the database looks like a LiftTrax database.
- [ ] Documentation explains where backups are stored and how to use them.

## Notes

Prefer boring, inspectable files over clever packaging.

