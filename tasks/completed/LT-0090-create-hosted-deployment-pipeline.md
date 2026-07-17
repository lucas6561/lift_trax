---
id: LT-0090
title: Create hosted deployment pipeline
status: done
track: distribution
priority: high
effort: medium
created: 2026-06-08
updated: 2026-06-26
owner: codex
depends_on: [LT-0082, LT-0084, LT-0085, LT-0086, LT-0087]
---

# LT-0090: Create hosted deployment pipeline

## Why

The hosted app needs repeatable environments, deploy checks, secret handling,
and rollback expectations before private beta use.

## Outcome

LiftTrax can deploy a protected hosted environment through a documented
pipeline.

## Scope

- In scope: staging and production environment definitions, build/deploy
  command, secret configuration, database migration order, quality-gate
  integration, smoke checks, rollback plan, and deployment documentation.
- Out of scope: public launch, billing automation, and broad monitoring.

## Acceptance criteria

- [x] Deployment runs from a documented command or CI workflow.
- [x] Secrets are configured outside source control.
- [x] Database migrations run before traffic reaches code that depends on them.
- [x] A smoke check verifies authenticated access and core logging after deploy.
- [x] Rollback and backup expectations are documented.

## Notes

Implemented `.github/workflows/hosted-deploy.yml` and
`scripts/hosted-smoke.ps1`.

The workflow runs `./gradlew qualityGate installDist`, optionally triggers the
Render deploy hook, and then runs hosted smoke checks. The smoke script always
checks anonymous redirect and callback hardening. If
`LIFTTRAX_HOSTED_SMOKE_SESSION_COOKIE` is configured, it also verifies
authenticated home access, lift creation, and execution logging.

Deployment documentation is in `docs/hosted-deployment-pipeline.md`.
