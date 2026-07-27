---
id: LT-0014
title: Add CI quality checks
status: done
track: quality
priority: high
effort: medium
created: 2026-05-27
updated: 2026-07-27
owner: unassigned
depends_on: [LT-0012]
---

# LT-0014: Add CI quality checks

## Why

The quality gate should run automatically before changes are accepted, not only on a developer machine.

## Outcome

A CI workflow runs the project quality gate on every pull request or main branch update.

## Scope

- In scope: CI workflow configuration, Java setup, Gradle cache, test reports, and coverage reports.
- Out of scope: deployment automation.

## Acceptance criteria

- [x] CI runs the documented quality gate.
- [x] CI fails on formatting, static analysis, test, or coverage failures.
- [x] CI publishes or preserves useful test and static analysis reports.
- [x] The README explains how local checks match CI.

## Notes

GitHub Actions is the likely first target unless the repository is hosted elsewhere.

Completed with `.github/workflows/quality.yml`. Pull requests and pushes to
`main` run the same `./gradlew qualityGate` command documented for local use,
with Gradle caching and always-uploaded test, PMD, and JaCoCo reports.
`./gradlew.bat qualityGate` passed locally on 2026-07-27.
