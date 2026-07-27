---
id: LT-0074
title: Expand mutation testing to active workout logging
status: done
track: quality
priority: high
effort: medium
created: 2026-06-07
updated: 2026-07-27
owner: unassigned
depends_on: [LT-0031, LT-0025, LT-0048]
---

# LT-0074: Expand mutation testing to active workout logging

## Why

The follow-along workout flow turns a planned day into durable execution
history. Small logic changes in set parsing, skipped exercises, swaps, notes,
dates, or repeated-set handling can silently corrupt training history even when
line coverage looks healthy.

## Outcome

Mutation testing covers the active-workout save path and exposes weak assertions
around completed set payloads, exercise swaps, and seeded execution inputs.

## Scope

- In scope: PIT targets for `PlannedWorkoutSessionService`, `WeightInputParser`, `ExecutionSetFormValues`, and closely related active-workout input/save helpers.
- Out of scope: visual regression testing, broad renderer mutation coverage, or redesigning the follow-along UI.

## Acceptance criteria

- [x] PIT targets include active-workout logging and execution-input helper classes.
- [x] Focused tests cover meaningful mutations around skipped exercises, swap choices, detailed sets, repeated fallback sets, dates, notes, and invalid values.
- [x] `./gradlew.bat pitest` reports surviving mutations for this slice in `build/reports/pitest/`.
- [x] At least one weak active-workout assertion is strengthened or a baseline of surviving mutants is documented.
- [x] The mutation-testing docs explain which active-workout classes are included and why renderer-heavy classes are still excluded.
- [x] `qualityGate` passes.

## Notes

Coordinate with `LT-0035` for malformed form submissions. This card is about
mutation-testing the save/input logic, not replacing route-level error tests.

PIT now covers `PlannedWorkoutSessionService`, `WeightInputParser`, and
`ExecutionSetFormValues` alongside the established workout targets. Tests were
strengthened for left/right weight units and exact metric-option selection,
killing an additional surviving mutant. The 2026-07-27 run generated 230
mutations, killed 206, reported 18 survivors plus 6 without coverage, and
finished at a 90 percent mutation score with 92 percent test strength.
`./gradlew.bat qualityGate` also passed.
