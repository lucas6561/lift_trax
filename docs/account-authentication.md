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
- `/auth/local-register`: creates a new local account with a unique username;
  unavailable in Supabase mode;
- `/auth/callback`: receives the Supabase PKCE callback in hosted mode;
- `/auth/logout`: clears the LiftTrax session cookie.
- `/account`: lets the signed-in user choose a memorable LiftTrax username.

All normal app routes are protected. Anonymous users are redirected to
`/auth/login` with a local `returnTo` path. Authenticated handlers can read the
stable server-side identity with `WebAuth.currentUser(exchange)`.

## Session cookie

LiftTrax stores a signed server-side session cookie named `lt_session`.

The cookie contains:

- the stable user ID;
- the email when available;
- the provider username as an initial account-page suggestion when available;
- an expiration timestamp;
- an HMAC signature created with the configured session secret.

The cookie is always `HttpOnly` and `SameSite=Lax`. Hosted/Supabase mode enables
`Secure` cookies by default. Local development can opt into secure cookies with
`lifttrax.auth.secureCookies=true` in the active config file.

Invalid or expired cookies fail closed: the app clears the session cookie and
redirects back to sign-in.

## Local development mode

Local development mode is the default. It uses the configured Postgres database:

```text
lifttrax.auth.mode=local
```

The local sign-in page asks for a username or account ID,
then creates a signed LiftTrax session. The account field defaults to the same
machine-local `lifttrax.cli.userId` / `LIFTTRAX_CLI_USER_ID` setting used by
operator commands. Signing in does not change the account's saved email. The old
`lifttrax.auth.localEmail` / `LIFTTRAX_AUTH_LOCAL_EMAIL` setting is no longer used.
A username is
resolved to the existing immutable auth ID
before the session is signed, so signing out and back in cannot create a second
empty identity merely because the friendly username was entered. Local mode
does not store passwords and must not be used as a hosted authentication
mechanism.

Choose **Create a local account** to register a new username and optional email.
Registration assigns a new immutable local identity and creates its account and
default lifter profile in one transaction. Duplicate or invalid usernames fail
without creating a partial account or signing into another user's account.
Unknown usernames on the sign-in form still fail; sign-in never creates accounts.
New accounts start with an empty lift list and history.

Separate devices or browser profiles can sign in concurrently. Tabs in one
browser profile share the session cookie. All authenticated POST forms carry an
account scope; after switching users, an old form is rejected with HTTP 409 before
it reaches a data handler, even if its CSRF token remains valid. Reopen the page
under the original account to continue. Work Along keeps its draft when a save
is rejected, and Resume restores it after signing back in.

Add Execution drafts and dashboard filters use storage keys scoped to the
immutable account identity. Old unscoped Add Execution drafts are not restored
automatically because their owner cannot be identified. Existing account-scoped
Work Along drafts retain their keys. Request identity and CSRF attributes are
private to each HTTP exchange, including simultaneous requests to the same route.

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

## LiftTrax usernames

The immutable Supabase `sub` claim remains the private authorization and
ownership key. LiftTrax stores a separate mutable username for display and
operator lookup; changing a username never changes record ownership.

Authenticated users can set or change their username at `/account`. Usernames
are normalized to lowercase, must contain 3-30 letters, numbers, underscores,
or hyphens, and are unique. The GitHub username is suggested on first setup when
Supabase includes it in `user_metadata.user_name`.

Signed-in browser requests always select the session account automatically.
Operator commands have no browser session, so their existing `--user` option
and machine-local default accept either the LiftTrax username or immutable auth
ID. Unknown identifiers fail instead of silently creating a new account.

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
