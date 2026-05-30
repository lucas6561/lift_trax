---
id: LT-0035
title: Add web form error tests
status: idea
track: quality
priority: medium
effort: medium
created: 2026-05-30
updated: 2026-05-30
owner: unassigned
depends_on: [LT-0009]
---

# LT-0035: Add web form error tests

## Why

Malformed form submissions should fail politely instead of producing confusing pages, partial writes, or server errors during normal use.

## Outcome

Route tests cover bad input paths for key web forms and verify that users get useful validation feedback.

## Scope

- In scope: missing fields, invalid numbers, invalid dates, unknown IDs, duplicate submissions, and user-facing error messages.
- Out of scope: a full client-side validation framework or visual redesign of every form.

## Acceptance criteria

- [ ] Key create, edit, import, and logging forms have malformed-input route tests.
- [ ] Invalid submissions do not write partial or corrupt data.
- [ ] Error responses preserve enough submitted values for the user to recover.
- [ ] Error messages are plain language and field-specific where practical.
- [ ] `qualityGate` passes.

## Notes

This pairs naturally with future UX work around inline validation.
