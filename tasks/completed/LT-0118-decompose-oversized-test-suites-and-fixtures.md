---
id: LT-0118
title: Decompose oversized test suites and fixtures
status: complete
track: quality
priority: high
effort: large
created: 2026-11-01
updated: 2026-11-01
owner: unassigned
depends_on: [LT-0113]
---

# LT-0118: Decompose oversized test suites and fixtures

## Why

`WebServerCliTest`, `SqliteDb`, and several end-to-end test methods exceed the
test-source limits enabled by LT-0113. Their exact exceptions prevent further
growth but leave multiple behaviors coupled in single fixtures and tests.

## Outcome

Test suites and fixtures are split by behavior while retaining meaningful
end-to-end assertions, isolated data, and readable failure diagnosis.

## Acceptance criteria

- [x] Every LT-0118 exception in `docs/source-size-and-complexity.md` passes its
      listed limits.
- [x] Remove every LT-0118 XPath exception from
      `config/pmd/ruleset-test.xml`.
- [x] Preserve test intent and coverage; do not replace end-to-end assertions
      with implementation-only checks.
- [x] `./gradlew.bat qualityGate` passes without widening a threshold.

## Implementation notes

Web server tests are grouped into core, rendering, and route suites; long scenarios use named phases; and the SQLite fixture separates query, schema/serialization, and write concerns.

The matching production or test PMD ruleset now passes without suppression
clauses, and the LT-0113 thresholds were not widened.
