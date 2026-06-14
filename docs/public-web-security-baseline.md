# Public web security baseline

Date: 2026-06-14

## Purpose

LiftTrax is still not ready for public hosting. This baseline makes the embedded
web UI safer at the request boundary so the hosted path can continue toward
authentication, authorization, and deployment without carrying avoidable HTTP
risks.

Public exposure remains blocked until:

- `LT-0085` implements account authentication and secure session handling;
- `LT-0086` enforces user-scoped authorization on every hosted data path;
- `LT-0087` moves core hosted persistence behind the selected Postgres adapter;
- `LT-0090` deploys through a protected environment with managed secrets.

## Implemented baseline

`WebRequestSecurity` wraps every registered embedded-web route before the route
handler runs.

The wrapper provides:

- exact route matching, so prefix paths such as `/add-execution/extra` do not
  fall into the `/add-execution` handler;
- allowlisted methods per route;
- a one-megabyte request body limit for form and JSON submissions;
- CSRF protection for mutating methods;
- response hardening headers on normal, redirect, and rejection responses;
- a same-site CSRF cookie for local form submissions.

## CSRF model

The current app does not have accounts or server-side sessions yet, so this
baseline uses a double-submit CSRF token:

1. A safe page request receives an `lt_csrf` cookie with `HttpOnly` and
   `SameSite=Lax`.
2. Server-rendered HTML injects a hidden `csrfToken` field into every
   `method='post'` form.
3. Mutating requests must submit a matching `csrfToken` form field or
   `X-CSRF-Token` header.
4. Requests without a valid token are rejected before the handler reads or
   mutates application data.

This is a pre-auth baseline. `LT-0085` should revisit cookie flags and session
binding once the hosted auth provider is wired in. Hosted HTTPS deployment
should use secure cookies at the auth/session layer.

## Response headers

The wrapper applies these headers:

- `Content-Security-Policy` limiting resources to self, the current Pico CSS
  CDN, inline styles/scripts required by the existing renderer, data images,
  same-origin forms, and no embedding by other sites;
- `X-Content-Type-Options: nosniff`;
- `X-Frame-Options: DENY`;
- `Referrer-Policy: same-origin`;
- `Permissions-Policy` disabling geolocation, microphone, and camera.

The inline script/style allowances are intentional for the current single-file
HTML renderer. A future frontend split can tighten the policy further.

## Request limits

The request wrapper rejects bodies larger than one megabyte with `413 Request
Entity Too Large`. This limit covers current form submissions, planned workout
JSON submissions, active workout saves, and execution edits while still blocking
unbounded body reads.

If hosted imports later need larger payloads, that work should use a dedicated
route with its own limit and tests rather than raising the global default.

## Tests

`WebServerCliTest` covers:

- rejected unsafe methods before handlers run;
- rejected missing CSRF tokens on mutating requests;
- rejected oversized requests;
- security headers, CSRF cookie creation, and hidden CSRF token injection on
  HTML responses.
