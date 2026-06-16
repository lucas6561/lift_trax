---
id: LT-0095
title: Add offline workout-session drafts
status: idea
track: interface
priority: medium
effort: large
created: 2026-06-15
updated: 2026-06-15
owner: unassigned
depends_on: [LT-0089, LT-0086, LT-0087]
---

# LT-0095: Add offline workout-session drafts

## Why

Training can happen in spotty gyms, but offline writes are risky unless drafts
are clearly separate from completed hosted history.

## Outcome

A signed-in user can keep a local draft of an active planned-workout session and
sync it deliberately when the hosted server is reachable.

## Scope

- In scope: user-scoped local draft storage, unsynced state, discard behavior,
  server-side validation on sync, conflict messaging, and tests.
- Out of scope: general offline browsing, automatic background sync, and
  cross-device merge automation.

## Acceptance criteria

- [ ] Drafts are stored under the current signed-in user identity.
- [ ] The UI clearly distinguishes unsynced draft data from saved hosted
      history.
- [ ] Sync validates the draft server-side before writing completed executions.
- [ ] Conflicts with newer hosted data are detected and shown to the user.
- [ ] Tests cover same-user draft recovery, cross-user draft isolation, conflict
      handling, and discard.

## Notes

Build this after the hosted persistence adapter is available so the final sync
path uses the same authorization checks as normal hosted logging.
