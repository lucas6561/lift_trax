---
id: LT-0073
title: Expand mutation testing to planned workout files
status: done
track: quality
priority: high
effort: medium
created: 2026-06-07
updated: 2026-11-19
owner: unassigned
depends_on: [LT-0031, LT-0023, LT-0061, LT-0062]
---

# LT-0073: Expand mutation testing to planned workout files

## Why

Planned workout import, export, and display formatting sit on the boundary
between generated plans, AI-produced JSON, and the in-app workout experience.
Mutation testing should cover this path so schema-compatible files keep loading
and generated output does not drift under weak assertions.

## Outcome

The PIT mutation workflow includes the highest-value planned-workout file
classes and reports surviving mutations that need stronger import/export tests.

## Scope

- In scope: PIT targets for planned-workout JSON reading/writing, export, text formatting, schema-version dispatch, focused test improvements, and documentation of the expanded target set.
- Out of scope: changing schema semantics, replacing schema contract tests, or snapshotting every generated workout variant.

## Acceptance criteria

- [x] PIT targets include planned-workout import/export classes such as `PlannedWorkoutJson`, `PlannedWorkoutExporter`, `PlannedWorkoutText`, and closely related schema-version helpers.
- [x] The expanded target set is documented in the mutation-testing workflow docs.
- [x] `./gradlew.bat pitest` reports surviving mutations for this slice in `build/reports/pitest/`.
- [x] At least one surviving or likely-surviving mutation is killed by strengthening planned-workout file tests, or a clear baseline is documented for future tightening.
- [x] The existing mutation threshold is not lowered without an explicit baseline note.
- [x] `qualityGate` passes.

## Notes

Coordinate with `LT-0032` and `LT-0034` so mutation testing complements schema
contract and golden-output tests instead of duplicating them.

PIT now covers planned-workout JSON reading/writing, wave export, text
formatting, planned-workout schema dispatch, and the shared schema catalog.
Focused metadata and version-support assertions killed 13 additional mutants:
the full target set improved from 385 of 445 killed (87 percent, 91 percent test
strength) to 398 of 445 killed (89 percent, 94 percent test strength). The
planned-workout slice killed 194 of 216 mutants (90 percent) with 97 percent
test strength. `./gradlew.bat qualityGate` passed on 2026-11-19.
