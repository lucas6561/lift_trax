---
id: LT-0034
title: Add golden workout output tests
status: idea
track: quality
priority: medium
effort: medium
created: 2026-05-30
updated: 2026-05-30
owner: unassigned
depends_on: [LT-0020, LT-0023]
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

- [ ] At least one representative program input generates a deterministic workout output file.
- [ ] Tests compare generated output against a checked-in expected fixture.
- [ ] Formatting or ordering is normalized so tests fail only for meaningful changes.
- [ ] The fixture update process is documented.
- [ ] `qualityGate` passes.

## Notes

This is most useful after the generic schema-to-wave path exists, but it can start with any stable generated workout path.
