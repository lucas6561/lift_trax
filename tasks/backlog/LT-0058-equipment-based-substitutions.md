---
id: LT-0058
title: Add equipment-based substitutions
status: idea
track: training-logic
priority: medium
effort: medium
created: 2026-05-30
updated: 2026-05-30
owner: unassigned
depends_on: [LT-0026, LT-0030]
---

# LT-0058: Add equipment-based substitutions

## Why

Users often need to adjust a workout when equipment is unavailable. Substitutions should respect equipment constraints instead of relying on manual guesswork.

## Outcome

Exercise swap logic can suggest replacements based on available equipment, movement pattern, and program constraints.

## Scope

- In scope: available-equipment input, substitution filtering, conflict messages, workout preview integration, and tests.
- Out of scope: live gym inventory, equipment reservations, or a full exercise recommendation engine.

## Acceptance criteria

- [ ] Users or program files can provide available equipment constraints.
- [ ] Substitution suggestions avoid exercises requiring unavailable equipment.
- [ ] When no valid substitution exists, the app explains why.
- [ ] Tests cover successful equipment-aware substitution and no-valid-option failures.
- [ ] `qualityGate` passes.

## Notes

This builds on the equipment metadata task rather than replacing it.
