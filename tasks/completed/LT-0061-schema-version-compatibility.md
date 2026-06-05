---
id: LT-0061
title: Add schema version compatibility
status: done
track: training-logic
priority: high
effort: medium
created: 2026-06-05
updated: 2026-06-05
owner: unassigned
depends_on: [LT-0018, LT-0019, LT-0023]
---

# LT-0061: Add schema version compatibility

## Why

Program and workout files will evolve. Older files should remain loadable while new exports use the latest supported contract.

## Outcome

Program and planned-workout schema versions are cataloged, readers reject unsupported versions clearly, and writers emit the latest registered version.

## Scope

- In scope: version catalogs, v1/v2 schema assets, compatibility loading, latest-version exports, documentation, and tests.
- Out of scope: changing the meaning of program or workout fields beyond versioning support.

## Acceptance criteria

- [x] Program and planned-workout schemas have explicit supported-version catalogs.
- [x] Older supported versions continue to load and validate.
- [x] New planned-workout exports use the latest supported schema version.
- [x] Unsupported versions fail with useful supported-version information.
- [x] Documentation explains the compatibility policy.
- [x] `qualityGate` passes.

## Notes

Retrospective card for the schema-versioning work completed on 2026-05-31.
Implementation added `SchemaVersionCatalog`, `ProgramSchemaVersions`,
`PlannedWorkoutSchemaVersions`, v2 schema/example snapshots, latest-version
planned-workout exports, and `docs/schema-versioning.md`.

Verification:

- `.\gradlew.bat qualityGate`
