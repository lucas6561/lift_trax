---
id: LT-0027
title: Persist planned versus completed workout data
status: idea
track: data
priority: high
effort: large
created: 2026-05-27
updated: 2026-05-27
owner: unassigned
depends_on: [LT-0024, LT-0005]
---

# LT-0027: Persist planned versus completed workout data

## Why

The app needs to compare what was planned with what actually happened during training.

## Outcome

Database tables and domain models store generated plans, planned workout items, completed sessions, completed sets, skipped work, and substitutions.

## Scope

- In scope: schema design, migrations, domain models, query paths, tests, and documentation.
- Out of scope: analytics beyond basic planned-versus-completed retrieval.

## Acceptance criteria

- [ ] Planned workouts can be stored and retrieved.
- [ ] Completed sessions are linked to planned workouts.
- [ ] Completed sets retain actual values and planned targets.
- [ ] Skips and substitutions are represented.
- [ ] Migration and database tests cover the new tables.

## Notes

Coordinate carefully with the user ownership model so multi-user changes do not require redoing these tables.
