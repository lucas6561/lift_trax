---
id: LT-0086
title: Enforce user-scoped authorization
status: doing
track: data
priority: critical
effort: large
created: 2026-06-08
updated: 2026-06-15
owner: codex
depends_on: [LT-0016, LT-0083, LT-0085]
---

# LT-0086: Enforce user-scoped authorization

## Why

Authentication only proves who is signed in. The hosted app must also ensure
that users can only read and modify training records they own or are allowed to
share.

## Outcome

All hosted data access paths enforce user ownership or explicit sharing rules.

## Scope

- In scope: user-scoped reads and writes for lifts, executions, planned
  workouts, programs, catalog entries, coach/lifter sharing, authorization tests,
  and failure behavior.
- Out of scope: implementing the full coach program assignment workflow beyond
  the permissions required to protect shared records.

## Acceptance criteria

- [ ] Data-access APIs require an authenticated user context.
- [ ] Queries for private records are scoped by owner or permitted relationship.
- [ ] Mutations reject records outside the current user's ownership or sharing
      rights.
- [ ] Tests cover cross-user read, update, delete, and shared-program cases.
- [ ] Error messages do not reveal private records that the user cannot access.

## Notes

This is the safety step that makes multi-user storage possible. Java route and
data-access checks remain required even if Supabase Row Level Security is added
as defense in depth.

Build on `WebAuth.currentUser(exchange)` from `LT-0085` for the current stable
user identifier, then apply owner or relationship predicates from
`docs/hosted-user-data-schema.md`.

2026-06-14 first implementation slice:

- added local SQLite `owner_user_id` columns for lifts and lift records;
- backfilled legacy local data to `local-user`;
- added a scoped `TrainingDataStore` facade used by authenticated web routes;
- allowed duplicate lift names across different owners;
- added tests for cross-user lift visibility and execution read/update/delete
  rejection.

Remaining LT-0086 work includes persisted program/planned-workout ownership,
coach/lifter relationship permissions, and hosted Postgres/RLS enforcement.

2026-06-15 hosted batch note:

- keep this card open while the hosted persistence adapter is introduced;
- the next authorization slice should cover planned workout/session persistence
  and the coach/lifter relationship predicates from
  `docs/hosted-user-data-schema.md`;
- `LT-0087`, `LT-0088`, and `LT-0090` must not be treated as public-hosting
  ready until this remaining scope is covered.
