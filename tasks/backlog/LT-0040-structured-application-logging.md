---
id: LT-0040
title: Add structured application logging
status: idea
track: quality
priority: medium
effort: medium
created: 2026-05-30
updated: 2026-05-30
owner: unassigned
depends_on: []
---

# LT-0040: Add structured application logging

## Why

When imports, previews, migrations, or workout saves fail, the project needs enough diagnostic information to understand the failure without digging through ambiguous console output.

## Outcome

The app uses consistent structured logging for important workflows and includes useful context while avoiding sensitive or noisy data.

## Scope

- In scope: logging library choice if needed, log format, key event fields, error context, and developer documentation.
- Out of scope: hosted log aggregation, analytics tracking, or user-facing telemetry.

## Acceptance criteria

- [ ] Important workflows log start, success, and failure events consistently.
- [ ] Error logs include useful identifiers such as route, file path, schema version, or entity ID where appropriate.
- [ ] Logs avoid storing secrets, full pasted workout files, or unnecessary personal data.
- [ ] Logging behavior is covered by focused tests or verified through documented smoke checks.
- [ ] `qualityGate` passes.

## Notes

Keep this practical for a local-first app. The first goal is better debugging, not enterprise observability.
