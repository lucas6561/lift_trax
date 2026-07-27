---
id: LT-0097
title: Enforce whole-code unit test coverage
status: done
track: quality
priority: high
effort: large
created: 2026-07-27
updated: 2026-07-27
owner: unassigned
depends_on: [LT-0011, LT-0012]
---

# LT-0097: Enforce whole-code unit test coverage

## Why

The 90 percent JaCoCo gate covered an allowlisted subset of production classes,
so unlisted code could remain untested without affecting the build.

## Outcome

The aggregate 90 percent instruction-coverage gate applies to every production
Java class, supported by unit and route-level tests across the previously
excluded CLI, web, persistence, and workout-configuration code.

## Scope

- In scope: remove the JaCoCo production-class allowlist, add focused tests for
  uncovered behavior, and document the repository-wide expectation.
- Out of scope: generated code exclusions, per-class coverage thresholds, and
  browser visual regression testing.

## Acceptance criteria

- [x] JaCoCo measures every class under the production class directories.
- [x] The 90 percent aggregate instruction-coverage verification remains active.
- [x] Tests cover meaningful behavior in the formerly excluded production code.
- [x] The README and project guide describe the whole-code coverage gate.
- [x] `./gradlew.bat qualityGate` passes.

## Notes

Removing the allowlist exposed a 74.89 percent baseline. The added tests raised
whole-production-code instruction coverage to 28,561 of 31,598 instructions,
or 90.389 percent, across 209 tests. `./gradlew.bat qualityGate` passed on
2026-07-27.
