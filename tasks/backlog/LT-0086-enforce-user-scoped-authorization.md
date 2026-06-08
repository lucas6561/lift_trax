---
id: LT-0086
title: Enforce user-scoped authorization
status: idea
track: data
priority: critical
effort: large
created: 2026-06-08
updated: 2026-06-08
owner: unassigned
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

This is the safety step that makes multi-user storage possible.
