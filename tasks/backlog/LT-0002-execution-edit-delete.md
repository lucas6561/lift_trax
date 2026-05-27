---
id: LT-0002
title: Add execution editing and deletion
status: idea
track: product
priority: high
effort: medium
created: 2026-05-27
updated: 2026-05-27
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

- [ ] Existing execution records can be edited from the web UI.
- [ ] Existing execution records can be deleted with a confirmation step.
- [ ] Database methods cover update and delete behavior.
- [ ] Route and renderer tests cover success and failure paths.

## Notes

Deletion should account for `execution_sets` rows linked by `record_id`.

