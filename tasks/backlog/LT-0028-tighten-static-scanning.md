---
id: LT-0028
title: Tighten static scanning rules
status: done
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

- [x] The PMD ruleset includes additional strict rules for correctness, maintainability, and fragile code patterns.
- [x] Any rule exclusions are documented with a short rationale.
- [x] Existing violations introduced by the stricter rules are fixed or explicitly deferred with a follow-up task.
- [x] `qualityGate` fails when the stricter static scanning rules are violated.
- [x] Project documentation explains what static scanning covers and how to run it locally.

## Notes

Added stricter PMD coverage for code style, design, concurrency, and selected performance risks. Fixed the clean baseline issues from the new rules: CLI utility-class shape, StringBuilder/string concatenation findings, and missing braces around max-effort pool guard clauses.

The static analysis policy and intentional exclusions are documented in `config/pmd/ruleset.xml`; local workflow docs now explain what PMD covers and point back to the ruleset.

Verified on 2026-05-28 by adding a temporary `ControlStatementBraces` violation and confirming `qualityGate` failed in `pmdMain`, then removing the probe and rerunning `qualityGate` successfully.
