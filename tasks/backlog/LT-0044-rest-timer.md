---
id: LT-0044
title: Add rest timer
status: idea
track: interface
priority: high
effort: medium
created: 2026-05-30
updated: 2026-05-30
owner: unassigned
depends_on: [LT-0025]
---

# LT-0044: Add rest timer

## Why

Rest timing is part of following a workout. A built-in timer keeps the session flow inside LiftTrax instead of forcing the user to switch apps.

## Outcome

The follow-along workout screen includes a rest timer that can start after a set, be adjusted, paused, reset, and clearly displayed on mobile.

## Scope

- In scope: timer controls, default rest durations, optional per-exercise or per-block duration hints, and session-screen integration.
- Out of scope: push notifications, background mobile alarms, wearable integration, or complex interval programming.

## Acceptance criteria

- [ ] The active workout view can start, pause, reset, and adjust a rest timer.
- [ ] The timer is readable on desktop and phone layouts.
- [ ] Completing a set can offer to start the appropriate timer.
- [ ] Timer state does not corrupt workout logging if the page is refreshed.
- [ ] `qualityGate` passes.

## Notes

Keep the first version simple. It can become more advanced after the follow-along session is stable.
