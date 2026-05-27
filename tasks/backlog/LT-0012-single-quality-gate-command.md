---
id: LT-0012
title: Add a single quality gate command
status: ready
track: quality
priority: high
effort: small
created: 2026-05-27
updated: 2026-05-27
owner: unassigned
depends_on: [LT-0011]
---

# LT-0012: Add a single quality gate command

## Why

Developers need one obvious command that answers whether the project is shippable.

## Outcome

The repository documents and exposes a single quality gate that runs formatting checks, static analysis, tests, and coverage verification.

## Scope

- In scope: Gradle task wiring, README or Java project note updates, and verification that the command fails on each quality category.
- Out of scope: external CI setup.

## Acceptance criteria

- [ ] A documented command runs format checks, PMD, tests, and JaCoCo verification.
- [ ] The command fails on static analysis warnings or errors.
- [ ] The command fails when tests fail.
- [ ] The command fails when coverage is below the threshold.

## Notes

The existing `check` task already runs PMD and coverage verification. This task should decide whether `check` is the product quality gate or whether a clearer alias such as `qualityGate` should be added.
