---
id: LT-0005
title: Add schema migration tracking
status: idea
track: data
priority: medium
effort: medium
created: 2026-05-27
updated: 2026-05-27
owner: unassigned
depends_on: []
---

# LT-0005: Add schema migration tracking

## Why

The database schema will need to evolve as plans, edits, backups, and analytics are added. The project should know which schema version a database is on and how to upgrade it safely.

## Outcome

LiftTrax can apply ordered schema migrations and report the active schema version.

## Scope

- In scope: migration file convention, migration runner, schema version tests, and developer documentation.
- Out of scope: cloud sync and multi-user conflict handling.

## Acceptance criteria

- [ ] Migrations run in a deterministic order.
- [ ] Already-applied migrations are not re-applied.
- [ ] Tests cover a new empty database and an already-current database.
- [ ] `README.md` or project docs explain how to add a migration.

## Notes

There is already a `shared/sql/schema_version.txt` file and `SqlSchemaVersion` test coverage that can inform the direction.

