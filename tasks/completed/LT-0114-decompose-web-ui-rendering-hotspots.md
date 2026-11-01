---
id: LT-0114
title: Decompose web UI rendering hotspots
status: complete
track: quality
priority: high
effort: large
created: 2026-11-01
updated: 2026-11-01
owner: unassigned
depends_on: [LT-0113]
---

# LT-0114: Decompose web UI rendering hotspots

## Why

The LT-0113 baseline found oversized or complex rendering responsibilities in
`WebUiRenderer`, `PlannedWorkoutSessionHtml`, `ExecutionInputWidgetHtml`,
`LiftDetailResponse`, and `WebRouteRegistry`. Exact temporary exceptions keep
those hotspots visible but should not become permanent architecture.

## Outcome

Page, widget, script, and route-registration rendering responsibilities are
split along product boundaries without changing HTML behavior, deep links,
progressive enhancement, keyboard behavior, or offline drafts.

## Acceptance criteria

- [x] Every LT-0114 production exception in
      `docs/source-size-and-complexity.md` passes its physical, NCSS,
      cyclomatic, and NPath limits as applicable.
- [x] Remove every LT-0114 XPath exception from `config/pmd/ruleset.xml`.
- [x] Preserve focused renderer tests and desktop/mobile browser behavior.
- [x] `./gradlew.bat qualityGate` passes without widening a threshold.

## Implementation notes

Rendering, embedded browser templates, route registration, lift details, and task-specific page sections now have focused collaborators while preserving existing HTML contracts.

The matching production or test PMD ruleset now passes without suppression
clauses, and the LT-0113 thresholds were not widened.
