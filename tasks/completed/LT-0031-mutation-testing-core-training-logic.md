---
id: LT-0031
title: Add mutation testing for core training logic
status: done
track: quality
priority: medium
effort: medium
created: 2026-05-30
updated: 2026-06-07
owner: unassigned
depends_on: [LT-0012]
---

# LT-0031: Add mutation testing for core training logic

## Why

The training logic should be protected by tests that fail when meaningful behavior changes. Mutation testing can expose places where the code is covered but the assertions are too weak.

## Outcome

A mutation-testing workflow checks high-value training logic and reports surviving mutations that need better assertions or clearer behavior.

## Scope

- In scope: mutation-test tool selection, configuration for core training packages, documentation, and a small initial quality threshold.
- Out of scope: requiring mutation testing for every package immediately, replacing unit tests, or blocking all development on a perfect mutation score.

## Acceptance criteria

- [x] A mutation-testing tool is configured for the Java project.
- [x] The first target package or package set is documented.
- [x] Surviving mutations are reported in a repeatable local command.
- [x] At least one weak test is improved or a baseline is documented for future tightening.
- [x] `qualityGate` passes.

## Notes

Implemented with PIT via the Gradle `pitest` task. The initial mutation target is
`com.lifttrax.workout.ConjugateWorkoutBuilder` and
`com.lifttrax.workout.WaveMarkdownWriter`, documented in `README.md` and
`JAVA_PROJECT.md`. Reports are written to `build/reports/pitest/` with
timestamped report folders disabled.

Verification completed 2026-06-07:

- `./gradlew.bat test --tests com.lifttrax.workout.ConjugateWorkoutBuilderTest`
- `./gradlew.bat pitest` - 108 mutations generated, 97 killed, 90% mutation score.
- `./gradlew.bat qualityGate`
