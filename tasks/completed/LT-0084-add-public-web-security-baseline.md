---
id: LT-0084
title: Add public web security baseline
status: done
track: platform
priority: critical
effort: medium
created: 2026-06-08
updated: 2026-06-14
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

- [x] Mutating routes require CSRF protection or an explicitly documented safer
      alternative.
- [x] Request size limits protect JSON and form endpoints that accept user data.
- [x] Security headers are applied to normal HTML responses.
- [x] Tests cover rejected unsafe methods, missing CSRF tokens, and oversized
      requests.
- [x] Documentation states that the app still must not be publicly hosted until
      authentication and authorization are complete.

## Notes

Completed with `src/main/java/com/lifttrax/cli/WebRequestSecurity.java` and
`docs/public-web-security-baseline.md`.

The embedded web server now wraps registered routes with exact path checks,
method allowlists, a one-megabyte request body limit, double-submit CSRF checks
for mutating requests, and response security headers. This remains a pre-auth
baseline; public hosting still waits for `LT-0085` authentication and `LT-0086`
authorization.
