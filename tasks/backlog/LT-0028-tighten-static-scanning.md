---
id: LT-0028
title: Tighten static scanning rules
status: idea
track: quality
priority: medium
effort: medium
created: 2026-05-28
updated: 2026-05-28
owner: unassigned
depends_on: [LT-0012, LT-0013]
---

# LT-0028: Tighten static scanning rules

## Why

The quality gate already runs static analysis, but the scanner should catch more risky code patterns before they reach review.

## Outcome

The project has a stricter static scanning policy with expanded PMD coverage, documented rule intent, and a clean baseline so new violations fail the quality gate.

## Scope

- In scope: PMD ruleset review, stricter correctness and maintainability rules, justified exclusions, local quality gate verification, and documentation updates.
- Out of scope: replacing PMD with a different scanner or performing broad refactors unrelated to new violations.

## Acceptance criteria

- [ ] The PMD ruleset includes additional strict rules for correctness, maintainability, and fragile code patterns.
- [ ] Any rule exclusions are documented with a short rationale.
- [ ] Existing violations introduced by the stricter rules are fixed or explicitly deferred with a follow-up task.
- [ ] `qualityGate` fails when the stricter static scanning rules are violated.
- [ ] Project documentation explains what static scanning covers and how to run it locally.

## Notes

Start by auditing PMD categories that are currently excluded or only lightly enforced, then tighten rules in small batches so failures stay understandable.
