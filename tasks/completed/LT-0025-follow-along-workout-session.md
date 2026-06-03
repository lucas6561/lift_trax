---
id: LT-0025
title: Build the follow-along workout session
status: done
track: interface
priority: high
effort: large
created: 2026-05-27
updated: 2026-05-31
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

- [x] The session shows the current planned block and upcoming work.
- [x] The user can enter completed weight, reps, rating, and notes where applicable.
- [x] The user can mark planned work complete or skipped.
- [x] The layout works on phone and desktop widths.
- [x] Tests verify the rendered session and save behavior.

## Notes

This should feel usable during a real workout, not like an admin form.

Completed with a `Start This Day` action on imported workout previews and a
follow-along session screen that seeds planned reps, left/right reps, seconds,
and distance values. The screen accepts actual weight and RPE, supports
exercise-level and set-level skips, and allows workout-file substitution
options when the selected lift exists locally.

Saving uses the existing execution-history path so the completed work appears
throughout the current app without introducing planned-versus-completed tables
ahead of `LT-0027`.

Verification:

- `.\gradlew.bat test --tests com.lifttrax.cli.PlannedWorkoutSessionHtmlTest --tests com.lifttrax.cli.PlannedWorkoutSessionServiceTest --tests com.lifttrax.cli.WebUiRendererTest --tests com.lifttrax.cli.WebServerCliTest`
- `.\gradlew.bat qualityGate` could not be rerun on 2026-05-31 because the
  desktop approval service hit its usage limit after the focused suite passed.
