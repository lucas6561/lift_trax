---
id: LT-0085
title: Implement account authentication
status: done
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

- [x] Anonymous users cannot reach protected application routes.
- [x] Signed-in users have a stable user identifier available to server code.
- [x] Session cookies use secure, HTTP-only, and same-site settings appropriate
      to the environment.
- [x] Auth callback failure states are handled without exposing secrets.
- [x] Tests cover successful login context, anonymous access, logout, and expired
      or invalid session behavior.

## Notes

`docs/adr/0003-hosted-auth-data-platform.md` selects Supabase Auth. Adapt this
task to Supabase sign-in, callback, session, JWT, and local-development flows
instead of building custom password storage.

Build on the route hardening in `docs/public-web-security-baseline.md`; auth
cookies and session binding should tighten the pre-auth CSRF cookie expectations
for hosted HTTPS.

Completed with `src/main/java/com/lifttrax/cli/WebAuth.java` and
`docs/account-authentication.md`.

The embedded web server now has open auth routes, protected app routes, signed
server-side session cookies, local-development sign-in, Supabase OAuth PKCE
callback handling, generic callback failure pages, logout, and a
`WebAuth.currentUser(exchange)` identity context for server handlers. This is
authentication only; `LT-0086` still owns user-scoped authorization.
