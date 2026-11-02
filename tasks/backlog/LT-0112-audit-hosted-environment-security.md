---
id: LT-0112
title: Audit Render and Supabase environment security
status: ready
track: distribution
priority: high
effort: medium
created: 2026-10-31
updated: 2026-10-31
owner: unassigned
depends_on: [LT-0090, LT-0103]
---

# LT-0112: Audit Render and Supabase environment security

## Why

The repository implements authentication, account-scoped authorization, secret
injection, and hosted smoke checks, but the live Render and Supabase dashboard
settings were not included in the 2026-10-31 local-machine review. Configuration
drift can expose data even when application code is correct.

## Outcome

Staging and any production environment have a documented, verified security
posture for public routes, authentication callbacks, secrets, database access,
logs, environment separation, backups, and incident response.

## Scope

- In scope: read-only dashboard inventory; Render service and environment
  settings; Supabase Auth and database settings; secrets and logs; staging data;
  anonymous/authenticated/cross-account checks; backups; remediation plan.
- Out of scope: copying secret values, deleting/recreating databases, changing
  production, rotating credentials, or making a service public/private without
  explicit approval and a tested rollback.

## Acceptance criteria

- [ ] Inventory Render and Supabase projects, environments, regions, domains,
      deploy sources, service identities, database targets, and auth modes
      without displaying or storing secret values.
- [ ] Verify staging and production, if production exists, use separate deploy
      hooks, session secrets, database credentials, callback allowlists, smoke
      credentials, and data boundaries.
- [ ] Confirm protected routes reject or redirect anonymous requests; only the
      intended health, authentication, and static/PWA routes are public.
- [ ] Verify exact Supabase OAuth redirect URLs and provider allowlists without
      broad wildcards, and confirm invalid state/callback attempts fail closed.
- [ ] Confirm Render-to-Postgres traffic requires encryption, database
      credentials are least-privileged and stored only as managed secrets, and
      no database port is exposed through the LiftTrax web service.
- [ ] Review environment variables and recent application/build logs for secret
      disclosure using names/presence checks and redacted evidence only. Rotate
      a suspect credential only after backup, impact review, and approval.
- [ ] Confirm staging contains no unnecessary copy of personal production data;
      document retention and safe cleanup for any test or imported data.
- [ ] Verify backups/account exports and rollback expectations before changing
      configuration. Do not treat an account export as a database restore.
- [ ] Run anonymous-route, authentication, CSRF, account-isolation, and hosted
      smoke checks, including two separate accounts for cross-account reads and
      mutations where safe test data is available.
- [ ] Review Render health checks, restart policy, resource limits, and the
      LT-0103 startup runbook without weakening security to improve uptime.
- [ ] Produce a dated, secret-free findings report with severity, evidence,
      owner, remediation, and explicit residual risks. Run focused tests and
      `./gradlew.bat qualityGate` for any repository changes.

## Notes

Start read-only. Staging restarts and test-account writes are allowed only when
they are part of a reviewed verification plan; production mutations require
separate explicit approval.
