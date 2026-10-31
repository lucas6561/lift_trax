---
id: LT-0103
title: Diagnose and prevent Render staging server exits
status: done
track: platform
priority: high
effort: medium
created: 2026-10-31
updated: 2026-10-31
owner: codex
depends_on: [LT-0090]
---

# LT-0103: Diagnose and prevent Render staging server exits

## Why

Render reported that the `lifttrax-staging` web service became unavailable and
that its server process exited with status 1. The notification does not contain
the failing log entry or distinguish a deployment/startup failure from a later
runtime crash. An unexplained restart can hide configuration, migration,
database-connectivity, port-binding, resource, or application failures and
cannot be considered resolved only because Render restarted the instance.

## Outcome

The failed staging event has an evidence-backed root cause and a verified fix
or provider-specific mitigation. Future startup failures produce actionable,
secret-safe diagnostics, and repeated staging starts and hosted smoke checks
complete without an unexpected process exit.

## Scope

- In scope: Render event/log correlation; deployed revision and service-setting
  review; startup and runtime failure classification; environment-variable
  presence checks; Postgres connection and migration startup; `PORT` binding;
  Render health-check configuration; JVM/resource evidence; focused regression
  coverage; staging verification; operator documentation.
- Out of scope: disabling failure notifications, exposing secret values,
  migrating away from Render or Supabase without a separate decision, changing
  production data to reproduce the incident, or treating an automatic restart
  as proof that the problem is fixed.

## Acceptance criteria

- [x] The Render failure is correlated to an exact timestamp, deployed commit
      or image, service event, and the surrounding staging logs. Sanitized
      evidence records the first causal error rather than only the final
      `Exited with status 1` message.
- [x] The investigation classifies the failure as build, startup, migration,
      configuration, database/network, resource exhaustion, application
      runtime, process signal, or provider-side infrastructure, with evidence
      supporting the classification.
- [x] The relevant Render settings are reviewed without copying secret values:
      Docker runtime, start command, required environment-variable presence,
      `PORT`, health-check path and timeout, instance size, deploy revision,
      restart behavior, and staging database target.
- [x] The root cause is reproduced locally or in an isolated staging-safe test
      when practical. A focused automated regression test or configuration
      check fails before the fix and passes afterward.
- [x] The application or deployment configuration is corrected so the known
      failure no longer exits the staging service. Database migrations remain
      ordered and idempotent, and no production or staging training data is
      dropped or replaced as part of the fix.
- [x] Required hosted configuration is validated early with clear, secret-safe
      messages that identify missing setting names or the failing startup stage
      without printing credentials, tokens, session secrets, or JDBC passwords.
- [x] Startup diagnostics identify configuration loading, database connection,
      migration completion, HTTP bind address/port, and readiness. Unexpected
      termination retains the causal exception and does not collapse into an
      unexplained status code.
- [x] Render uses the existing `/health` endpoint as its health check, and the
      configured health/startup behavior allows migrations to finish while
      still detecting a process that never becomes ready.
- [x] At least two consecutive clean staging deploy or cold-start cycles are
      observed after the fix, followed by the hosted smoke checks. Anonymous
      checks are mandatory; authenticated logging/history checks run when the
      staging smoke session is available.
- [x] The deployment runbook documents the incident cause, the fix, how to find
      the relevant Render logs, how to distinguish build/start/runtime failure,
      and the rollback or escalation path if the alert recurs.
- [x] If evidence shows a provider-side failure that cannot be reproduced or
      fixed in LiftTrax, the issue records that evidence and implements the
      appropriate detection, retry, resource, or operational mitigation instead
      of inventing an unrelated code change.
- [x] Focused tests and `./gradlew.bat qualityGate` pass.

## Notes

Reported by Render email on 2026-10-31 for service `lifttrax-staging`
(`srv-d9dd2f61a83c739b6meg`) with the summary `Exited with status 1`.

Start with the Render logs and events for the affected instance. The current
container runs `WebServerCli` with Render's `PORT`, uses
`config/lifttrax-render.properties`, connects to Postgres, and applies ordered
application migrations during startup. Existing references are `Dockerfile`,
`.github/workflows/hosted-deploy.yml`, `scripts/hosted-smoke.ps1`, and
`docs/hosted-deployment-pipeline.md`.

Investigation and verification: [Render staging startup incident](../../docs/render-staging-startup-incident.md). Root cause: initial Postgres startup/TLS transport failure was immediately fatal; bounded connection retries and startup diagnostics are implemented. Staging verification passed: Docker build and container quality gate, two consecutive clean starts (including recovery on connection attempt 2), anonymous hosted smoke, authenticated browser logging/history/detail, and identical before/after snapshots. See the runbook for timestamps and retained synthetic smoke data.
