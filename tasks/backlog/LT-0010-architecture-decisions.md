---
id: LT-0010
title: Document architecture decisions
status: ready
track: docs
priority: medium
effort: small
created: 2026-05-27
updated: 2026-05-27
owner: unassigned
depends_on: []
---

# LT-0010: Document architecture decisions

## Why

The project is an incremental Java port with CLI, web UI, database, and workout planning pieces. A short decision log will help future changes stay coherent.

## Outcome

The repository has a lightweight architecture decision record folder for important choices.

## Scope

- In scope: ADR folder, template, and first decision record for local-first Java/SQLite architecture.
- Out of scope: long-form architecture documentation.

## Acceptance criteria

- [ ] An ADR template exists.
- [ ] At least one current architecture decision is documented.
- [ ] `JAVA_PROJECT.md` links to the decision records.

## Notes

This can stay intentionally small. The goal is memory, not ceremony.

