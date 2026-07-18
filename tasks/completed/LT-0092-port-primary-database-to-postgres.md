---
id: LT-0092
title: Make Postgres the sole runtime database
status: done
track: data
priority: high
effort: large
created: 2026-06-08
updated: 2026-07-18
owner: codex
depends_on: [LT-0082, LT-0083, LT-0087]
---

# LT-0092: Make Postgres the sole runtime database

## Why

Running LiftTrax against both Postgres and SQLite creates two independent
sources of truth. A workout written to one database does not appear in the
other, and command-line entry points that still open SQLite can silently show
or modify stale data. Postgres must be the canonical runtime store everywhere,
whether the application is hosted or launched locally.

SQLite remains valuable as a portable backup artifact. It should be produced
only by an explicit operator command that takes a consistent snapshot of the
Postgres application data; it must not remain an alternate live database.

## Outcome

Every database-aware LiftTrax executable uses the configured Postgres database
as its runtime source of truth. Local and hosted launches therefore read and
write the same canonical data when configured for the same environment.

An operator-only executable can create a complete SQLite backup of all
LiftTrax-owned Postgres tables and rows on demand. Creating that file is a
one-way snapshot operation, not synchronization and not a second runtime mode.

## Scope

- In scope: inventorying every `main` class and Gradle executable; routing every
  database-aware runtime path through Postgres; removing SQLite runtime and
  local-source-of-truth assumptions; Postgres migrations and persistence
  coverage for all supported application workflows; an explicit
  Postgres-to-SQLite backup executable; safe backup file creation; backup
  completeness verification; and local/operator documentation.
- Out of scope: continuous or bidirectional synchronization; using SQLite for
  normal local operation; automatically creating a SQLite file during normal
  application startup; copying Supabase-managed auth, storage, or other system
  schemas; provider-level disaster recovery; user-facing account export; and
  unrelated training-generation logic.

## Acceptance criteria

- [x] Every Java `main` class and Gradle application task is inventoried and
      classified as Postgres-backed or database-neutral; no normal runtime
      executable opens `SqliteDb` or accepts a SQLite database as its live data
      source.
- [x] The normal local web command and hosted deployment both use Postgres, and
      their command-line interfaces no longer require a misleading SQLite path.
- [x] All supported database workflows run against Postgres, including lift
      catalog operations, execution logging/history, workout and program
      persistence, planned-workout sessions, imports that remain supported,
      database inspection/dump commands, and administrative operations.
- [x] The project has a documented Postgres schema and ordered migration path
      for every LiftTrax-owned application table.
- [x] A separately named operator executable connects to Postgres and writes a
      new SQLite backup file containing the complete LiftTrax-owned schema and
      data, including all users and ownership identifiers.
- [x] The backup runs from a transactionally consistent Postgres snapshot,
      writes through a temporary file, and only publishes the final SQLite file
      after all tables and validation checks succeed.
- [x] Backup creation refuses to overwrite an existing destination unless the
      operator explicitly confirms it, removes an incomplete temporary artifact
      after failure, and exits nonzero with an actionable error.
- [x] The SQLite backup records format/schema version and creation metadata, and
      verification compares expected tables plus per-table row counts between
      the Postgres snapshot and the completed SQLite file.
- [x] Runtime code cannot write new application data to SQLite; SQLite-specific
      code is isolated to backup serialization, backup validation, and tests.
- [x] Repeatable Postgres integration tests cover migrations and representative
      reads/writes from each database-aware executable, while backup integration
      tests cover completeness, user ownership, empty tables, overwrite safety,
      consistent failure cleanup, and round-trip readability of the SQLite
      artifact.
- [x] Developer and operator documentation explains Postgres-only local usage,
      secret handling, backup creation, backup contents, and the fact that the
      SQLite file is a point-in-time artifact rather than a synchronized store.

## Notes

`LT-0087` introduced the hosted persistence adapter as a smaller first slice.
This task is the final runtime cutover: the existing `runHostedWeb` behavior
becomes the normal local and hosted behavior, while the SQLite-backed `runWeb`
path is retired from normal use.

`docs/adr/0003-hosted-auth-data-platform.md` confirms Supabase Postgres as the
first hosted Postgres target.

Use `docs/hosted-user-data-schema.md` as the primary application schema shape.
"Complete Postgres backup" means all LiftTrax-owned application tables and all
users' rows. Supabase-managed `auth`, storage, and platform-internal schemas stay
under provider backup controls rather than being translated into SQLite.

The backup executable in this story is an operator snapshot of the whole
LiftTrax application database. `LT-0091` remains separate: it covers a
user-facing, account-scoped export and hosted-provider recovery controls.

2026-06-26 readiness note:

- `LT-0087` proves the first hosted adapter boundary for app users, lifter
  profiles, catalog entries, executions, and execution sets.
- `LT-0088` proves local SQLite import into hosted account-owned records.
- The implementation should expand the hosted provider from core web logging
  into every remaining database-aware main, then remove the SQLite runtime path
  only after equivalent Postgres coverage exists.
- Existing SQLite data should receive a separately planned final-import or
archival decision before its runtime path is removed; there is no ongoing
synchronization requirement.

2026-07-18 completion note:

- All production entry points now use the configured Postgres provider; the
  retired mutable SQLite implementation lives only in test fixtures.
- `shared/postgres/migrations/` is the ordered LiftTrax application migration
  path. Existing hosted tables are adopted safely through idempotent migration
  SQL and recorded in `lifttrax_schema_migrations`.
- `postgresSqliteBackup` creates and validates a complete, all-user snapshot
  through a temporary file and repeatable-read transaction. Published filenames
  include the snapshot's UTC creation date and time. It is intentionally not a
  runtime or restore mode.
- Verification: focused Postgres/backup tests, the full Gradle test suite, and
  `tasks --group application` passed. Final repository verification used
  `spotlessApply` followed by `qualityGate`.
