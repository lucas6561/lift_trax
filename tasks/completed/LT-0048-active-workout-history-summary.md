---
id: LT-0048
title: Show history in active workout
status: done
track: interface
priority: high
effort: medium
created: 2026-05-30
updated: 2026-06-05
owner: unassigned
depends_on: [LT-0025]
---

# LT-0048: Show history in active workout

## Why

Users often need last-session context while deciding whether today's set is appropriate. That information should be visible without leaving the workout.

## Outcome

The active workout view shows compact history for the current exercise, including recent performance and useful bests.

## Scope

- In scope: last performed values, recent set history, best estimated 1RM or top set where available, and compact rendering.
- Out of scope: full charting, advanced analytics, or editing old sessions from the active workout view.

## Acceptance criteria

- [x] The active workout view shows recent history for the current exercise when available.
- [x] Missing history is handled cleanly without empty clutter.
- [x] History data is grouped and formatted for quick scanning.
- [x] Tests cover rendering with and without prior history.
- [x] `qualityGate` passes.

## Notes

Completed with compact `Last:` and `Best 1RM:` context inside follow-along
exercise cards. The session view reuses `PlannedWorkoutHistory` and
`WorkoutHistoryFormatter`, so follow-along, preview, print, and generated output
share the same local-history behavior.

The original card depended on `LT-0027`, but the shipped slice only needs local
execution history through the existing database path. Durable planned-versus-
completed session storage remains `LT-0027` scope.

Verification:

- `.\gradlew.bat qualityGate`
