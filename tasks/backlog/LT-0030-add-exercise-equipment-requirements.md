---
id: LT-0030
title: Add exercise equipment requirements
status: ready
track: training-logic
priority: medium
effort: medium
created: 2026-05-30
updated: 2026-05-30
owner: unassigned
depends_on: [LT-0023]
---

# LT-0030: Add exercise equipment requirements

## Why

Circuit planning should not accidentally select multiple movements that all need the same limited equipment, such as a cable station, rack, bench, or dumbbell pair. The app should reason from explicit exercise metadata instead of exercise-name heuristics.

## Outcome

Exercises can declare their needed equipment in a normalized way, and circuit generation or validation uses that metadata to avoid or clearly flag incompatible equipment combinations.

## Scope

- In scope: canonical equipment vocabulary, exercise equipment metadata, workout output equipment fields, circuit conflict rules, validation or generation fallback behavior, documentation, and tests.
- Out of scope: live gym equipment availability, multi-user equipment reservations, full facility inventory management, and a general equipment editor unless it is needed to maintain exercise metadata.

## Acceptance criteria

- [ ] A normalized set of equipment identifiers is documented and used consistently by program schema, workout output, and local exercise data.
- [ ] Exercises can represent zero, one, or multiple required equipment items, including bodyweight or no-equipment cases.
- [ ] Circuit building detects incompatible repeated exclusive equipment in the same circuit and chooses non-conflicting alternatives when available.
- [ ] When no valid circuit can be built, the error names the conflicting equipment and the exercises or pools involved.
- [ ] Exercise swap or substitution logic can use equipment requirements to avoid replacing a movement with one that violates active equipment constraints.
- [ ] Tests cover non-conflicting circuits, conflict fallback, no-valid-alternative failure, and serialization or loading of equipment metadata.
- [ ] `qualityGate` passes.

## Notes

The current conjugate circuit builder only checks for duplicate cable and dumbbell-like exercise names. This task should replace or generalize that heuristic with explicit metadata.

Program schema v1 already has per-exercise `equipment`; carry that concept through workout output and app domain models instead of inventing a second meaning.
