---
id: LT-0122
title: Allow the workout maker to specify planned weight
status: idea
track: product
priority: high
effort: medium
created: 2026-11-02
updated: 2026-11-02
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

- [ ] The workout maker offers an optional weight for each planned set, using the
      same supported weight syntax and validation rules as workout logging.
- [ ] A new backward-compatible workout schema version and the Java model can
      represent an explicit planned weight; older workout files remain importable.
- [ ] Validation defines the relationship between exact weight, percentage, and
      RPE targets, rejects ambiguous or invalid combinations with an actionable
      message, and does not silently discard a supplied weight.
- [ ] An explicit planned weight takes precedence over a history-derived weight
      suggestion. Sets without one retain the current percentage/history behavior.
- [ ] App preview, print view, Markdown, and workout JSON clearly preserve and
      display the prescribed weight with its set target.
- [ ] Work Along pre-fills the explicit planned weight for the corresponding set,
      but the lifter can record a different actual weight without changing the
      saved plan.
- [ ] Import/export round trips preserve the planned weight exactly, including its
      unit, and account isolation is unchanged.
- [ ] Focused model, schema, renderer, and session tests cover explicit weight,
      omitted weight, invalid input, precedence, and planned-versus-performed
      values.
- [ ] Desktop and 390-by-844 phone browser checks cover authoring and following a
      weighted workout, including keyboard use and the JavaScript-disabled form
      path.
- [ ] `qualityGate` passes.

## Notes

Keep the planned prescription separate from `completedSet.weight`, which records
what the lifter actually performed. Reuse the existing weight parser and display
conventions rather than introducing a second unit grammar.
