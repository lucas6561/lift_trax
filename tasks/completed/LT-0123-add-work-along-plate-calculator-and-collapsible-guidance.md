---
id: LT-0123
title: Add Work Along plate calculator and collapsible guidance
status: done
track: interface
priority: high
effort: medium
created: 2026-11-02
updated: 2026-11-03
owner: unassigned
depends_on: [LT-0025, LT-0095, LT-0120]
---

# LT-0123: Add Work Along plate calculator and collapsible guidance

## Why

Work Along places planned targets, exercise history, warm-up guidance, and the
logging controls in one long exercise card. The information is useful, but it
creates substantial scrolling on a phone when the lifter only needs one piece
of guidance at a time. The lifter must also calculate barbell plate loading
outside LiftTrax or mentally convert the target weight into plates per side.

## Outcome

Each Work Along exercise presents planned targets, history, warm-ups, and a
plate calculator as consistent, independently collapsible panes. The calculator
turns the current pound-denominated barbell target into an explicit plate list
for each side without changing the performed weight or submitted workout data.

## Scope

- In scope: native collapsible Work Along panes; history and warm-up rendering;
  pound-denominated bar and plate choices; symmetric plate-loading calculation;
  integration with suggested, entered, loaded-last, and restored weights;
  session-only preferences; accessibility; mobile layout; focused JavaScript,
  renderer, draft, and browser tests.
- Out of scope: kilogram plates, custom gym inventory, asymmetric loading,
  collar weight, photographic plate art or animations, changing warm-up
  prescriptions, automatically changing the logged weight, or making the
  optional calculator a requirement for saving a workout.

## Acceptance criteria

- [x] Planned Targets, History, Warm-ups, and Plate Calculator use consistent
      native `<details>` and `<summary>` panes within each applicable Work Along
      exercise. Each pane can be opened or closed independently using touch,
      mouse, or keyboard.
- [x] Document and test sensible initial open states. Closing every optional pane
      leaves the exercise name, logging fields, status, and save controls clear
      and usable without unnecessary blank space.
- [x] History becomes collapsible without losing its Last and Best 1RM values,
      unavailable/empty messages, live update after changing the performed lift,
      or its polite status semantics.
- [x] Warm-up guidance becomes collapsible without changing any generated
      warm-up sets, working-weight explanation, loading warning, rest guidance,
      intent, caution, or back-off note.
- [x] The Plate Calculator pane offers these unique bar weights: 5, 15, 35, 40, 45,
      and 65 pounds. The 45-pound option is visibly labeled as the default and is
      selected initially unless a session-restored selection exists.
- [x] Available plates are 45, 25, 10, 5, 2.5, and 0.5 pounds. Calculations use
      matched pairs and display the required count of each denomination per side,
      not a combined count that the lifter must divide mentally.
- [x] The result also shows a verification total composed of the selected bar
      plus both loaded sides, making it easy to catch a mistaken bar or plate.
- [x] The calculator follows the active exercise's current absolute-pound weight,
      including a history or planned suggestion copied into the logging field,
      manual edits, Load Last, block navigation, and device-draft restoration.
      It updates without submitting the workout form.
- [x] For bar-plus-bands loading, calculate only the entered bar-and-plate portion
      and clearly exclude band tension. For bands-only, bodyweight, time,
      distance, kilograms, or another unsupported mode, show a concise
      not-applicable explanation rather than producing a misleading plate list.
- [x] If the target is below the selected bar, invalid, or not exactly reachable
      with matched plate pairs, explain the problem. For an unreachable target,
      show the nearest attainable total and signed difference; never silently
      label an approximation as exact.
- [x] The loading algorithm uses decimal-safe units and a deterministic,
      minimum-plate solution. Tests cover every bar and plate denomination,
      multiple plates of one denomination, fractional targets, exact and nearest
      loads, targets below the bar, empty input, and unsupported measurement modes.
- [x] Bar choice and pane open/closed state remain stable while moving among
      workout blocks and are restored with the account-scoped device draft for
      the current workout session. They are not written to completed execution
      history or another account's draft.
- [x] With JavaScript disabled, native disclosure panes and normal Work Along
      logging remain usable. The optional live calculator may report that it
      requires JavaScript, but must not block or alter form submission.
- [x] Pane summaries, inputs, results, warnings, focus order, and touch targets
      are accessible and readable at desktop and approximately 390-by-844 phone
      layouts without horizontal overflow or obscuring sticky save controls.
- [x] Focused renderer and JavaScript tests cover disclosure markup, calculation,
      live synchronization, lift swaps, block navigation, and draft recovery.
      Real-browser checks cover keyboard and phone use, refresh/resume, and the
      JavaScript-disabled fallback.
- [x] JavaScript syntax, static-analysis, behavior, and coverage gates pass, and
      `./gradlew.bat qualityGate` passes.

## Notes

The requested bar list included 45 pounds twice; the selector should present it
once. This first version assumes sufficient matched plate pairs of each listed
denomination. Custom plate inventories can be considered separately if the
unlimited-pair assumption proves inconvenient in real use.

Before LT-0123, `PlannedWorkoutSessionHtml` rendered History as an always-visible
`.session-history` region and delegated the always-visible warm-up panel to
`PlannedWorkoutWarmupHtml`. Planned Targets already used native disclosure
markup, providing the pattern to unify rather than introducing a custom
accordion widget.

LT-0122 can later provide additional explicit planned-weight inputs, but this
calculator must also work with the existing suggested, manually entered, and
restored Work Along weights and therefore does not depend on LT-0122.
## Completion — 2026-11-02

Implemented native, independent guidance panes with Planned Targets initially
open and History, Warm-ups, and Plate Calculator initially closed. Existing
history updates and all warm-up prescriptions remain intact.

Added the five unique bar choices and decimal-safe, deterministic minimum-plate
loading per side, verification totals, explicit nearest-load differences, and
unsupported-mode messages. The calculator follows current logging fields and
Load last/restored values without changing execution payloads. Per-exercise bar
and disclosure choices stay in the existing account-scoped workout draft,
including for submitted blocks. Older drafts retain sensible defaults.

Added a native Work Along logging fallback for JavaScript-disabled browsers,
using the same server-side session validation. Removed a stray template marker
found during visual review. Documented defaults and calculator behavior in
`docs/saved-workouts.md` and updated the JavaScript verification inventory.

Validation passed:

- `./gradlew.bat qualityGate`: 409 Java tests (407 passed, 2 skipped), formatting,
  Java/JavaScript static analysis, syntax, behavior, and coverage gates.
- Java instruction coverage: 92.688%.
- 106 JavaScript tests; aggregate 86.58% line / 72.92% branch coverage. The new
  guidance module has 100% line / 95.06% branch coverage.
- `./gradlew.bat browserAcceptance -PbrowserSuite=workout-recovery`: desktop
  1280×900 and phone 390×844 keyboard, mouse/touch, layout, live calculation,
  reload/resume, block submission, draft cleanup, and actual JavaScript-disabled
  Work Along saving. Reviewed generated desktop, phone, and no-script images.

## Visual follow-up — 2026-11-03

At the user's request, replaced the dense visual calculation paragraph with a
simple bar-end diagram. A prominent “Load this on EACH SIDE” caption and matching
left/right instruction accompany individually labeled plates, largest first.
The per-side recipe and left-plus-right verification remain visible. Stacks
over eight plates use labeled groups with exact counts listed underneath.
Nearest-load warnings remain explicit, and unsupported/invalid weights clear
the diagram. The original polite textual result remains available to screen
readers. The diagram uses native HTML/CSS and requires no downloaded images.

Follow-up verification: 108 JavaScript tests pass, including plate order/labels,
nearest and bar-only loads, grouped large counts, and clearing stale diagrams.
New module coverage is 100% lines / 95.92% branches. Desktop and 390×844 phone
browser checks pass for individual eight-plate stacks and grouped large loads
without horizontal overflow; reviewed both generated diagram screenshots.
The full `qualityGate` also passes after the visual follow-up.

Added a 5 lb bar option on 2026-11-03 at the user's request. The 45 lb default
remains selected for new sessions. The existing denomination calculation and
draft-recovery checks now cover the 5 lb selection as well.
