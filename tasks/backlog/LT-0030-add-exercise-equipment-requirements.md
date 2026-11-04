---
id: LT-0030
title: Add exercise equipment requirements
status: ready
track: training-logic
priority: high
effort: large
created: 2026-05-30
updated: 2026-11-04
owner: unassigned
depends_on: [LT-0023]
---

# LT-0030: Add exercise equipment requirements

## Why

Circuit planning should not accidentally select multiple movements that all need the same limited equipment, such as a cable station, rack, bench, or dumbbell pair. The app should reason from explicit exercise metadata instead of exercise-name heuristics.

## Outcome

Equipment and exercise-equipment relationships are persisted as account-scoped,
normalized data. An exercise can declare multiple alternative equipment setups,
and each setup can require one or more equipment items without confusing "all
of these" requirements with "any one of these" alternatives.

## Scope

- In scope: Postgres migrations; stable equipment identities; account and lifter
  ownership; exercise-to-equipment option groups; quantities and exclusivity
  needed by later conflict checks; structured catalog import/export; workout
  contract compatibility; backup and recovery coverage; documentation; tests.
- Out of scope: equipment-management pages, automatic workout rearrangement,
  live reservations shared between lifters, maintenance scheduling, and
  equipment-based exercise recommendations.

## Acceptance criteria

- [ ] A forward-only Postgres migration adds normalized, account-scoped
      equipment data and exercise-equipment relationships without changing or
      deleting existing catalog, execution, plan, or account rows.
- [ ] The model distinguishes the items required together within one setup from
      alternative valid setups. For example, `{rack, bench, barbell}` and
      `{chest-press machine}` are two alternatives rather than four simultaneous
      requirements.
- [ ] Exercises can represent no equipment, one item, several items used
      together, and multiple alternative setups. Equipment and option groups
      use stable identities rather than display-name joins.
- [ ] The model records enough availability, quantity, and exclusive-use
      semantics for later same-block conflict detection without implementing a
      live multi-user reservation system.
- [ ] Program-schema equipment strings and older exercise-catalog/workout files
      remain readable. A documented mapping or versioned contract preserves
      their meaning without silently inventing equipment associations.
- [ ] Account exports, catalog import/export, Postgres-to-SQLite operator
      backups, snapshot integrity checks, and recovery documentation include the
      new data where appropriate and never expose another account's inventory.
- [ ] Renaming, archiving, or changing current equipment metadata does not
      rewrite completed exercise history.
- [ ] Tests cover migrations from the current schema, ownership isolation,
      alternative and compound setups, invalid references, round trips,
      backups, and rollback using the pre-migration snapshot.
- [ ] `qualityGate` passes.

## Notes

The current conjugate circuit builder only checks for duplicate cable and
dumbbell-like exercise names. `LT-0125` replaces that heuristic after this
card supplies explicit metadata.

Program schema v1 already has a flat per-exercise `equipment` list, while the
exercise-catalog contract has no structured equipment fields. Preserve the
flat field as a compatibility input, but use explicit option groups in the
database so alternatives are not flattened into incorrect requirements.
