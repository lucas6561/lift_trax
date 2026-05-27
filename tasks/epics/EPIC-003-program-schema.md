# EPIC-003: Program schema and wave building

## Goal

Create a schema-driven workout planning system that can generate training waves from program files, coach-authored templates, AI-authored files, and specialized generators.

## Why

Hard-coded program builders are useful, but they do not scale well to coach customization or generated programs. A program schema gives the app a stable contract for creating and validating training plans.

## Success criteria

- Program schema v1 can represent current conjugate and hypertrophy behavior.
- Schema files are validated with actionable errors.
- A generic builder can generate waves from the schema.
- Existing builders can either emit schema or use the generic path.
- A future drag-and-drop editor has a documented model to edit.

## Related tasks

- `LT-0003`: Design the workout plan model.
- `LT-0018`: Define the program schema v1.
- `LT-0019`: Build a schema validation layer.
- `LT-0020`: Create a generic schema-to-wave builder.
- `LT-0021`: Convert existing conjugate and hypertrophy builders to schema output.
- `LT-0022`: Design the coach program editor.
