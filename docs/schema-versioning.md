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

## Numbered Snapshots and Latest Entrypoints

Numbered schema files are frozen compatibility snapshots. For example,
`shared/workouts/schema/workout.schema.v2.json` describes planned-workout
version `2` forever, even after a newer version ships. Do not use numbered
schema paths in public AI prompts or long-lived external instructions unless
the instruction intentionally targets that exact version.

Stable latest entrypoints are copies of the currently latest numbered snapshot:

- `shared/programs/schema/program.schema.latest.json`
- `shared/workouts/schema/workout.schema.latest.json`

Use these stable latest paths for public guidance, including raw GitHub links.
Guard tests compare each latest entrypoint with the latest version registered in
`ProgramSchemaVersions` and `PlannedWorkoutSchemaVersions`, so advancing the
catalog without refreshing the latest files fails loudly.

## Rules

1. Never edit a published schema snapshot after it ships.
2. Add a new numbered schema file for a contract change.
3. Keep readers or validators registered for every supported older version.
4. Make creation and export paths use the latest registered version.
5. Add fixtures that prove older versions still load and new files use latest.
6. Refresh the matching `*.latest.json` entrypoint when a new latest version is
   registered.

Program schema snapshots live under `shared/programs/schema/`. Planned-workout
snapshots live under `shared/workouts/schema/`.

In Java, `ProgramSchemaVersions` and `PlannedWorkoutSchemaVersions` are the
authoritative catalogs. `ProgramSchemaValidator` dispatches validation by
program schema version. `PlannedWorkoutJson` dispatches workout imports by
planned-workout schema version, and `PlannedWorkoutExporter` always writes the
latest version.

## Updating Latest During a Release

When a schema version advances:

1. Add the new numbered schema snapshot.
2. Register the new version in the appropriate schema catalog.
3. Copy the new numbered schema payload to the matching `*.latest.json` file.
4. Update this document's current-version table.
5. Run `./gradlew.bat qualityGate` before publishing links or release notes.
