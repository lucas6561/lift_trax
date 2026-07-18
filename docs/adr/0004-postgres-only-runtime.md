# 0004: Use Postgres as the sole runtime database

Date: 2026-07-18

## Status

Accepted

## Context

Running local workflows against SQLite while hosted workflows use Postgres
creates independent sources of truth. A command can silently read or modify
stale local data even when the web application uses the hosted account.

## Decision

Postgres is the sole runtime database for every database-aware LiftTrax
executable. Local development may use local authentication, but it connects to
the configured Postgres boundary just like hosted deployment.

SQLite remains supported only as a read-only legacy import source, a test
fixture, and the output format of an explicit whole-application operator
snapshot. The snapshot is one-way and is never opened by normal runtime code.

LiftTrax-owned Postgres schema changes use ordered migrations under
`shared/postgres/migrations/`. Provider-owned auth, storage, and internal tables
remain outside the application migration and snapshot boundary.

## Consequences

- Local and hosted commands observe the same canonical data when configured for
  the same environment.
- Database credentials are required for database-aware local commands.
- Old SQLite databases require the explicit import command before their data is
  available in the runtime application.
- Provider/operator Postgres recovery is distinct from the portable SQLite
  inspection snapshot.

This decision supersedes ADR-0001's SQLite runtime choice and the transitional
local-SQLite language in ADR-0003.
