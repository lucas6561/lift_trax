---
id: LT-0081
title: Configure between-set rest time
status: ready
track: interface
priority: high
effort: small
created: 2026-06-07
updated: 2026-06-07
owner: unassigned
depends_on: [LT-0044]
---

# LT-0081: Configure between-set rest time

## Why

Different lifts and training styles need different rest windows. A lifter should be able to choose the reset/rest duration used between sets instead of relying on a fixed timer value.

## Outcome

The active workout rest timer can use a user-selected between-set rest duration, remember that choice where appropriate, and reset to that duration when the lifter finishes or prepares for the next set.

## Scope

- In scope: a simple control for setting the between-set rest duration, validation for reasonable values, persistence or session carry-forward of the selected duration, and active workout integration.
- Out of scope: complex interval templates, wearable or push-notification timers, coach-level rest prescriptions, or automatic rest recommendations.

## Acceptance criteria

- [ ] The active workout UI lets a lifter set the rest duration used between sets.
- [ ] Resetting or restarting the rest timer uses the configured duration instead of only a hard-coded default.
- [ ] The selected duration is retained for the current workout session, and persists beyond the session if the chosen implementation already has an appropriate settings location.
- [ ] Invalid or extreme durations are handled with clear validation and do not break workout logging.
- [ ] Tests cover duration parsing, timer reset behavior, and session/settings retention.
- [ ] `qualityGate` passes.

## Notes

This is a focused follow-up to `LT-0044`. If `LT-0044` is implemented later as a larger slice, this card can be satisfied by including configurable reset/rest duration in that implementation.
