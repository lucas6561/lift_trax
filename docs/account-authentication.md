# Account authentication

Date: 2026-06-14

## Purpose

LiftTrax now has a server-side authentication boundary for the hosted web path.
This began as the authentication step only. The hosted branch now also includes
the `LT-0086` user-scoped authorization slice and the `LT-0087` hosted
JDBC/Postgres adapter for core lift and execution workflows.

## Current behavior

The embedded web server registers auth routes separately from application
routes:

- `/auth/login`: starts sign-in;
- `/auth/dev-login`: creates a local-development session when auth mode is
  `local`;
- `/auth/callback`: receives the Supabase PKCE callback in hosted mode;
- `/auth/logout`: clears the LiftTrax session cookie.

All normal app routes are protected. Anonymous users are redirected to
`/auth/login` with a local `returnTo` path. Authenticated handlers can read the
stable server-side identity with `WebAuth.currentUser(exchange)`.

## Session cookie

LiftTrax stores a signed server-side session cookie named `lt_session`.

The cookie contains:

- the stable user ID;
- the email when available;
- an expiration timestamp;
- an HMAC signature created with the configured session secret.

The cookie is always `HttpOnly` and `SameSite=Lax`. Hosted/Supabase mode enables
`Secure` cookies by default. Local development can opt into secure cookies with
`lifttrax.auth.secureCookies=true` in the active config file.

Invalid or expired cookies fail closed: the app clears the session cookie and
redirects back to sign-in.

## Local development mode

Local development mode is the default so the app can still run offline:

```text
lifttrax.auth.mode=local
```

The local sign-in page asks for a user ID and optional email, then creates a
signed LiftTrax session. It does not store passwords and must not be used as a
hosted authentication mechanism.

## Supabase mode

Hosted auth mode uses Supabase Auth as selected in ADR-0003:

```text
lifttrax.auth.mode=supabase
lifttrax.auth.sessionSecret=<long random secret>
lifttrax.supabase.url=https://<project>.supabase.co
lifttrax.supabase.anonKey=<publishable anon key>
lifttrax.auth.provider=github
lifttrax.auth.redirectUri=https://<host>/auth/callback
lifttrax.auth.secureCookies=true
```

`/auth/login` starts a Supabase OAuth PKCE flow by redirecting to the configured
provider. `/auth/callback` validates the state cookie, exchanges the auth code
with Supabase, reads the stable `sub` claim from the returned access token, and
creates the LiftTrax server-side session cookie.

Callback errors return a generic authentication failure page and do not echo
provider error details or secrets.

## Supabase reference points

Supabase Auth uses JWT access tokens for authentication and can integrate with
Row Level Security for authorization. Supabase sessions include an access token
and refresh token. For server-side auth, the PKCE flow redirects back with an
auth code that is exchanged for session tokens.

Relevant official docs checked on 2026-06-14:

- https://supabase.com/docs/guides/auth
- https://supabase.com/docs/guides/auth/sessions
- https://supabase.com/docs/guides/auth/sessions/pkce-flow

## Remaining hosted blockers

Public hosting still needs:

- `LT-0090` creates the protected deployment pipeline and smoke checks;
- `LT-0088` imports existing local databases into a hosted account;
- `LT-0091` adds hosted backup/export controls;
- token refresh and provider logout/revocation expectations are revisited for
  the production hosted session policy.
