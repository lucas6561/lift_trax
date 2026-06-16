---
id: LT-0089
title: Define PWA and offline boundaries
status: done
track: interface
priority: medium
effort: medium
created: 2026-06-08
updated: 2026-06-15
owner: codex
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

- [x] The plan states which screens can be cached safely.
- [x] Authenticated user data is not cached in a way that leaks between users or
      devices.
- [x] Offline writes are either explicitly deferred or scoped to a draft model
      with conflict handling.
- [x] Follow-up implementation tasks are created for PWA installability and any
      offline draft behavior.
- [x] The decision is reflected in distribution documentation.

## Notes

Phone usability remains required even before PWA installability ships.

Completed with `docs/pwa-offline-boundaries.md`.

The first PWA slice is installability only: manifest, icons, static user-neutral
asset caching, and a generic offline fallback. Authenticated pages, history
fragments, execution data, and auth responses remain network-only. Offline
writes are deferred until a separate user-scoped draft model exists.

Follow-up implementation tasks:

1. `LT-0094`: Add PWA installability shell.
2. `LT-0095`: Add offline workout-session drafts.
