# M6: Equipment-aware training

## Goal

Make equipment a first-class, maintainable part of LiftTrax so plans reflect
what the lifter owns or can access and avoid impossible same-block combinations.

## Exit criteria

- Equipment and exercise-equipment options are normalized, account-scoped, and
  safely persisted in Postgres.
- A lifter can maintain equipment inventory and alternative exercise setups
  through the authenticated application.
- Simultaneous workout blocks detect and resolve limited-equipment conflicts
  without relying on exercise names.
- Substitution suggestions respect available equipment and active block
  constraints.
- Existing workout, catalog, history, export, backup, and account-isolation
  behavior remains compatible and tested.

## Tasks

- `LT-0030`: Add exercise equipment requirements.
- `LT-0124`: Manage equipment inventory and exercise setups.
- `LT-0125`: Resolve equipment conflicts within workout blocks.
- `LT-0026`: Add exercise swap rules.
- `LT-0058`: Add equipment-based substitutions.

## Sequence

1. `LT-0030` establishes the database and compatibility model, including the
   distinction between compound requirements and alternative setups.
2. `LT-0124` makes inventory and exercise setups maintainable without direct
   database or JSON editing.
3. `LT-0125` uses the saved inventory to validate and deconflict simultaneous
   blocks.
4. `LT-0026` and `LT-0058` provide explicit, equipment-aware exercise swaps
   when changing setup alone cannot resolve a block.

## Product boundary

This milestone models one lifter's available equipment and plan feasibility. It
does not attempt multi-person reservations, live gym occupancy, purchasing, or
facility-management software.
