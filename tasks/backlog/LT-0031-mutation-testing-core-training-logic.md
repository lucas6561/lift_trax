---
id: LT-0031
title: Add mutation testing for core training logic
status: idea
track: quality
priority: medium
effort: medium
created: 2026-05-30
updated: 2026-05-30
owner: unassigned
depends_on: [LT-0012]
---

# LT-0031: Add mutation testing for core training logic

## Why

The training logic should be protected by tests that fail when meaningful behavior changes. Mutation testing can expose places where the code is covered but the assertions are too weak.

## Outcome

A mutation-testing workflow checks high-value training logic and reports surviving mutations that need better assertions or clearer behavior.

## Scope

- In scope: mutation-test tool selection, configuration for core training packages, documentation, and a small initial quality threshold.
- Out of scope: requiring mutation testing for every package immediately, replacing unit tests, or blocking all development on a perfect mutation score.

## Acceptance criteria

- [ ] A mutation-testing tool is configured for the Java project.
- [ ] The first target package or package set is documented.
- [ ] Surviving mutations are reported in a repeatable local command.
- [ ] At least one weak test is improved or a baseline is documented for future tightening.
- [ ] `qualityGate` passes.

## Notes

Start with training logic where regressions would be expensive, such as wave generation, schema conversion, workout output, or progression calculations.
