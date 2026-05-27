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

## Notes

This milestone is mostly design and thin validation. It should create enough clarity to implement the generic builder without guessing.
