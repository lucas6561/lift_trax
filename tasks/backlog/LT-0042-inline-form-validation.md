---
id: LT-0042
title: Add inline form validation
status: idea
track: interface
priority: high
effort: medium
created: 2026-05-30
updated: 2026-05-30
owner: unassigned
depends_on: [LT-0001, LT-0024]
---

# LT-0042: Add inline form validation

## Why

Users should be able to correct form mistakes without guessing which field failed or losing the values they already entered.

## Outcome

Important lift, execution, import, and workout forms show field-level validation messages and preserve user input after errors.

## Scope

- In scope: server-side validation messages, field-level rendering, value preservation, and tests for invalid submissions.
- Out of scope: replacing server-side checks with client-only validation or redesigning every form at once.

## Acceptance criteria

- [ ] Invalid required fields show a nearby message.
- [ ] Invalid numeric, date, and selection values are named clearly.
- [ ] Submitted values are preserved when the form is redisplayed.
- [ ] Tests cover at least one invalid path for each updated form type.
- [ ] `qualityGate` passes.

## Notes

This should reuse validation logic rather than letting every route invent its own message style.
