---
id: LT-0124
title: Manage equipment inventory and exercise setups
status: idea
track: interface
priority: high
effort: large
created: 2026-11-04
updated: 2026-11-04
owner: unassigned
depends_on: [LT-0030]
---

# LT-0124: Manage equipment inventory and exercise setups

## Why

Structured equipment data is only useful if a lifter can maintain it without
editing database rows or JSON by hand. Different homes and gyms have different
equipment and quantities, and one exercise may be possible through several
valid setups.

## Outcome

The authenticated application provides clear pages for maintaining the current
lifter's equipment inventory and associating one or more alternative equipment
setups with an exercise.

## Scope

- In scope: equipment list and edit pages; add, rename, quantity, availability,
  and archive flows; exercise equipment-option editing; server-side validation;
  account isolation; mobile and keyboard usability; progressive enhancement;
  tests.
- Out of scope: deleting performed history, shared live reservations, gym floor
  maps, maintenance logs, purchasing, barcode scanning, and automatic exercise
  substitutions.

## Acceptance criteria

- [ ] Account controls include an Equipment destination that lists the current
      lifter's equipment, availability, quantity, and archived state without
      adding another primary navigation tab.
- [ ] The lifter can add, rename, change quantity or availability, and archive
      equipment with field-level validation and clear duplicate-name handling.
- [ ] Exercise catalog editing can add, reorder, edit, and remove alternative
      equipment setups. The UI clearly distinguishes items required together
      from separate valid alternatives.
- [ ] Archiving equipment preserves exercise associations and history, warns
      about affected active exercises, and offers an explicit safe resolution.
- [ ] Forms preserve submitted values after validation errors and work without
      JavaScript; JavaScript enhancements do not bypass server validation.
- [ ] Routes enforce authentication, CSRF protection, and lifter/account
      ownership. IDs or names from another account cannot be viewed or mutated.
- [ ] Desktop and 390-by-844 phone browser checks cover list, add, edit,
      archive, alternative setup entry, keyboard use, refresh, and back/forward
      behavior.
- [ ] Focused route, persistence, rendering, JavaScript, accessibility, and
      ownership tests cover successful and invalid paths.
- [ ] `qualityGate` passes.

## Notes

Follow the unified application shell: inventory belongs under Account, while
exercise setup associations belong with the Exercise Catalog. Do not create a
new equal-weight root tab for equipment.
