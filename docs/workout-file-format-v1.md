# Workout File Format v1

LiftTrax workout format v1 is the JSON contract for generated, planned training
sessions. A workout file is the thing a lifter can import and view without
rerunning the original wave generator or having the original program schema.

This snapshot remains supported for older files. New exports use the latest
snapshot documented in `docs/schema-versioning.md`.

## Files

- Schema: `shared/workouts/schema/workout.schema.v1.json`
- Example: `shared/workouts/examples/conjugate-wave-v1.json`

## Top-Level Shape

- `schemaVersion`: Must be `1`.
- `metadata`: Human-readable name, description, total week count, and tags.
- `source`: Where the file came from, such as wave generation, program-schema
  generation, manual authoring, or import.
- `weeks`: Ordered planned training weeks.
- `completedWorkouts`: Optional completed results. New generated files use an
  empty array so planned targets and performed work stay separate.

## Planned Work

Each week contains ordered days. Each day contains ordered blocks, such as warm
up, max effort, dynamic effort, accessory, conditioning, circuit, or single
work. Blocks contain exercises, and exercises contain planned set targets.

Set targets can represent:

- reps
- left/right reps
- rep ranges
- timed work
- distance work
- percent targets
- RPE targets
- accommodating resistance
- deload flags

## Import Boundary

The file should be loadable on its own. It stores exercise names, regions, lift
types, muscles, notes, planned sets, and substitution options directly in the
planned workout file. Future app code may reconcile those names with the local
database, but display does not require the original generator.

The web UI loads workout files from the Import Workout tab. The browser file
picker reads the selected JSON file locally and sends its contents to the
preview route. When imported exercise names match local lifts, the preview also
shows the same local history context used by generated waves: `Last:` and `Best
1RM:`.

From the preview, `Start This Day` opens the follow-along logging screen for one
week and day. Planned set counts seed editable result fields for reps,
left/right reps, time, and distance. Completed sets can add weights and RPE,
planned work can be skipped, and exercises can use substitution options already
declared in the workout file when those lifts exist locally. Saved work enters
the regular execution history. Durable planned-versus-completed storage is a
separate persistence concern.
