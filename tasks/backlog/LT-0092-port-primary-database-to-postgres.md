---
id: LT-0092
title: Port primary database to Postgres
status: idea
track: data
priority: high
effort: large
created: 2026-06-08
updated: 2026-06-14
owner: unassigned
depends_on: [LT-0082, LT-0083, LT-0087]
---

# LT-0092: Port primary database to Postgres

## Why

LiftTrax will outgrow a single local SQLite file when it supports hosted use,
multiple users, and deployment outside one trusted machine. Postgres gives the
project a durable server database target with stronger concurrency,
operational, and hosted-platform support.

## Outcome

LiftTrax can run the primary training-history and workout-management workflows
against Postgres as the durable application database, with the remaining SQLite
path treated as a local-development, import, or compatibility option rather than
the main persistence model.

## Scope

- In scope: Postgres connection configuration, schema migration assets, SQLite
  schema translation decisions, persistence boundary updates, core lift and
  execution reads/writes, planned-workout persistence touched by the current
  database layer, developer setup docs, and focused integration tests against a
  repeatable Postgres test environment.
- Out of scope: choosing the hosted vendor, implementing authentication,
  deploying the hosted app, continuous bidirectional sync with SQLite, and
  rewriting unrelated training-generation logic.

## Acceptance criteria

- [ ] The project has a documented Postgres schema and migration path for the
      current LiftTrax data model.
- [ ] Core database workflows run against Postgres, including lift listing,
      lift create/update, execution logging, execution history, lift detail
      reads, and any planned-workout records stored through the current
      database layer.
- [ ] SQLite-specific SQL, `PRAGMA` usage, path assumptions, and backup/restore
      behavior are either replaced, isolated behind the persistence boundary, or
      explicitly documented as SQLite-only compatibility code.
- [ ] Tests cover successful Postgres reads/writes, migration behavior, and at
      least one representative local-to-Postgres data transfer path.
- [ ] Developer documentation explains how to configure and verify a local
      Postgres-backed LiftTrax run without committing secrets.

## Notes

`LT-0087` introduces the hosted persistence adapter as a smaller first slice.
This task is the broader cutover once Postgres is confirmed by `LT-0082` and the
hosted data shape from `LT-0083` is clear.

`docs/adr/0003-hosted-auth-data-platform.md` confirms Supabase Postgres as the
first hosted Postgres target.
