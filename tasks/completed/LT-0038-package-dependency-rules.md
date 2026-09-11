---
id: LT-0038
title: Add package dependency rules
status: done
track: quality
priority: medium
effort: medium
created: 2026-05-30
updated: 2026-09-11
owner: unassigned
depends_on: [LT-0013]
---

# LT-0038: Add package dependency rules

## Why

Package boundaries are easier to preserve when the build can detect accidental coupling. This keeps web, data, schema, and training logic changes from becoming tangled.

## Outcome

Automated checks enforce the most important package dependency rules and document the intended direction of dependencies.

## Scope

- In scope: selecting an architecture test approach, defining allowed dependencies, adding tests, and documenting exceptions.
- Out of scope: large package reshuffling unless needed to make the first rule set honest.

## Acceptance criteria

- [x] Important package dependency rules are written in executable tests or static checks.
- [x] Rules cover at least web-to-domain, domain-to-data, and schema/training boundaries.
- [x] Existing violations are fixed or explicitly documented as temporary exceptions.
- [x] New violations fail with readable messages.
- [x] `qualityGate` passes.

## Notes

Implemented seven ArchUnit checks in `PackageDependencyTest`, run by the
existing JUnit suite and `qualityGate`. They scan production output explicitly
and reject an empty scan. `docs/code-organization-and-quality.md` records the
rules, allowed datastore contracts, renderer history access, and the deferred
legacy Swing boundary. No violations are suppressed.

Verified that a temporary model field referencing `Database` fails with the
offending field/type and rule explanation; removed the probe afterward.
Focused tests and `./gradlew.bat qualityGate` passed on 2026-09-11.
