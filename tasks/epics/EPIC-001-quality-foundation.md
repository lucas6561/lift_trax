# EPIC-001: Quality foundation

## Goal

Make LiftTrax reliable to change by enforcing formatting, static analysis, tests, coverage, and documentation expectations before code can be considered shippable.

## Why

The app is headed toward a larger product with program schemas, persistence, mobile workflows, and eventual distribution. That work needs a strict safety net so future features do not make the system harder to understand or easier to break.

## Success criteria

- A single quality command runs formatting checks, static analysis, tests, and coverage verification.
- The build fails on static analysis warnings or errors.
- The coverage gate is 90 percent for the agreed coverage scope.
- CI runs the same checks.
- Coding standards and package boundaries are documented.

## Related tasks

- `LT-0011`: Raise coverage gate to 90 percent.
- `LT-0012`: Add a single quality gate command.
- `LT-0013`: Document package boundaries and coding standards.
- `LT-0014`: Add CI quality checks.
- `LT-0010`: Document architecture decisions.
