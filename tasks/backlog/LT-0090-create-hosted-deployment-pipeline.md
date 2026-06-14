---
id: LT-0090
title: Create hosted deployment pipeline
status: idea
track: distribution
priority: high
effort: medium
created: 2026-06-08
updated: 2026-06-14
owner: unassigned
depends_on: [LT-0082, LT-0084, LT-0085, LT-0086, LT-0087]
---

# LT-0090: Create hosted deployment pipeline

## Why

The hosted app needs repeatable environments, deploy checks, secret handling, and
rollback expectations before private beta use.

## Outcome

LiftTrax can deploy a protected hosted environment through a documented pipeline.

## Scope

- In scope: staging and production environment definitions, build/deploy command,
  secret configuration, database migration order, quality-gate integration,
  smoke checks, rollback plan, and deployment documentation.
- Out of scope: public launch, billing automation, and broad monitoring.

## Acceptance criteria

- [ ] Deployment runs from a documented command or CI workflow.
- [ ] Secrets are configured outside source control.
- [ ] Database migrations run before traffic reaches code that depends on them.
- [ ] A smoke check verifies authenticated access and core logging after deploy.
- [ ] Rollback and backup expectations are documented.

## Notes

This should not ship before the public web security baseline, authentication,
authorization, and hosted persistence adapter are in place.

`docs/adr/0003-hosted-auth-data-platform.md` selects Render as the first
deployment target for the Java web service.

Use `docs/public-web-security-baseline.md` as the minimum security smoke-check
surface before enabling any public hosted environment.

Deployment smoke checks should also verify anonymous redirect to `/auth/login`,
Supabase callback configuration, secure `lt_session` cookie flags, and
authenticated access to the root app route.
