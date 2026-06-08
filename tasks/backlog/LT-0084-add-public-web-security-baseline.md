---
id: LT-0084
title: Add public web security baseline
status: idea
track: platform
priority: critical
effort: medium
created: 2026-06-08
updated: 2026-06-08
owner: unassigned
depends_on: [LT-0015]
---

# LT-0084: Add public web security baseline

## Why

The current web UI is appropriate for local-network use, but public hosting
requires explicit request hardening before any browser-accessible deployment is
safe.

## Outcome

LiftTrax has a documented and tested baseline for public web request safety.

## Scope

- In scope: secure response headers, CSRF protection for mutating requests,
  same-site cookie expectations, request body limits, method checks, route
  hardening, basic rate-limit strategy, and security test coverage.
- Out of scope: user login implementation and provider-specific deployment.

## Acceptance criteria

- [ ] Mutating routes require CSRF protection or an explicitly documented safer
      alternative.
- [ ] Request size limits protect JSON and form endpoints that accept user data.
- [ ] Security headers are applied to normal HTML responses.
- [ ] Tests cover rejected unsafe methods, missing CSRF tokens, and oversized
      requests.
- [ ] Documentation states that the app still must not be publicly hosted until
      authentication and authorization are complete.

## Notes

This task is intentionally separate from authentication so the app has route
hardening before account flows are added.
