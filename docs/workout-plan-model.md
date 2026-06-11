# Workout Plan Model

LiftTrax separates workout planning into three durable concepts:

1. Program templates describe reusable training intent.
2. Generated planned workouts describe the concrete work for a dateable wave.
3. Completed executions record what the lifter actually did.

This split lets the app regenerate or revise future training without rewriting
old completed history.

## Model Layers

| Layer | Purpose | Current artifact | Persistence role |
| --- | --- | --- | --- |
| Program template | Reusable authoring source for a training style, coach plan, or AI-generated program | `shared/programs/schema/program.schema.latest.json` and example program files | Stored as an authored source file or future program record. It should not be required to display an already-generated workout. |
| Generated plan | Concrete planned weeks, days, blocks, exercises, and set targets | `PlannedWorkoutFile` and `shared/workouts/schema/workout.schema.latest.json` | Stored as the snapshot the athlete follows. It can reference its source template, but it must be displayable without rerunning the generator. |
| Workout session | One athlete performing one generated day | Follow-along session routes and saved execution inputs | Stores session state, skips, substitutions, notes, and completed sets. Draft state can be temporary; saved results become completed execution history. |
| Completed execution | Durable training history for a performed exercise | `LiftExecution` and `ExecutionSet` | Stored as history. It should preserve performed values and enough exercise context to remain readable after catalog or plan changes. |

## Domain Objects

### Program Template

A program template answers "what kind of training should be generated?" It
contains metadata, weeks, day templates, ordered blocks, exercise pools,
substitution rules, and progression rules.

Templates are editable authoring assets. They can be coach-authored, AI-authored,
or produced by a specialized generator. A template can be corrected and used to
generate a new wave, but editing it must not silently mutate a generated plan
that the athlete already followed.

### Generated Planned Workout

A generated planned workout answers "what should I train in this wave?" It is a
snapshot of weeks, days, blocks, exercises, planned set targets, notes, and
substitution options.

Generated plans keep source metadata:

- `source.kind`: how the plan was created, such as wave generation, schema
  generation, manual authoring, or import.
- `source.generator`: the generator or tool that produced it.
- `source.programName`: the template or preset name.
- `source.programSchemaVersion`: the source schema version when applicable.
- `source.generatedAt`: when this plan snapshot was created.

A generated plan should be immutable once used for training. Corrections should
create a new plan revision or a new generated file, while completed history keeps
pointing at the plan version the athlete actually saw.

### Workout Session

A workout session answers "what happened while following this planned day?" It
starts from one planned week and day, then records:

- the selected planned block and exercise order
- blank athlete-entered result fields before the lifter logs work
- completed set values such as reps, time, distance, weight, and RPE
- skipped planned sets or exercises
- substitutions selected from the planned workout or local catalog
- session notes and exercise notes
- draft versus saved state

The session layer is intentionally separate from the program template. The
athlete should be able to train from an imported planned workout file even if
the source template is unavailable.

### Completed Execution

A completed execution answers "what did I actually do for this exercise?" Today
the execution model stores a date, one or more sets, warmup and deload flags, and
notes:

- `LiftExecution`
- `ExecutionSet`
- `SetMetric`

Completed executions are the durable history used by dashboards, lift detail
pages, recent-history summaries, and future planned-versus-completed comparison.
They should not depend on live planned-workout data just to render old training.

## Lifecycle

```text
program template
  -> validated program schema
  -> generated planned workout
  -> workout session
  -> completed executions
  -> comparison and future planning input
```

The lifecycle is append-friendly. The app can create new generated plans from a
template and can create new completed executions from a session, but older
completed executions remain stable.

## Current Conjugate Mapping

The current conjugate builder can fit this model:

- The conjugate program template is the training style: lower and upper max
  effort days, lower and upper dynamic days, accessories, conditioning, warmups,
  forearm finishers, max-effort rotation, dynamic effort loading waves, and
  deload rules.
- `MaxEffortPlan` is a generated selection inside the plan: it chooses lower and
  upper max-effort lifts plus derived deload pairings.
- `ConjugateWorkoutBuilder.getWave(...)` produces generated planned weeks as
  `List<Map<DayOfWeek, Workout>>`.
- `Workout` contains ordered `WorkoutLift` blocks. A block can hold a single
  exercise target or a circuit.
- `SingleLift` captures the planned exercise, metric, percent target, RPE
  target, accommodating resistance, and deload flag.
- `CircuitLift` captures grouped accessory work, rounds, and warmup status.
- `PlannedWorkoutExporter` converts the transient wave into the versioned
  planned-workout file format.
- Follow-along logging records completed work separately from the planned set
  targets.

The important boundary is that `Workout` and `SingleLift` are generation-time
objects. The versioned `PlannedWorkoutFile` is the durable output contract for
loading, previewing, printing, following, importing, and comparing planned work.

## Planned Versus Completed Links

Planned and completed data should link through stable identifiers rather than
through mutable display text alone.

Suggested identifiers for future persistence:

- `plan_id`: generated plan snapshot
- `plan_revision`: immutable version of the generated plan
- `planned_week_number`: source week within the generated plan
- `planned_day_key`: source day within the generated week
- `planned_block_id`: stable block within the day
- `planned_exercise_id`: stable planned exercise occurrence
- `planned_set_id`: stable planned set occurrence
- `session_id`: one athlete's attempt at one planned day
- `execution_id`: durable completed execution row

When a completed set comes from a plan, store both the planned target reference
and the actual result. When the athlete logs extra work, allow completed results
without a planned set reference. When the athlete skips work, store the skipped
planned reference without inventing a completed set.

## Persistence Impact

No schema change is required just to document this model. Future schema changes
should be staged in this order:

1. Keep program templates and planned workout files as versioned JSON contracts.
2. Add database tables only when the app needs durable local plan/session state.
3. Store generated plan snapshots separately from completed execution history.
4. Link completed sessions to planned workout snapshots without requiring the
   original generator to be present.
5. Preserve completed execution rendering even when a planned workout file,
   program template, or catalog exercise later changes.
6. Coordinate plan/session tables with the user ownership model before hosted or
   multi-user data is introduced.
7. Use migrations for any database-backed plan, session, or execution-reference
   changes.

The first durable persistence task should not replace `LiftExecution`. It should
add plan/session references around the existing execution history path, then
backfill or enrich history only where needed for comparison and stable display.

## Follow-Up Work

Existing implementation tasks cover the next slices:

- `LT-0020`: Create a generic schema-to-wave builder.
- `LT-0021`: Convert existing builders to schema output.
- `LT-0022`: Design the coach program editor.
- `LT-0027`: Persist planned versus completed workout data.
- `LT-0064`: Add active workout draft and resume.
- `LT-0076`: Document execution and catalog database boundary.
- `LT-0077`: Add exercise snapshots to execution history.

These tasks should use this model as the naming boundary: templates are authored
inputs, generated planned workouts are immutable followable outputs, sessions
are in-progress attempts, and executions are completed history.
