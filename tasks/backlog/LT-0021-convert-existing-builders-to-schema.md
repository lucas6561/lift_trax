---
id: LT-0021
title: Convert existing builders to schema output
status: idea
track: training-logic
priority: medium
effort: large
created: 2026-05-27
updated: 2026-05-27
owner: unassigned
depends_on: [LT-0020]
---

# LT-0021: Convert existing builders to schema output

## Why

Conjugate and hypertrophy logic should remain available while moving toward schema-driven generation.

## Outcome

The existing specific builders either emit program schema files or use the generic schema builder internally, with parity tests preserving current behavior.

## Scope

- In scope: adapter design, parity tests, examples, and migration notes.
- Out of scope: deleting old code before parity is proven.

## Acceptance criteria

- [ ] Conjugate generation can be represented through schema.
- [ ] Hypertrophy generation can be represented through schema.
- [ ] Existing parity tests are preserved or replaced by equivalent schema-based tests.
- [ ] Documentation explains how specific generators relate to schema files.

## Notes

This is where hard-coded builders become presets or schema producers.
