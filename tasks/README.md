# LiftTrax task system

This folder is the planning home for LiftTrax. It keeps the roadmap close to the code without turning every idea into an immediate implementation promise.

## Folder layout

```text
tasks/
  README.md
  schema.md
  roadmap.md
  epics/
    EPIC-001-quality-foundation.md
  milestones/
    M1-quality-baseline.md
  backlog/
    LT-0003-workout-plan-model.md
  completed/
    LT-0002-execution-edit-delete.md
```

## How to use it

1. Start with `roadmap.md` when deciding what matters next.
2. Use `epics/` for long-lived product goals that group many tasks.
3. Use `milestones/` for short sequencing plans that can actually finish.
4. Create one markdown file in `backlog/` for each concrete task.
5. Use `schema.md` as the shape for every task file.
6. Keep tasks small enough that "done" is testable.
7. Move status forward in the task file as work becomes clearer.
8. Move `done` task files from `backlog/` to `completed/`.

## Task ID format

Use `LT-0001`, `LT-0002`, and so on. The ID should stay stable even if the task title changes.

## Status values

- `idea`: worth keeping, not ready to build.
- `ready`: clear enough to pick up.
- `doing`: currently being worked.
- `blocked`: cannot move without a decision or dependency.
- `done`: implemented and verified.
- `dropped`: intentionally abandoned.
