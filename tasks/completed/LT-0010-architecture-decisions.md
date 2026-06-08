---
id: LT-0010
title: Document architecture decisions
status: done
track: docs
priority: medium
effort: small
created: 2026-05-27
updated: 2026-06-07
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

- [x] An ADR template exists.
- [x] At least one current architecture decision is documented.
- [x] `JAVA_PROJECT.md` links to the decision records.

## Notes

Completed by adding `docs/adr/README.md`, `docs/adr/TEMPLATE.md`, and
`docs/adr/0001-local-first-java-sqlite.md`. `JAVA_PROJECT.md` now links to the
ADR folder and the first decision record.

Verification: documentation link and task placement checks passed on
2026-06-07.
