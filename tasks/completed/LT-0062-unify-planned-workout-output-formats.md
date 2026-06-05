---
id: LT-0062
title: Unify planned workout output formats
status: done
track: product
priority: high
effort: medium
created: 2026-06-05
updated: 2026-06-05
owner: unassigned
depends_on: [LT-0023, LT-0024, LT-0025]
---

# LT-0062: Unify planned workout output formats

## Why

Planned workouts need to be useful in more than one context: browser preview, follow-along sessions, printable sheets, and generated text or markdown output.

## Outcome

Planned-workout output paths share the same target formatting and local-history context while exposing readable browser, print, text, and markdown views.

## Scope

- In scope: shared formatting helpers, print-friendly planned workouts, markdown/text output, browser route updates, and renderer tests.
- Out of scope: long-term analytics, PDF export automation, or editing planned workouts from printed output.

## Acceptance criteria

- [x] Browser preview, follow-along, print, text, and markdown paths format planned targets consistently.
- [x] Local-history context is shared instead of reimplemented per output format.
- [x] The print layout can split long workout days without orphaning week headings.
- [x] Documentation or route copy points users toward the available output paths.
- [x] Renderer tests cover the key output paths.
- [x] `qualityGate` passes.

## Notes

Retrospective card for the planned-workout output cleanup and print-layout work
completed across the 2026-06-04 updates. Implementation introduced
`PlannedWorkoutText`, `PlannedWorkoutMarkdownWriter`,
`PlannedWorkoutPrintHtml`, and shared `PlannedWorkoutHistory` lookup behavior.

Verification:

- `.\gradlew.bat qualityGate`
