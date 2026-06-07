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
- `LT-0076`: Document execution and catalog database boundary.
- `LT-0077`: Add exercise snapshots to execution history.
- `LT-0078`: Add exercise catalog database foundation.
- `LT-0079`: Move exercise catalog to catalog database.

## Notes

This milestone is mostly design and thin validation. It should create enough clarity to implement the generic builder without guessing.
