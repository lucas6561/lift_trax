---
id: LT-0078
title: Add exercise catalog database foundation
status: idea
track: data
priority: high
effort: medium
created: 2026-06-07
updated: 2026-06-07
owner: unassigned
depends_on: [LT-0076, LT-0005]
---

# LT-0078: Add exercise catalog database foundation

## Why

Exercises and future equipment metadata are catalog data, not execution history. Giving them their own database foundation will make starter catalogs, equipment requirements, and future catalog sharing cleaner than continuing to grow the execution database.

## Outcome

LiftTrax can open and migrate a separate exercise catalog database alongside the execution database, with schema and tests ready for exercise records and future equipment metadata.

## Scope

- In scope: catalog database path rules, catalog migration assets, exercise table shape, future equipment table or extension point, database-opening code, validation, tests, and docs.
- Out of scope: moving the live UI to the catalog database, migrating existing user lifts, implementing equipment conflict logic, or changing backup/restore behavior beyond noting the new database.

## Acceptance criteria

- [ ] The app has a clear way to locate the execution database and exercise catalog database for local development and normal startup.
- [ ] Catalog migrations can run independently from execution-history migrations and report their active schema version.
- [ ] The catalog schema can represent the current lift fields: name, region, main lift type, muscles, notes, and enabled status.
- [ ] The catalog schema leaves an explicit path for future equipment metadata from `LT-0030`.
- [ ] Tests cover creating a fresh catalog database, re-opening an already migrated catalog database, and detecting an unsupported catalog schema version.
- [ ] Developer documentation explains how to add catalog migrations separately from execution migrations.
- [ ] `qualityGate` passes.

## Notes

Keep this as infrastructure only. The user-facing cutover belongs in `LT-0079`, and the backup/restore updates belong in `LT-0080`.
