---
id: LT-0095
title: Add offline workout-session drafts
status: doing
track: interface
priority: critical
effort: large
created: 2026-06-15
updated: 2026-07-24
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

- [x] Once a changed value is visible in the workout form, the current workout
      state is recoverable from browser storage. Persistence does not depend on
      a periodic timer or a page shutdown event firing.
- [x] A successful local write shows when the workout was last saved on this
      device. Blocked, unavailable, or full browser storage shows an error and
      never claims that the workout is backed up.
- [x] The Dashboard discovers drafts belonging to the current signed-in user
      and offers one-tap Resume and explicit, confirmed Discard actions.
- [x] The resumed workout uses a reload-safe GET URL and restores the exact
      training date, current block, entered sets, skips, substitutions, and
      notes without re-importing or reselecting the workout.
- [x] Drafts are stored under the immutable signed-in user identity. A different
      signed-in account on the same browser cannot discover, resume, discard, or
      submit another user's draft.
- [x] The UI clearly distinguishes data saved only on the current device,
      submission in progress, and data confirmed in hosted history.
- [x] Sync validates the draft server-side before writing completed executions.
- [x] Every workout session and block submission has a stable idempotency key.
      Losing a successful response and retrying produces exactly one history
      entry.
- [x] Reusing a submission key with changed data is detected as a conflict and
      shown to the user instead of silently overwriting or duplicating history.
- [x] A draft remains available until hosted completion is confirmed or the user
      explicitly confirms Discard.
- [x] Automated tests cover same-user draft recovery, reload-safe resume,
      cross-user draft isolation, unavailable browser storage, repeated
      submission, changed-payload conflict, successful completion cleanup, and
      discard.
- [ ] A real-phone smoke check covers screen lock/app switching, force-closing
      Chrome, reopening the app, reconnecting after network loss, and submitting
      to a cold hosted server. The exercise values and current block must return,
      and hosted history must contain one copy.
- [x] `qualityGate` passes.

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

2026-07-24 phone interruption requirements:

- Chrome remaining alive or connected is not a product requirement. Workout
  recovery after suspension, discard, process termination, reload, network
  loss, and hosted cold start is the requirement.
- Cover workouts lasting at least 2.5 hours.
- Treat the browser draft as the durable working copy until the server confirms
  each submission.
- This card owns the phone-resume behavior formerly described only in
  `LT-0064`. Cross-device merge automation remains out of scope.

2026-07-24 implementation status:

- Desktop Chrome verified that typed Notes and RPE values survive a reload of
  the GET resume URL and return with the unsynced-work message.
- Existing version 2 drafts are migrated when their original workout page is
  re-rendered, so deploying this change does not overwrite an in-progress
  browser backup before restoration.
- Keep this card in `doing` until the real Android Chrome interruption and cold
  hosted-server smoke check is complete.
