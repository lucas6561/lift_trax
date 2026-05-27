---
id: LT-0003
title: Design the workout plan model
status: idea
track: training-logic
priority: high
effort: large
created: 2026-05-27
updated: 2026-05-27
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

- [ ] The plan model distinguishes templates, generated workouts, and completed executions.
- [ ] The model can represent current conjugate wave behavior.
- [ ] Persistence impact is documented before schema changes.
- [ ] Follow-up implementation tasks are created.

## Notes

This should be designed before adding many planner-specific tables.

