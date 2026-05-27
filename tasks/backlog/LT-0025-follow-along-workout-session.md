---
id: LT-0025
title: Build the follow-along workout session
status: idea
track: interface
priority: high
effort: large
created: 2026-05-27
updated: 2026-05-27
owner: unassigned
depends_on: [LT-0024, LT-0007]
---

# LT-0025: Build the follow-along workout session

## Why

Lifters should be able to train from the app, enter data as sets are completed, and see what remains in the workout.

## Outcome

A workout session view shows planned work in order and lets the user enter completed set data as the workout progresses.

## Scope

- In scope: session screen, set entry, completion state, validation, mobile layout, and persistence hooks.
- Out of scope: advanced analytics.

## Acceptance criteria

- [ ] The session shows the current planned block and upcoming work.
- [ ] The user can enter completed weight, reps, rating, and notes where applicable.
- [ ] The user can mark planned work complete or skipped.
- [ ] The layout works on phone and desktop widths.
- [ ] Tests verify the rendered session and save behavior.

## Notes

This should feel usable during a real workout, not like an admin form.
