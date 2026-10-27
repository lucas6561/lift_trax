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
- `LT-0076`: Define the Postgres catalog and history boundary.
- `LT-0077`: Add exercise snapshots to Postgres execution history.
- `LT-0078`: Add portable identity and provenance to the exercise catalog.
- `LT-0079`: Make catalog changes history-safe.
- `LT-0080`: Drill catalog and history recovery.
- `LT-0093`: Import exercise catalog files.

## Notes

The plan/workout contracts are delivered. Remaining work makes completed
history independent of mutable catalog metadata inside the single Postgres
runtime, then creates enough portable catalog identity to implement imports
without guessing.
