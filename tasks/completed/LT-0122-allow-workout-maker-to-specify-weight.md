---
id: LT-0122
title: Allow the workout maker to specify planned weight
status: done
track: product
priority: high
effort: medium
created: 2026-11-02
updated: 2026-11-03
owner: unassigned
depends_on: [LT-0023]
---

# LT-0122: Allow the workout maker to specify planned weight

## Why

Planned sets can currently prescribe reps, percentages, and RPE, while LiftTrax
derives a suggested working weight from workout history. A workout author cannot
prescribe an exact load such as `225 lb`, so a workout that already has a chosen
weight loses that intent or requires the lifter to re-enter it during training.

## Outcome

The workout maker can assign an optional exact weight to a planned set. LiftTrax
preserves that prescription through workout generation, export, import, preview,
and Work Along, while keeping the performed weight editable and distinct from
the planned value.

## Scope

- In scope: workout-maker input, the versioned planned-workout contract and Java
  model, validation and compatibility, generated output, preview/print/Markdown
  display, Work Along defaults, and focused automated/browser tests.
- Out of scope: changing completed-set history, automatically selecting weights,
  plate-loading calculations, or replacing percentage- and history-based load
  suggestions for sets without an explicit weight.

## Acceptance criteria

- [x] The workout maker offers an optional weight for each planned set, using the
      same supported weight syntax and validation rules as workout logging.
- [x] A new backward-compatible workout schema version and the Java model can
      represent an explicit planned weight; older workout files remain importable.
- [x] Validation defines the relationship between exact weight, percentage, and
      RPE targets, rejects ambiguous or invalid combinations with an actionable
      message, and does not silently discard a supplied weight.
- [x] An explicit planned weight takes precedence over a history-derived weight
      suggestion. Sets without one retain the current percentage/history behavior.
- [x] App preview, print view, Markdown, and workout JSON clearly preserve and
      display the prescribed weight with its set target.
- [x] Work Along pre-fills the explicit planned weight for the corresponding set,
      but the lifter can record a different actual weight without changing the
      saved plan.
- [x] Import/export round trips preserve the planned weight exactly, including its
      unit, and account isolation is unchanged.
- [x] Focused model, schema, renderer, and session tests cover explicit weight,
      omitted weight, invalid input, precedence, and planned-versus-performed
      values.
- [x] Desktop and 390-by-844 phone browser checks cover authoring and following a
      weighted workout, including keyboard use and the JavaScript-disabled form
      path.
- [x] `qualityGate` passes.

## Notes

Keep the planned prescription separate from `completedSet.weight`, which records
what the lifter actually performed. Reuse the existing weight parser and display
conventions rather than introducing a second unit grammar.

## Completion — 2026-11-03

Added planned-workout format v7 with an optional per-set `weight`. The exact
weight uses the execution-log text conventions, round trips without changing
its value or unit, and is mutually exclusive with percentage and RPE load modes.
Versions 1 through 6 remain importable through their frozen schemas and readers.

Preview, print, Markdown, JSON export, and Work Along display the exact load with
its target. Exact weights take precedence over history suggestions. Work Along
pre-fills a single exact target directly and seeds the individual-set log when
prescribed weights differ across sets; editing performed weight does not mutate
the embedded plan. The native JavaScript-disabled form receives the same seed.

Updated the stable schema entrypoint, versioning and AI authoring guidance,
reviewed examples, and generated-output goldens. Verification passed:

- `./gradlew.bat qualityGate`: 412 Java tests (410 passed, 2 skipped), formatting,
  Java/JavaScript static analysis, behavior, and coverage gates.
- 108 JavaScript tests; aggregate 86.79% line / 73.39% branch coverage.
- `./gradlew.bat browserAcceptance -PbrowserSuite=workout-recovery`: desktop
  1280×900 and phone 390×844 import/preview/follow flows, keyboard editing,
  prescribed-versus-performed separation, draft isolation, and the existing
  JavaScript-disabled save path. Reviewed generated desktop, phone, and
  no-script screenshots.
