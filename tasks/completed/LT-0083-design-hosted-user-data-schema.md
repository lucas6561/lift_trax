---
id: LT-0083
title: Design hosted user data schema
status: done
track: data
priority: high
effort: medium
created: 2026-06-08
updated: 2026-06-14
owner: unassigned
depends_on: [LT-0016, LT-0082]
---

# LT-0083: Design hosted user data schema

## Why

The current database assumes one local owner. Hosted LiftTrax needs records to be
scoped to users, lifters, coaches, and shared programs without breaking existing
training history.

## Outcome

The project has a hosted data model and migration plan that maps the local
SQLite schema into user-owned hosted records.

## Scope

- In scope: users, lifter profiles, coach relationships, lifts, executions,
  planned workouts, programs, exercise catalog records, ownership columns,
  indexes, migration shape, and schema-version expectations.
- Out of scope: writing production migrations or changing runtime persistence.

## Acceptance criteria

- [x] The hosted schema identifies the owner or visibility rules for each major
      record type.
- [x] The design explains how existing local `lifts.db` data maps into one
      hosted account.
- [x] The design names records that can be shared between coach and lifter.
- [x] The design coordinates with the execution/catalog database boundary.
- [x] Follow-up migration and persistence implementation tasks are created or
      updated.

## Notes

This should follow `LT-0016` so the schema implements the ownership model rather
than inventing one implicitly. `docs/adr/0003-hosted-auth-data-platform.md`
selects Supabase Auth and Supabase Postgres, so this design should use Postgres
tables and constraints keyed to stable Supabase Auth user IDs.

Completed with `docs/hosted-user-data-schema.md`. The schema design maps local
`lifts.db` into a signed-in user's default lifter profile, defines Postgres
tables for users, lifter profiles, coach relationships, catalog entries,
executions, planned workouts, sessions, and local import batches, and keeps
execution snapshots independent from mutable catalog rows.
