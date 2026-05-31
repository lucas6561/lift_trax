---
id: LT-0005
title: Add schema migration tracking
status: done
track: data
priority: medium
effort: medium
created: 2026-05-27
updated: 2026-05-31
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

- [x] Migrations run in a deterministic order.
- [x] Already-applied migrations are not re-applied.
- [x] Tests cover a new empty database and an already-current database.
- [x] `README.md` or project docs explain how to add a migration.

## Notes

There is already a `shared/sql/schema_version.txt` file and `SqlSchemaVersion` test coverage that can inform the direction.

Completed with numbered SQL assets under `shared/sql/migrations/`, an explicit
`index.txt` manifest, and `SqlSchemaMigrator`. The runner applies files in
numeric order, records them in `schema_migrations`, maintains SQLite's
`PRAGMA user_version`, and lets `SqliteDb` report its active schema version.

The existing compatibility repairs for pre-tracking databases remain in place
after migrations run so older local databases still gain the `lifts.enabled`
column and normalized `execution_sets` storage.

Verified on 2026-05-31:

- `.\gradlew.bat test --tests com.lifttrax.db.SqlSchemaVersionTest --tests com.lifttrax.db.SqlSchemaMigratorTest --tests com.lifttrax.db.SqliteDbEnabledTest`
- `.\gradlew.bat qualityGate`
