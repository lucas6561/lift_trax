---
id: LT-0086
title: Enforce user-scoped authorization
status: done
track: data
priority: critical
effort: large
created: 2026-06-08
updated: 2026-06-16
owner: codex
depends_on: [LT-0016, LT-0083, LT-0085]
---

# LT-0086: Enforce user-scoped authorization

## Why

Authentication only proves who is signed in. The hosted app must also ensure
that users can only read and modify training records they own or are allowed to
share.

## Outcome

Core training data access paths enforce user ownership through the authenticated
request context.

## Scope

- In scope: user-scoped reads and writes for lifts, executions, planned
  workout session saves that write executions, authorization tests, and failure
  behavior.
- Out of scope: implementing the full coach program assignment workflow beyond
  the permissions required to protect shared records.

## Acceptance criteria

- [x] Data-access APIs require an authenticated user context.
- [x] Queries for private records are scoped by owner or permitted relationship.
- [x] Mutations reject records outside the current user's ownership or sharing
      rights.
- [x] Tests cover cross-user read, update, delete, and unavailable shared-record
      behavior.
- [x] Error messages do not reveal private records that the user cannot access.

## Notes

Implemented in two slices:

- local SQLite ownership bridge with `owner_user_id` on `lifts` and
  `lift_records`, backfilled to `local-user`;
- hosted JDBC/Postgres adapter that resolves each signed-in account to an app
  user and default lifter profile before reading or writing hosted catalog and
  execution rows.

The web server now requests a scoped `TrainingDataStore` for every protected
route. Cross-user read/update/delete tests cover both the SQLite bridge and the
hosted adapter.

Coach/lifter relationship tables and persisted hosted program sharing remain
future feature work because those shared records are not yet implemented as
runtime surfaces.

Verification:

- `.\gradlew.bat test --tests com.lifttrax.db.HostedPostgresTrainingDataStoreTest`
- `.\gradlew.bat qualityGate`
