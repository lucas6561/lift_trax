---
id: LT-0116
title: Decompose persistence and integrity hotspots
status: ready
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

- [ ] Every LT-0116 exception in `docs/source-size-and-complexity.md` passes its
      listed limits.
- [ ] Remove every LT-0116 XPath exception from `config/pmd/ruleset.xml`.
- [ ] Preserve account isolation, migration, backup, and recovery coverage.
- [ ] `./gradlew.bat qualityGate` passes without widening a threshold.
