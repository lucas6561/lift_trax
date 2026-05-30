---
id: LT-0029
title: Reduce duplicate code
status: done
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

- [x] Duplicate-code hotspots are identified and the selected refactor target is noted in this task.
- [x] At least one meaningful duplicate production-code cluster is consolidated without changing user-visible behavior.
- [x] Refactored behavior is covered by focused tests or existing tests are extended to guard the shared path.
- [x] The extracted code follows the package-boundary guidance from `LT-0013`.
- [x] `qualityGate` passes after the refactor.

## Notes

Identified duplicate-code hotspots:

- Repeated lift filter/data-attribute rendering in `WebUiRenderer` list rows and select options.
- Repeated lift-name fallback lookup in web-configured workout selectors.
- Repeated `SetMetric` to execution-edit form field mapping in `WebUiRenderer`.

Selected refactor target: the repeated execution-edit form mapping, because the same production logic drove both visible edit controls and the hidden initial JSON payload used by browser-side execution editing.

Completed by extracting package-local CLI helper `ExecutionSetFormValues` and using it from both execution-set edit row rendering and initial edit JSON serialization.

Added `WebUiRendererTest.executionRowsUseSharedMetricFormValuesForEditorRowsAndInitialPayload` to guard reps-left/right, time, distance, weight, and RPE values through the shared path.

Verified on 2026-05-30:

- `.\gradlew.bat test --tests com.lifttrax.cli.WebUiRendererTest`
- `.\gradlew.bat qualityGate`
