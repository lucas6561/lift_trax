---
id: LT-0049
title: Add confirmation and undo flows
status: idea
track: interface
priority: high
effort: medium
created: 2026-05-30
updated: 2026-05-30
owner: unassigned
depends_on: [LT-0002]
---

# LT-0049: Add confirmation and undo flows

## Why

Deleting or overwriting training data should be hard to do by accident. Users need confidence that mistakes can be corrected quickly.

## Outcome

Destructive actions use clear confirmations or undo behavior, depending on the action and risk.

## Scope

- In scope: delete confirmations, undo for recent low-risk changes where practical, clear success messages, and route tests.
- Out of scope: full audit history, multi-user conflict resolution, or restoring arbitrary historical versions.

## Acceptance criteria

- [ ] High-risk destructive actions require confirmation.
- [ ] Lower-risk recent changes support undo where the data model makes it practical.
- [ ] Success and undo messages identify what changed.
- [ ] Tests cover confirmed delete, canceled delete, and undo behavior where implemented.
- [ ] `qualityGate` passes.

## Notes

Use the least annoying protection that still prevents costly mistakes.
