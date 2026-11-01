---
id: LT-0119
title: Refine test granularity and quality ratchet
status: completed
track: quality
priority: high
effort: large
created: 2026-11-01
updated: 2026-11-01
owner: unassigned
depends_on: [LT-0113, LT-0114, LT-0115, LT-0118]
---

# LT-0119: Refine test granularity and quality ratchet

## Why

The zero-exception PMD baseline prevents the largest source and complexity
regressions, but passing a size ceiling does not prove that a test has one
reason to fail or that a class has one cohesive responsibility. Several route
and renderer tests still combined independent contracts, and extracted browser
templates contained JavaScript that Java PMD could not analyze.

## Outcome

Fast renderer and route checks express one behavioral contract per test,
expensive cross-component scenarios remain explicit acceptance tests, browser
automation fails on runtime script errors, and the source ratchet is tightened
to the strongest clean baseline supported by the refactor.

## Acceptance criteria

- [x] Split broad renderer, planned-session, route, and parser checks into
      independently named tests with isolated setup and one reason to fail.
- [x] Retain TLS and multi-account end-to-end coverage as clearly named
      acceptance scenarios rather than duplicating expensive fixture startup.
- [x] Extend the authenticated desktop/mobile browser check to exercise the
      planned-session script and fail on uncaught browser runtime errors.
- [x] Tighten production and test physical-size or complexity thresholds where
      the measured post-refactor baseline allows it, without exceptions.
- [x] Document the difference between unit/contract tests and acceptance
      scenarios, including when multiple assertions are appropriate.
- [x] Focused tests, browser checks, and `./gradlew.bat qualityGate` pass.

## Verification

- 404 tests: 0 failures, 0 errors, 2 environment skips.
- 92.626% JaCoCo instruction coverage.
- Desktop 1280x900 and mobile 390x844 authenticated browser checks passed,
  including planned-session draft persistence, block skip/restore, responsive
  layout, service-worker registration, and zero uncaught runtime exceptions.
- `./gradlew.bat qualityGate` passed on 2026-11-01.
