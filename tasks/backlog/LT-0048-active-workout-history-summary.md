---
id: LT-0048
title: Show history in active workout
status: idea
track: interface
priority: high
effort: medium
created: 2026-05-30
updated: 2026-05-30
owner: unassigned
depends_on: [LT-0025, LT-0027]
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

- [ ] The active workout view shows recent history for the current exercise when available.
- [ ] Missing history is handled cleanly without empty clutter.
- [ ] History data is grouped and formatted for quick scanning.
- [ ] Tests cover rendering with and without prior history.
- [ ] `qualityGate` passes.

## Notes

This should reuse existing history formatting where possible.
