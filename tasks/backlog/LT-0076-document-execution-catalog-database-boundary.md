---
id: LT-0076
title: Document execution and catalog database boundary
status: ready
track: data
priority: high
effort: small
created: 2026-06-07
updated: 2026-06-07
owner: unassigned
depends_on: [LT-0005, LT-0010]
---

# LT-0076: Document execution and catalog database boundary

## Why

Execution history and exercise catalog data have different lifecycles. Completed training should behave like a durable history log, while exercises and future equipment metadata should be editable reference data that can be seeded, expanded, and shared without rewriting old executions.

## Outcome

The repo has a short architecture note that defines which data belongs in the execution database, which data belongs in the exercise/equipment catalog database, and how the two stores refer to each other without making old history fragile.

## Scope

- In scope: database boundary, naming conventions, file-location expectations, migration implications, cross-database identity strategy, backup/restore impact, and interaction with future equipment metadata.
- Out of scope: implementing the split, adding new equipment behavior, changing the program/workout JSON schemas, or building a full exercise-library editor.

## Acceptance criteria

- [ ] An architecture note or ADR explains why execution history is separate from exercise/equipment catalog data.
- [ ] The decision names the intended database files or roles, such as execution history versus exercise catalog, without relying on ambiguous "main database" wording.
- [ ] The decision defines how execution rows preserve the performed exercise when a catalog exercise is renamed, disabled, deleted, or enriched later.
- [ ] The decision explains whether cross-database foreign keys are avoided and what stable identifiers or snapshots replace them.
- [ ] Follow-up implementation tasks are linked from the decision note.
- [ ] Documentation or task notes mention the backup/restore and starter-catalog implications.

## Notes

Today `lift_records` points at `lifts.id` inside the same SQLite database. Splitting the files makes sense, but the execution side should not depend on a live catalog row just to render old training history.

Coordinate with `LT-0030` for equipment metadata and `LT-0072` for starter exercises. The catalog database should become the natural home for both, but this task is only the boundary decision.
