---
id: LT-0115
title: Decompose server auth and session orchestration
status: ready
track: quality
priority: high
effort: large
created: 2026-11-01
updated: 2026-11-01
owner: unassigned
depends_on: [LT-0113]
---

# LT-0115: Decompose server auth and session orchestration

## Why

`WebServerCli`, `WebAuth`, startup and argument parsing, and planned-workout
session saving exceed one or more LT-0113 complexity or responsibility limits.

## Outcome

Server startup, route orchestration, authentication, CLI parsing, and session
save validation have focused boundaries while preserving auth, CSRF, account
isolation, startup diagnostics, and existing command behavior.

## Acceptance criteria

- [ ] Every LT-0115 exception in `docs/source-size-and-complexity.md` passes its
      listed limits.
- [ ] Remove every LT-0115 XPath exception from `config/pmd/ruleset.xml`.
- [ ] Preserve startup, authentication, authorization, route, and session-save
      regression coverage.
- [ ] `./gradlew.bat qualityGate` passes without widening a threshold.
