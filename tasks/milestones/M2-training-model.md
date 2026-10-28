# M2: Training model

## Goal

Define the contracts that separate program authoring, generated plans, workout output, and completed training data.

## Exit criteria

- The plan model distinguishes templates, generated workouts, and completed executions.
- Program schema v1 can represent the current builders.
- Workout file format v1 can be loaded by the app later.
- Persistence changes are planned before tables are added.

## Tasks

- `LT-0003`: Design the workout plan model.
- `LT-0018`: Define the program schema v1.
- `LT-0023`: Define the workout file format v1.
- `LT-0005`: Add schema migration tracking.
- `LT-0061`: Add schema version compatibility.
- `LT-0076`: Define the Postgres catalog and history boundary. (done)
- `LT-0077`: Add exercise snapshots to Postgres execution history. (done)
- `LT-0078`: Add portable identity and provenance to the exercise catalog. (done)
- `LT-0079`: Make catalog changes history-safe. (done)
- `LT-0080`: Drill catalog and history recovery. (done)
- `LT-0093`: Import exercise catalog files. (done)

## Notes

The plan/workout contracts, performed-history snapshots, catalog lifecycle and
provenance, explicit catalog import, and isolated recovery drill are delivered.
See [ADR-0005](../../docs/adr/0005-catalog-history-lifecycle.md) and the
[recorded recovery drill](../../docs/recovery-drill-2026-10-27.md).
