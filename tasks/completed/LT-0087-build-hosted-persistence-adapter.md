---
id: LT-0087
title: Build hosted persistence adapter
status: done
track: data
priority: high
effort: large
created: 2026-06-08
updated: 2026-06-16
owner: codex
depends_on: [LT-0083, LT-0086]
---

# LT-0087: Build hosted persistence adapter

## Why

The app needs a way to use the selected hosted database without deleting the
local SQLite development path all at once.

## Outcome

LiftTrax can run core web workflows against the hosted data store through a
clear persistence boundary.

## Scope

- In scope: repository or adapter boundaries, hosted connection configuration,
  local-development configuration, hosted table creation for the first slice,
  core lift and execution reads/writes, and tests against a realistic
  hosted-store substitute.
- Out of scope: importing existing local databases and replacing every CLI path.

## Acceptance criteria

- [x] The persistence boundary separates local SQLite access from hosted-store
      access.
- [x] Core lift listing, execution logging, history reads, and lift detail reads
      work through the hosted adapter.
- [x] Configuration keeps secrets out of source-controlled files.
- [x] Tests cover successful hosted reads/writes and missing or invalid
      connection configuration.
- [x] Documentation explains how developers run the hosted persistence test path.

## Notes

`TrainingDataStoreProvider` selects SQLite by default and
`HostedPostgresTrainingDataStoreProvider` when `LIFTTRAX_DATA_STORE` is set to
`hosted-postgres`.

The hosted adapter creates and uses the first table boundary from
`docs/hosted-user-data-schema.md`: `app_users`, `lifter_profiles`,
`exercise_catalog_entries`, `executions`, and `execution_sets`.

The current Java web routes still use integer execution handles, so the hosted
adapter maps those to `executions.web_execution_id` while preserving UUID
execution IDs for the hosted schema.

Developer documentation is in `docs/hosted-persistence-adapter.md`.

Verification:

- `.\gradlew.bat test --tests com.lifttrax.db.HostedPostgresTrainingDataStoreTest`
- `.\gradlew.bat qualityGate`
