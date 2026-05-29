---
id: LT-0019
title: Build a schema validation layer
status: done
track: training-logic
priority: high
effort: medium
created: 2026-05-27
updated: 2026-05-28
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

- [x] Valid example files pass validation.
- [x] Invalid files report field-level errors where possible.
- [x] Validation catches missing weeks, invalid exercise references, impossible progressions, and unsupported schema versions.
- [x] Tests cover success and failure cases.

## Notes

Validation should be useful from CLI, web, and future editor workflows.

Completed with `ProgramSchemaValidator`, field-level validation result types,
invalid example files under `shared/programs/examples/invalid/`, and
`ProgramSchemaValidatorTest`.

Verification:

- `.\gradlew.bat test`
- `.\gradlew.bat qualityGate`
