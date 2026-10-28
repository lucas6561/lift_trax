---
id: LT-0093
title: Import exercise catalog files
status: done
track: data
priority: medium
effort: medium
created: 2026-06-11
updated: 2026-10-27
owner: unassigned
depends_on: [LT-0078]
---

# LT-0093: Import exercise catalog files

## Why

Coaches, lifters, and generated program workflows need a portable way to share
reusable exercise definitions without mixing catalog updates into program or
planned-workout import. Exercise imports should be an explicit database
operation so users can decide when shared exercise metadata becomes part of
their account-owned Postgres catalog.

## Outcome

LiftTrax supports a versioned exercise-catalog file format and an import flow
that adds or updates exercise definitions in the signed-in account's Postgres
catalog. Program and planned-workout imports can still reference unknown
exercises, but they do not automatically mutate the exercise catalog; unknown
exercises simply have no local history or catalog metadata until the user
imports or creates them.

## Scope

- In scope: exercise-catalog JSON schema, shared schema assets and examples,
  schema validation with actionable errors, import preview, duplicate or
  existing-exercise handling, explicit save to the account-owned Postgres
  catalog, tests, and documentation.
- Out of scope: bundling exercise definitions inside program or planned-workout imports, importing public third-party exercise databases wholesale, marketplace discovery, automatic exercise recommendations, or changing execution history when catalog metadata changes.

## Acceptance criteria

- [x] A versioned exercise-catalog schema defines the portable fields LiftTrax accepts for imported exercises, including at least name, enabled status, region, main lift type, muscles, notes, and any equipment metadata available by implementation time.
- [x] Shared schema assets include a stable latest entrypoint, a representative example file, and tests that ensure the latest alias matches the newest numbered schema.
- [x] Users can import an exercise-catalog file through an explicit flow that
      validates the file, previews the exercises to add or update, and requires
      confirmation before writing to their account-owned catalog.
- [x] Existing exercises are handled deterministically by a documented matching rule, with clear preview labels for new, unchanged, changed, skipped, or conflicting rows.
- [x] Program and planned-workout import behavior remains separate: importing those files never creates or overwrites exercise catalog rows, and unknown exercise references remain loadable without local history or metadata.
- [x] Import errors distinguish malformed JSON, unsupported schema version, schema validation failures, duplicate exercise definitions, and database-write failures.
- [x] Tests cover valid imports, unsupported versions, invalid examples, duplicate names in one file, updates to existing exercises, skipped unchanged exercises, and planned-workout/program imports that reference unknown exercises without mutating the catalog.
- [x] Documentation explains the exercise import workflow, the schema location, and when users should import exercises before importing or following a workout.
- [x] `qualityGate` passes.

## Notes

This task intentionally keeps exercise catalog import separate from program and planned-workout import. A workout can still be loaded when its exercise name is not in the local catalog; it just cannot show catalog-backed metadata or local history for that exercise until the user imports or creates it.

Implement after `LT-0078` defines portable identity and provenance so imports
merge deterministically into the existing Postgres catalog. Do not create a
second catalog database or an SQLite runtime path.

## Completion - 2026-10-27

Added the standalone exercise-catalog v1 schema, byte-identical latest alias,
and representative example. The explicit Add Execution import flow accepts a
file or pasted JSON, validates it, previews new/unchanged/changed/skipped/conflict
rows, and requires confirmed save. Confirmation checks the current catalog
fingerprint and writes all changes in a transaction; stale previews, conflicts,
and database failures retain existing data.

Portable source identity matches renamed rows. Name-only differences never
overwrite silently, ambiguous names and duplicate definitions are actionable
errors, unchanged rows are skipped, and source updates preserve acquisition
provenance. Program/workout imports remain separate and unknown references stay
loadable without creating catalog rows. Errors distinguish JSON syntax, schema
version, schema validation, duplicates, conflicts/stale preview, and database
failure. Parser, planner, datastore, HTTP flow, rollback, schema-alias, and
unknown-reference regression tests pass.

See [the import guide](../../docs/exercise-catalog-import.md) and
`shared/exercise-catalog/`.

Validation: `./gradlew.bat qualityGate` passed with 337 tests, zero failures or
skips, and 92.34% instruction coverage. The run included the real Postgres
backfill and recovery tests against disposable loopback databases.
