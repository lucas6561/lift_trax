---
id: LT-0039
title: Add realistic training history fixtures
status: idea
track: quality
priority: medium
effort: medium
created: 2026-05-30
updated: 2026-05-30
owner: unassigned
depends_on: [LT-0027]
---

# LT-0039: Add realistic training history fixtures

## Why

Many meaningful behaviors only show up with several weeks of training history. Realistic fixtures make tests, demos, and future UI work more representative.

## Outcome

The project has reusable multi-week training history fixtures that cover normal progression, missed sessions, substitutions, notes, and completed workout data.

## Scope

- In scope: fixture format, seed helper, representative histories, documentation, and tests that prove fixtures load correctly.
- Out of scope: importing real user data or creating a full sample-data management UI.

## Acceptance criteria

- [ ] At least one multi-week fixture covers multiple lifts, sessions, and progression outcomes.
- [ ] Fixtures can be loaded by tests without duplicating setup logic.
- [ ] Fixture data includes imperfect real-world cases such as missed sets or exercise swaps.
- [ ] Documentation explains what each fixture is meant to exercise.
- [ ] `qualityGate` passes.

## Notes

These fixtures should become the shared basis for trends, summaries, recommendations, and follow-along workout tests.
