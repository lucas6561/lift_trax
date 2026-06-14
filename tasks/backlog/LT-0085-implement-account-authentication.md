---
id: LT-0085
title: Implement account authentication
status: idea
track: platform
priority: critical
effort: large
created: 2026-06-08
updated: 2026-06-14
owner: unassigned
depends_on: [LT-0082, LT-0084]
---

# LT-0085: Implement account authentication

## Why

Hosted LiftTrax needs a reliable way to know who is using the app before it can
store private training data online.

## Outcome

Users can sign in and out through the selected auth provider, and the app has a
server-side identity context available to protected routes.

## Scope

- In scope: sign-in, sign-out, callback handling, session validation, protected
  route redirects, secure cookie configuration, local-development auth behavior,
  and tests for authenticated and anonymous requests.
- Out of scope: user-scoped authorization for every data record.

## Acceptance criteria

- [ ] Anonymous users cannot reach protected application routes.
- [ ] Signed-in users have a stable user identifier available to server code.
- [ ] Session cookies use secure, HTTP-only, and same-site settings appropriate
      to the environment.
- [ ] Auth callback failure states are handled without exposing secrets.
- [ ] Tests cover successful login context, anonymous access, logout, and expired
      or invalid session behavior.

## Notes

`docs/adr/0003-hosted-auth-data-platform.md` selects Supabase Auth. Adapt this
task to Supabase sign-in, callback, session, JWT, and local-development flows
instead of building custom password storage.

Build on the route hardening in `docs/public-web-security-baseline.md`; auth
cookies and session binding should tighten the pre-auth CSRF cookie expectations
for hosted HTTPS.
