---
id: LT-0044
title: Add configurable rest timer
status: ready
track: interface
priority: high
effort: medium
created: 2026-05-30
updated: 2026-10-27
owner: unassigned
depends_on: [LT-0025]
---

# LT-0044: Add configurable rest timer

## Why

Rest timing is part of following a workout. A built-in timer keeps the session flow inside LiftTrax instead of forcing the user to switch apps.

## Outcome

The follow-along workout screen includes a rest timer that can start after a
set, be adjusted, paused, reset, and clearly displayed on mobile. The lifter can
choose the between-set duration and reuse it throughout the session.

## Scope

- In scope: timer controls, configurable between-set duration, validation,
  session retention, default durations, optional per-exercise or per-block
  duration hints, and session-screen integration.
- Out of scope: push notifications, background mobile alarms, wearable integration, or complex interval programming.

## Acceptance criteria

- [ ] The active workout view can start, pause, reset, and adjust a rest timer.
- [ ] The lifter can set a reasonable between-set duration, and invalid or
      extreme values produce clear validation without affecting workout logging.
- [ ] Restarting or resetting uses the configured duration instead of a
      hard-coded default.
- [ ] The selected duration is retained for the current workout session and is
      restored with the device draft; longer-lived persistence is used only if
      an appropriate account setting exists.
- [ ] The timer is readable on desktop and phone layouts.
- [ ] Completing a set can offer to start the appropriate timer.
- [ ] Timer state does not corrupt workout logging if the page is refreshed.
- [ ] Tests cover duration parsing, validation, start/pause/reset behavior,
      configured reset behavior, draft retention, and logging independence.
- [ ] `qualityGate` passes.

## Notes

This card now includes the configurable-duration scope formerly tracked by
`LT-0081`. Keep the first version simple; push notifications, background alarms,
wearables, and complex interval programming remain out of scope.
