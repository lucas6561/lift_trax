---
id: LT-0034
title: Add golden workout output tests
status: done
track: quality
priority: medium
effort: medium
created: 2026-05-30
updated: 2026-09-11
owner: unassigned
depends_on: [LT-0023]
---

# LT-0034: Add golden workout output tests

## Why

Generated workout files should not drift silently. Golden-file tests can make intended output changes obvious and keep generated plans stable across refactors.

## Outcome

Representative generated workouts are compared against checked-in expected output with a clear update workflow for intentional changes.

## Scope

- In scope: stable fixture inputs, deterministic output generation, golden JSON files, normalization rules, and documentation for updating expected files.
- Out of scope: snapshotting every possible program variant or using golden files as the only validation layer.

## Acceptance criteria

- [x] At least one representative program input generates a deterministic workout output file.
- [x] Tests compare generated output against a checked-in expected fixture.
- [x] Formatting or ordering is normalized so tests fail only for meaningful changes.
- [x] The fixture update process is documented.
- [x] `qualityGate` passes.

## Notes

Implemented `GoldenWorkoutOutputTest` for the existing conjugate and hypertrophy
builders, each generating two weeks from a fixed catalog and export timestamp.
Conjugate selection uses the existing deterministic randomizer. Every case
generates twice from fresh inputs and compares parsed JSON plus typed imports
against reviewed expected files in `src/test/resources/golden/workouts/`.
Whitespace and object-key order are ignored; workout/set array order remains
significant. The adjacent README documents inputs and the explicit review/copy
workflow. Candidates go only to `build/golden/workouts/`.

LT-0020 is no longer a prerequisite for this initial slice: these tests cover
the stable existing builders, as permitted by the original scope. Add fixtures
for the generic schema-to-wave path when it exists.

Verified that changing a prescribed percentage in the expected conjugate file
fails the comparison; restored the reviewed fixture afterward. Focused tests
and `./gradlew.bat qualityGate` passed on 2026-09-11.
