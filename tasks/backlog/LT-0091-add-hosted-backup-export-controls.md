---
id: LT-0091
title: Add hosted backup and export controls
status: ready
track: data
priority: high
effort: medium
created: 2026-06-08
updated: 2026-06-26
owner: unassigned
depends_on: [LT-0086, LT-0087, LT-0090]
---

# LT-0091: Add hosted backup and export controls

## Why

Hosted data is still user-owned training history. Users need a way to leave,
recover, and audit their data instead of trusting the hosted database blindly.

## Outcome

The hosted app has documented backup expectations and a user-facing export path
for account-owned training data.

## Scope

- In scope: provider backup expectations, manual export, account-scoped export
  validation, restore or support-runbook expectations, and documentation.
- Out of scope: continuous local/cloud sync and legal compliance automation.

## Acceptance criteria

- [ ] Users can export their hosted training data in a documented format.
- [ ] Exports include lifts, executions, planned workouts, and relevant program
      or catalog data owned by the account.
- [ ] Export cannot include another user's private data.
- [ ] Provider backup and restore expectations are documented for operators.
- [ ] Tests cover account-scoped export isolation and malformed export requests.

## Notes

This is the hosted counterpart to the existing local backup and restore story.

2026-06-26 readiness note:

- `LT-0090` now provides the hosted pipeline and smoke-check shape.
- `LT-0088` now records import provenance and user-scoped hosted rows, which
  gives export isolation tests a concrete hosted data surface.
- The next implementation should start with account-scoped export JSON for the
  hosted adapter, then document provider backup/restore operations separately
  from user-initiated exports.
