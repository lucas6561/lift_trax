---
id: LT-0091
title: Add hosted backup and export controls
status: done
track: data
priority: high
effort: medium
created: 2026-06-08
updated: 2026-10-27
owner: codex
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

- [x] Users can export their hosted training data in a documented format.
- [x] Exports include lifts, executions, planned workouts, and relevant program
      or catalog data owned by the account.
- [x] Export cannot include another user's private data.
- [x] Provider backup and restore expectations are documented for operators.
- [x] Tests cover account-scoped export isolation and malformed export requests.

## Notes

This is the hosted counterpart to the existing local backup and restore story.

2026-06-26 readiness note:

- `LT-0090` now provides the hosted pipeline and smoke-check shape.
- `LT-0088` now records import provenance and user-scoped hosted rows, which
  gives export isolation tests a concrete hosted data surface.
- The next implementation should start with account-scoped export JSON for the
  hosted adapter, then document provider backup/restore operations separately
  from user-initiated exports.

2026-10-27 completion note:

- The authenticated Account page downloads a version 1 JSON export through the
  signed-in user's scoped `TrainingDataStore`; the route accepts no account
  selector and rejects unknown parameters or formats.
- The export contains all enabled and disabled catalog entries, chronological
  executions and ordered sets, and complete saved planned-workout documents.
  It excludes authentication IDs, email, credentials, tokens, provider data,
  and server-only import bookkeeping.
- Numbered/latest schemas live under `shared/account-export/schema/`.
  `docs/account-data-export-and-recovery.md` documents the user format,
  provider/off-provider backup expectations, single-account recovery, and
  whole-service recovery.
- The multi-user HTTP test proves cross-account isolation and malformed-request
  rejection. Focused tests, PMD, Spotless, the full test suite, 90 percent
  coverage verification, and `qualityGate` passed.
