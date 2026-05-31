# JSON Schema Versioning

LiftTrax keeps published program and planned-workout JSON formats loadable after
new versions ship. New files should always use the latest schema snapshot.

## Current Versions

| Contract | Latest version | Supported versions |
| --- | --- | --- |
| Program schema | `2` | `1`, `2` |
| Planned workout format | `2` | `1`, `2` |

Version `2` intentionally keeps the version `1` payload shape. It establishes
the compatibility path before the next data-model change: loaders dispatch by
`schemaVersion`, older schema snapshots remain packaged, and creators use the
latest registered version automatically.

## Rules

1. Never edit a published schema snapshot after it ships.
2. Add a new numbered schema file for a contract change.
3. Keep readers or validators registered for every supported older version.
4. Make creation and export paths use the latest registered version.
5. Add fixtures that prove older versions still load and new files use latest.

Program schema snapshots live under `shared/programs/schema/`. Planned-workout
snapshots live under `shared/workouts/schema/`.

In Java, `ProgramSchemaVersions` and `PlannedWorkoutSchemaVersions` are the
authoritative catalogs. `ProgramSchemaValidator` dispatches validation by
program schema version. `PlannedWorkoutJson` dispatches workout imports by
planned-workout schema version, and `PlannedWorkoutExporter` always writes the
latest version.
