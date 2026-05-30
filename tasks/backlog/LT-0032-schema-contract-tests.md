---
id: LT-0032
title: Add schema contract tests
status: idea
track: quality
priority: high
effort: medium
created: 2026-05-30
updated: 2026-05-30
owner: unassigned
depends_on: [LT-0018, LT-0023]
---

# LT-0032: Add schema contract tests

## Why

Program and workout JSON schemas are shared contracts. A small accidental schema change could break generated programs, previews, imports, or future coach tooling.

## Outcome

Schema contract tests make sure valid examples keep passing, invalid examples keep failing for the expected reasons, and schema versions remain explicit.

## Scope

- In scope: program schema tests, workout schema tests, valid fixtures, invalid fixtures, version checks, and clear failure messages.
- Out of scope: changing schema semantics unless a bug is uncovered and handled by a separate task.

## Acceptance criteria

- [ ] Every shared schema has at least one valid fixture and one invalid fixture covered by tests.
- [ ] Tests assert that unsupported schema versions fail clearly.
- [ ] Tests protect required fields and important enum values.
- [ ] Fixture file names and purposes are documented near the schema assets.
- [ ] `qualityGate` passes.

## Notes

This should build on the existing program schema validation and workout file format work without merging the two contracts.
