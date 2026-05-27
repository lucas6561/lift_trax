---
id: LT-0019
title: Build a schema validation layer
status: idea
track: training-logic
priority: high
effort: medium
created: 2026-05-27
updated: 2026-05-27
owner: unassigned
depends_on: [LT-0018]
---

# LT-0019: Build a schema validation layer

## Why

Coaches and AI-generated files need clear feedback when a program schema is invalid.

## Outcome

The app can load a program file, validate it, and report actionable errors without starting wave generation.

## Scope

- In scope: parser, validation rules, error messages, tests, and example invalid files.
- Out of scope: editor UI.

## Acceptance criteria

- [ ] Valid example files pass validation.
- [ ] Invalid files report field-level errors where possible.
- [ ] Validation catches missing weeks, invalid exercise references, impossible progressions, and unsupported schema versions.
- [ ] Tests cover success and failure cases.

## Notes

Validation should be useful from CLI, web, and future editor workflows.
