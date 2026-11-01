---
id: LT-0117
title: Simplify catalog and workout algorithms
status: complete
track: quality
priority: high
effort: large
created: 2026-11-01
updated: 2026-11-01
owner: unassigned
depends_on: [LT-0113]
---

# LT-0117: Simplify catalog and workout algorithms

## Why

Catalog preview, planned-workout formatting, schema validation, warmup loading,
and history calculations contain LT-0113 NPath or cyclomatic exceptions.

## Outcome

Decision-heavy algorithms are expressed through focused helpers or explicit
domain values without changing generated workouts, import conflicts, loading
choices, schema compatibility, or history results.

## Acceptance criteria

- [x] Every LT-0117 exception in `docs/source-size-and-complexity.md` passes its
      listed limits.
- [x] Remove every LT-0117 XPath exception from `config/pmd/ruleset.xml`.
- [x] Preserve golden workout outputs and focused boundary tests.
- [x] Run relevant mutation tests and `./gradlew.bat qualityGate` without
      widening a threshold.

## Implementation notes

Catalog preview, workout formatting, schema validation, warmup selection, loading candidates, and history calculations now use smaller decision-focused helpers.

The matching production or test PMD ruleset now passes without suppression
clauses, and the LT-0113 thresholds were not widened.
