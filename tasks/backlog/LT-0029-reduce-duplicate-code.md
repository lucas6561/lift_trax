---
id: LT-0029
title: Reduce duplicate code
status: ready
track: quality
priority: medium
effort: medium
created: 2026-05-30
updated: 2026-05-30
owner: unassigned
depends_on: [LT-0012, LT-0013, LT-0028]
---

# LT-0029: Reduce duplicate code

## Why

Repeated implementation patterns make the app harder to change safely because fixes, formatting decisions, and behavior updates have to be copied across multiple places.

## Outcome

The project has less repeated production code in the most visible hotspots, with shared behavior extracted behind clear package boundaries and covered by focused regression tests.

## Scope

- In scope: identifying duplicated production-code patterns, prioritizing the highest-value duplicate cluster, extracting shared helpers or small abstractions that match existing package boundaries, and updating tests around the changed behavior.
- Out of scope: broad architecture rewrites, cosmetic-only refactors, feature behavior changes, and introducing a new duplicate-code scanner unless the current refactor clearly needs one.

## Acceptance criteria

- [ ] Duplicate-code hotspots are identified and the selected refactor target is noted in this task.
- [ ] At least one meaningful duplicate production-code cluster is consolidated without changing user-visible behavior.
- [ ] Refactored behavior is covered by focused tests or existing tests are extended to guard the shared path.
- [ ] The extracted code follows the package-boundary guidance from `LT-0013`.
- [ ] `qualityGate` passes after the refactor.

## Notes

Prioritize duplication that makes future feature work riskier, especially repeated CLI, rendering, serialization, validation, or file-format handling logic.
