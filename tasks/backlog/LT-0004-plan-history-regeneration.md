---
id: LT-0004
title: Add plan history and regeneration rules
status: idea
track: training-logic
priority: medium
effort: large
created: 2026-05-27
updated: 2026-05-27
owner: unassigned
depends_on: [LT-0003]
---

# LT-0004: Add plan history and regeneration rules

## Why

Generated workouts become more useful when the app remembers what it suggested and explains when a plan should change.

## Outcome

LiftTrax stores generated plan history and has clear rules for when to reuse, revise, or regenerate future workouts.

## Scope

- In scope: regeneration rules, persisted history shape, and tests around deterministic behavior.
- Out of scope: full calendar scheduling and multi-week periodization UI.

## Acceptance criteria

- [ ] Generated workouts can be associated with a saved plan.
- [ ] Regeneration rules are documented in plain language.
- [ ] Re-running generation does not unexpectedly overwrite completed work.
- [ ] Tests cover at least one reuse case and one regeneration case.

## Notes

This should follow the plan model from `LT-0003`.

