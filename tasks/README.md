# LiftTrax task system

This folder is the planning home for LiftTrax. It keeps the roadmap close to the code without turning every idea into an immediate implementation promise.

## Folder layout

```text
tasks/
  README.md
  schema.md
  roadmap.md
  backlog/
    LT-0001-daily-training-dashboard.md
```

## How to use it

1. Start with `roadmap.md` when deciding what matters next.
2. Create one markdown file in `backlog/` for each concrete task.
3. Use `schema.md` as the shape for every task file.
4. Keep tasks small enough that "done" is testable.
5. Move status forward in the task file as work becomes clearer.

## Task ID format

Use `LT-0001`, `LT-0002`, and so on. The ID should stay stable even if the task title changes.

## Status values

- `idea`: worth keeping, not ready to build.
- `ready`: clear enough to pick up.
- `doing`: currently being worked.
- `blocked`: cannot move without a decision or dependency.
- `done`: implemented and verified.
- `dropped`: intentionally abandoned.
