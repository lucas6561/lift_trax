---
id: LT-0038
title: Add package dependency rules
status: idea
track: quality
priority: medium
effort: medium
created: 2026-05-30
updated: 2026-05-30
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

- [ ] Important package dependency rules are written in executable tests or static checks.
- [ ] Rules cover at least web-to-domain, domain-to-data, and schema/training boundaries.
- [ ] Existing violations are fixed or explicitly documented as temporary exceptions.
- [ ] New violations fail with readable messages.
- [ ] `qualityGate` passes.

## Notes

ArchUnit or a similarly focused approach may fit this well if it does not add too much build complexity.
