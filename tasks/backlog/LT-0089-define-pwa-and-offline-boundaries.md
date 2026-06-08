---
id: LT-0089
title: Define PWA and offline boundaries
status: idea
track: interface
priority: medium
effort: medium
created: 2026-06-08
updated: 2026-06-08
owner: unassigned
depends_on: [LT-0015, LT-0085, LT-0087]
---

# LT-0089: Define PWA and offline boundaries

## Why

The hosted app should work well on phones, but offline behavior can create data
conflicts if it is added casually.

## Outcome

LiftTrax has a documented PWA plan that separates installability and shell
caching from any future offline write/sync behavior.

## Scope

- In scope: PWA manifest expectations, service-worker cache boundaries,
  authenticated-page caching rules, offline read/write policy, conflict risks,
  and follow-up tasks for any offline training-session drafts.
- Out of scope: implementing full offline sync.

## Acceptance criteria

- [ ] The plan states which screens can be cached safely.
- [ ] Authenticated user data is not cached in a way that leaks between users or
      devices.
- [ ] Offline writes are either explicitly deferred or scoped to a draft model
      with conflict handling.
- [ ] Follow-up implementation tasks are created for PWA installability and any
      offline draft behavior.
- [ ] The decision is reflected in distribution documentation.

## Notes

Phone usability remains required even before PWA installability ships.
