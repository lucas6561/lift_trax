---
id: LT-0070
title: Add latest schema entrypoints
status: idea
track: training-logic
priority: high
effort: medium
created: 2026-06-05
updated: 2026-06-05
owner: unassigned
depends_on: [LT-0061]
---

# LT-0070: Add latest schema entrypoints

## Why

Versioned schema snapshots are good for compatibility, but they make public
instructions fragile. If a user copies a raw GitHub URL for
`workout.schema.v2.json`, that URL becomes stale when the latest schema moves to
v3. The repo needs a stable "latest" entrypoint that always represents the
current supported schema.

## Outcome

LiftTrax exposes stable latest-schema paths for public docs and AI prompts, and
tests fail if those paths drift from the latest schema versions registered in
the application.

## Scope

- In scope: stable latest-schema assets or pointers for planned workouts and,
  if practical, authored programs; README/schema-versioning docs updates; tests
  that compare the stable paths with the latest registered version; and release
  notes for how to update them when a schema version advances.
- Out of scope: changing the current schema payload shape, removing frozen
  numbered schema snapshots, or dropping backward-compatible readers.

## Acceptance criteria

- [ ] The repo contains a stable latest planned-workout schema path suitable for
      raw GitHub links.
- [ ] The repo contains a stable latest program schema path or documents why the
      planned-workout schema is the only public AI-generation entrypoint for
      now.
- [ ] Tests prove each stable latest path matches the latest registered schema
      version from the schema catalog.
- [ ] `docs/schema-versioning.md` explains the difference between frozen
      numbered snapshots and stable latest entrypoints.
- [ ] README uses the stable latest paths for public-facing schema guidance.
- [ ] `qualityGate` passes.

## Notes

Prefer an approach that works reliably from GitHub raw URLs on Windows checkouts.
If alias files are used, add a guard test so updating the latest catalog without
updating the alias fails loudly.
