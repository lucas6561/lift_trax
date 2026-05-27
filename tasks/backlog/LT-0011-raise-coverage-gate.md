---
id: LT-0011
title: Raise coverage gate to 90 percent
status: ready
track: quality
priority: high
effort: medium
created: 2026-05-27
updated: 2026-05-27
owner: unassigned
depends_on: []
---

# LT-0011: Raise coverage gate to 90 percent

## Why

The project goal is that tests must have at least 90 percent coverage before a build can pass.

## Outcome

The Gradle coverage verification task enforces a 90 percent minimum for the agreed coverage scope, and the test suite is expanded until the gate passes.

## Scope

- In scope: JaCoCo threshold change, coverage scope review, targeted tests for uncovered stable code, and documentation of the coverage policy.
- Out of scope: chasing coverage on throwaway prototypes or generated code unless the coverage policy includes them.

## Acceptance criteria

- [ ] `jacocoTestCoverageVerification` requires at least 90 percent covered instructions for the agreed scope.
- [ ] `check` fails when the coverage gate fails.
- [ ] The coverage scope is documented in the build or project notes.
- [ ] A local quality run passes with the new threshold.

## Notes

The current build enforces 80 percent coverage over an explicit class include list. Decide whether 90 percent applies to that list first or to a broader package set.
