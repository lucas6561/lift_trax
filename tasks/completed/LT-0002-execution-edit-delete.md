 ---
id: LT-0002
title: Add execution editing and deletion
status: done
track: product
priority: high
effort: medium
created: 2026-05-27
updated: 2026-06-11
owner: unassigned
depends_on: []
---

# LT-0002: Add execution editing and deletion

## Why

Workout logs need correction. A mistyped weight, date, RPE, or lift should not require manual database editing.

## Outcome

Users can edit and delete logged executions from the web UI, with database changes reflected immediately in lift history.

## Scope

- In scope: edit form, delete action, validation, database write paths, and tests.
- Out of scope: undo history and bulk editing.

## Acceptance criteria

- [x] Existing execution records can be edited from the web UI.
- [x] Existing execution records can be deleted with a confirmation step.
- [x] Database methods cover update and delete behavior.
- [x] Route and renderer tests cover success and failure paths.

## Notes

Deletion should account for `execution_sets` rows linked by `record_id`.

Completed with database coverage for execution update/delete persistence, route coverage for
update/delete success and error redirects, existing renderer coverage for edit/delete controls, and
full `qualityGate` verification.
