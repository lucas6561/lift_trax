---
id: LT-0016
title: Add user and ownership model design
status: done
track: data
priority: high
effort: medium
created: 2026-05-27
updated: 2026-06-14
owner: unassigned
depends_on: [LT-0015, LT-0005]
---

# LT-0016: Add user and ownership model design

## Why

The app will eventually support multiple users, which means lifts, programs, workouts, and executions need clear ownership rules.

## Outcome

A documented data model describes users, coaches, lifters, ownership, sharing, and migration impact before multi-user code is added.

## Scope

- In scope: conceptual model, database impact, privacy assumptions, and migration strategy.
- Out of scope: authentication implementation.

## Acceptance criteria

- [x] The model identifies records owned by a user.
- [x] The model explains coach-created programs shared with lifters.
- [x] Database migration impact is documented.
- [x] Authentication and authorization follow-up tasks are created.

## Notes

Completed with `docs/user-ownership-model.md`. The model separates signed-in
users from lifter profiles, keeps training data private by default, assigns
local imports to the signed-in user's default lifter profile, and preserves a
bounded coach-to-lifter sharing path for program assignment.

Follow-up tasks:

1. `LT-0083`: Design hosted user data schema.
2. `LT-0085`: Implement account authentication.
3. `LT-0086`: Enforce user-scoped authorization.
4. `LT-0088`: Import local database to hosted account.
5. `LT-0091`: Add hosted backup and export controls.
