---
id: LT-0095
title: Add offline workout-session drafts
status: doing
track: interface
priority: critical
effort: large
created: 2026-06-15
updated: 2026-07-21
owner: codex
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

2026-07-18 implementation slice:

- Free hosting is a product constraint, and Render can sleep during a workout.
- An active workout must survive at least 2.5 hours of server inactivity,
  browser refresh/interruption, and a cold server at final submission without
  losing entered progress.
- The first slice scopes browser drafts to the authenticated user, exposes
  unsynced status, wakes the server before final submission, and retries a save
  while retaining the local draft until the server confirms success.
- Keep this card open for explicit conflict detection and discard controls.

2026-07-21 work-along recovery slice:

- Submit each completed block to hosted history immediately, including the final
  block, and keep the device draft as protection for only the unfinished work
  and reconnect position.
- Keep the current-block actions below the block so submission happens after
  data entry rather than from the sticky progress header.
- Treat the session-rendering POST as read-only and refresh the open form's CSRF
  token after waking the server so a restored Chrome tab can submit safely.
