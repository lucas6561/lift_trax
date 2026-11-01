---
id: LT-0116
title: Decompose persistence and integrity hotspots
status: complete
track: quality
priority: high
effort: large
created: 2026-11-01
updated: 2026-11-01
owner: unassigned
depends_on: [LT-0113]
---

# LT-0116: Decompose persistence and integrity hotspots

## Why

The Postgres datastore, sharing, password matching, snapshot integrity, and
backup table specification contain LT-0113 baseline exceptions.

## Outcome

Persistence mapping, sharing, validation, and backup metadata are separated
without changing ownership checks, transaction behavior, snapshot contents, or
canonical Postgres data.

## Acceptance criteria

- [x] Every LT-0116 exception in `docs/source-size-and-complexity.md` passes its
      listed limits.
- [x] Remove every LT-0116 XPath exception from `config/pmd/ruleset.xml`.
- [x] Preserve account isolation, migration, backup, and recovery coverage.
- [x] `./gradlew.bat qualityGate` passes without widening a threshold.

## Implementation notes

Hosted JDBC mapping, execution batch reads, training queries, sharing, password matching, snapshot validation, and backup table metadata were decomposed without changing persistence semantics.

The matching production or test PMD ruleset now passes without suppression
clauses, and the LT-0113 thresholds were not widened.
