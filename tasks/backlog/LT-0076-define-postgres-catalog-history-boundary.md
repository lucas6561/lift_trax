---
id: LT-0076
title: Define the Postgres catalog and history boundary
status: ready
track: data
priority: high
effort: small
created: 2026-06-07
updated: 2026-10-27
owner: unassigned
depends_on: [LT-0092]
---

# LT-0076: Define the Postgres catalog and history boundary

## Why

Exercise definitions and completed training have different lifecycles even
though both now belong in the sole Postgres runtime. Catalog entries can be
renamed, disabled, enriched, shared, or removed; completed history must continue
to describe what the lifter actually performed.

The old plan to put these records in separate SQLite databases is obsolete.
The remaining decision is a logical ownership and lifecycle boundary inside
Postgres.

## Outcome

The repository documents which fields are live catalog metadata, which fields
are immutable execution snapshots, and how catalog changes, exports, imports,
and recovery preserve account-owned history.

## Scope

- In scope: Postgres table roles, ownership keys, stable identities, execution
  snapshot fields, rename/disable/delete behavior, provenance, query rules,
  migration order, export behavior, and recovery implications.
- Out of scope: a second runtime database, cross-database foreign keys, adding
  the snapshot columns, implementing catalog imports, or changing workout JSON
  schemas.

## Acceptance criteria

- [ ] An ADR or architecture note states that catalog and history remain in one
      Postgres database but have separate lifecycle rules.
- [ ] The decision names the execution-side snapshot fields required to render
      and filter old history without a mutable catalog join.
- [ ] The decision defines how live catalog IDs, portable source identities,
      and copied/imported catalog provenance differ.
- [ ] Rename, reclassification, disable, and delete behavior is explicit for
      both live catalog views and existing executions.
- [ ] Account export and operator recovery implications link to
      `docs/account-data-export-and-recovery.md`.
- [ ] Follow-up implementation tasks `LT-0077` through `LT-0080` are linked in
      their intended order.

## Notes

`LT-0092` made Postgres the sole runtime store. Do not reintroduce an exercise
catalog database file or a SQLite runtime mode.

`docs/hosted-user-data-schema.md` already sketches performed-exercise snapshot
fields, but the implemented migration still joins executions to mutable catalog
rows. This task should reconcile the design document with the implemented schema.
