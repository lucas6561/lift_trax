# M1: Quality baseline

## Goal

Make the engineering bar explicit and enforce it with one repeatable command.

## Exit criteria

- The project has a 90 percent coverage gate for the agreed scope.
- Static analysis failures block the build.
- Formatting, static analysis, tests, and coverage run through one documented command.
- CI runs the same quality gate.

## Tasks

- `LT-0011`: Raise coverage gate to 90 percent.
- `LT-0012`: Add a single quality gate command.
- `LT-0013`: Document package boundaries and coding standards.
- `LT-0014`: Add CI quality checks.

## Notes

The existing Gradle build already has PMD, Spotless, JUnit, and JaCoCo. This milestone should tighten and document what is already present before expanding the app surface area.
