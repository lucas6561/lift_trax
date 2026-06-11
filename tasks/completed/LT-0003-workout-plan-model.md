---
id: LT-0003
title: Design the workout plan model
status: done
track: training-logic
priority: high
effort: large
created: 2026-05-27
updated: 2026-06-11
owner: unassigned
depends_on: []
---

# LT-0003: Design the workout plan model

## Why

The project has workout builders and wave generation, but it needs a durable concept of a plan that can be saved, reviewed, regenerated, and compared to actual execution.

## Outcome

A documented domain model describes plans, planned workouts, planned lifts, progression rules, and how actual logged sets relate back to the plan.

## Scope

- In scope: model design, persistence sketch, naming, and starter tests if code is added.
- Out of scope: full planner UI and all programming styles.

## Acceptance criteria

- [x] The plan model distinguishes templates, generated workouts, and completed executions.
- [x] The model can represent current conjugate wave behavior.
- [x] Persistence impact is documented before schema changes.
- [x] Follow-up implementation tasks are created.

## Notes

Completed with `docs/workout-plan-model.md`.

The design distinguishes program templates, generated planned workouts, workout
sessions, and completed executions. It maps the current conjugate builder from
`MaxEffortPlan`, `Workout`, `SingleLift`, and `CircuitLift` into the durable
`PlannedWorkoutFile` contract, then defines how future planned-versus-completed
links should point from completed sessions and sets back to immutable planned
targets.

Persistence impact is documented as a staged approach: keep versioned JSON
contracts first, add plan/session tables only when durable local state is
needed, link sessions to generated plan snapshots, preserve completed history
independently from mutable plans or catalog exercises, and coordinate future
tables with migrations and user ownership.

Follow-up work is already tracked by `LT-0020`, `LT-0021`, `LT-0022`,
`LT-0027`, `LT-0064`, `LT-0076`, and `LT-0077`.

Verification:

- Checked task schema and completed-card placement.
- Checked links to existing follow-up cards.
- No code changed, so no build gate was run.
