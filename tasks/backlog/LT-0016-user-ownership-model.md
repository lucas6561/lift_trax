---
id: LT-0016
title: Add user and ownership model design
status: idea
track: data
priority: high
effort: medium
created: 2026-05-27
updated: 2026-05-27
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

- [ ] The model identifies records owned by a user.
- [ ] The model explains coach-created programs shared with lifters.
- [ ] Database migration impact is documented.
- [ ] Authentication and authorization follow-up tasks are created.

## Notes

This should happen before plan and workout tables become deeply embedded.
