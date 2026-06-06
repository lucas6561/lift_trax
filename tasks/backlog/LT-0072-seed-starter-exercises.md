---
id: LT-0072
title: Seed starter exercises for new users
status: ready
track: data
priority: medium
effort: medium
created: 2026-06-06
updated: 2026-06-06
owner: unassigned
depends_on: [LT-0005]
---

# LT-0072: Seed starter exercises for new users

## Why

A fresh LiftTrax install should not feel empty or require a new user to manually create every basic lift before they can log training, preview workouts, or use generated plans.

## Outcome

New databases start with a small, practical exercise catalog covering common barbell lifts, accessories, bodyweight movements, and conditioning options. Existing users keep their current lift library untouched.

## Scope

- In scope: a curated starter exercise list, startup or migration seeding path, idempotent insert behavior, tests for fresh and existing databases, and short documentation of what gets seeded.
- Out of scope: importing full public exercise databases, personalized recommendations, sample workout history, fake maxes, seeded completed workouts, or a full exercise-library editor.

## Acceptance criteria

- [ ] A fresh database receives a basic lift catalog with enough squat, deadlift, bench, overhead, accessory, bodyweight, and conditioning entries to make the app usable immediately.
- [ ] Seeding only runs when appropriate for a new or empty lift library and never duplicates or overwrites existing user-created lifts.
- [ ] Seeded exercises use the existing lift metadata fields consistently so dashboard filters, workout builders, and planned-workout logging can consume them without special cases.
- [ ] The behavior is covered by tests for an empty database, a database that has already been seeded, and a database with existing user lifts.
- [ ] Documentation or notes identify the starter set and explain how future migrations should add or adjust seeded exercises without damaging user data.
- [ ] `qualityGate` passes.

## Notes

This should complement `LT-0041` rather than replace it: empty states still matter, but a brand-new local database should have useful exercise options before any training history exists.

Prefer a compact, maintainable starter list over a huge taxonomy. If equipment metadata from `LT-0030` has landed by implementation time, seed entries should be easy to enrich with that data, but this task should not require equipment-aware circuit planning to ship first.
