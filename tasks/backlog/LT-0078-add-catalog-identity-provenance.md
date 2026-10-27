---
id: LT-0078
title: Add portable identity and provenance to the exercise catalog
status: idea
track: data
priority: medium
effort: medium
created: 2026-06-07
updated: 2026-10-27
owner: unassigned
depends_on: [LT-0076, LT-0092]
---

# LT-0078: Add portable identity and provenance to the exercise catalog

## Why

The account-owned Postgres catalog already has durable internal IDs, but copied
and future file-imported exercises need provenance and deterministic matching.
Name alone is not a sufficient portable identity when users rename exercises or
import later revisions.

## Outcome

Catalog entries retain their account-owned internal identity while optionally
recording a portable source identity and provenance for starter, shared, and
file-imported definitions.

## Scope

- In scope: provenance model, portable source key rules, Postgres migration,
  shared-lift copy provenance, starter-catalog compatibility, deterministic
  merge semantics for `LT-0093`, export fields, and tests.
- Out of scope: a separate catalog database, marketplace discovery, silently
  mutating catalog rows during program/workout import, and equipment conflict
  behavior from `LT-0030`.

## Acceptance criteria

- [ ] The model distinguishes the account-owned catalog row ID from an optional
      portable source identity.
- [ ] User-created, starter, shared-copy, and file-imported origins have
      documented provenance rules that do not expose another user's private ID.
- [ ] Reimport matching uses source identity when present and a documented
      conflict path when it is absent; name-only overwrite is never silent.
- [ ] Existing catalog rows migrate safely as user-created or unknown-origin
      rows without changing their names, enabled state, or history links.
- [ ] Account export includes portable provenance needed to audit or later
      reimport an owned catalog without leaking server-only ownership keys.
- [ ] Tests cover existing rows, renamed rows, shared copies, duplicate source
      keys, and cross-account isolation.
- [ ] `qualityGate` passes.

## Notes

This replaces the obsolete catalog-database-foundation task. Postgres table
`exercise_catalog_entries` is the durable catalog foundation; the remaining
remaining gap is portable identity and provenance.
