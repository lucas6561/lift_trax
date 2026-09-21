# Account authentication

Updated: 2026-09-21

## Purpose

LiftTrax now has a server-side authentication boundary for the hosted web path.
This began as the authentication step only. The hosted branch now also includes
the `LT-0086` user-scoped authorization slice and the `LT-0087` hosted
JDBC/Postgres adapter for core lift and execution workflows.

## Current behavior

The embedded web server registers auth routes separately from application
routes:

- `/auth/login`: starts sign-in;
- `/auth/dev-login`: verifies a local account password and creates a session;
- `/auth/local-register`: creates a new local account with a unique username and password;
  unavailable in Supabase mode;
- `/auth/callback`: receives the Supabase PKCE callback in hosted mode;
- `/auth/logout`: clears the LiftTrax session cookie.
- `/account`: manages the username, lift sharing, and local password.
- `/auth/change-password`: requires the current password and changes the local
  password, revoking all existing local sessions. Unavailable in Supabase mode.

All normal app routes are protected. Anonymous users are redirected to
`/auth/login` with a local `returnTo` path. Authenticated handlers can read the
stable server-side identity with `WebAuth.currentUser(exchange)`.

## Session cookie

LiftTrax stores a signed server-side session cookie named `lt_session`.

The cookie contains:

- the stable user ID;
- the email when available;
- the provider username as an initial account-page suggestion when available;
- the credential version for local accounts;
- a format version and expiration timestamp;
- an HMAC signature. Local mode uses a fresh random key per server instance;
  Supabase mode uses the configured session secret.

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

The local sign-in page requires a username or account ID and a password,
then creates a signed LiftTrax session. The account field defaults to the same
machine-local `lifttrax.cli.userId` / `LIFTTRAX_CLI_USER_ID` setting used by
operator commands. Signing in does not change the account's saved email. The old
`lifttrax.auth.localEmail` / `LIFTTRAX_AUTH_LOCAL_EMAIL` setting is no longer used.
A username is
resolved to the existing immutable auth ID
before the session is signed, so signing out and back in cannot create a second
empty identity merely because the friendly username was entered.

Choose **Create a local account** to register a username, password, and optional email.
Registration assigns a new immutable local identity and creates its account and
default lifter profile in one transaction. Duplicate or invalid usernames fail
without creating a partial account or signing into another user's account.
Unknown usernames on the sign-in form still fail; sign-in never creates accounts.
New accounts start with an empty lift list and history.

### Existing accounts and forgotten passwords

Existing accounts cannot sign in until the server owner sets a password. There
is no public username-only claim or reset route. From an interactive terminal
in the repository on the server, run:

```powershell
./set-local-password.bat --user <username-or-account-id>
```

This builds the application, reads the existing local configuration (including
its ignored override), and asks twice for a hidden password. Passwords are never
command-line arguments. It requires an explicit existing account and preserves
its ID, lifts, history, and browser draft keys. The same command resets a forgotten
password. The Java entry point is `com.lifttrax.cli.SetLocalPasswordCli`; run it
with the installed distribution's `lib/*` classpath in an interactive terminal
on other platforms. Operator commands still rely on trusted database access.

Signed-in local users can change their own password in **Account**, providing
the current password and confirming the new one. Both resets and changes rotate
the credential version, so other sessions are rejected on their next request.
Concurrent password changes cannot overwrite a newer credential with an older
verified password. Supabase users continue managing credentials at their provider.

### Password and session protection

Passwords require at least one character, with no password-specific maximum
length or complexity rules. Spaces and Unicode are accepted, including a single
space. Passwords are not trimmed or silently truncated. Only salted PBKDF2-HMAC-SHA256 hashes are stored
(600,000 iterations, 16 random salt bytes, 256-bit output), following the
[OWASP password storage guidance](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html).
Missing accounts and wrong passwords return the same sign-in error. All password
operations use the existing CSRF boundary; signed-in changes also check account
scope. Password fields are never repopulated in error responses.

The server allows ten password-operation attempts per remote address per minute,
including sign-in, registration, and changes. It returns HTTP 429 with Retry-After
when throttled. The in-memory limiter has bounded storage and resets on restart;
it ignores forwarded address headers. A reverse proxy's users therefore share
its limit unless separate edge throttling/address handling is introduced.

Local sessions last eight hours and are invalidated on server restart. The old
password-free cookie format and shared development signing secret no longer
work. Local OAuth callback requests are rejected. Configure HTTPS and
`lifttrax.auth.secureCookies=true` for network deployments; passwords need
transport protection as well as hashing. Local mode is designed for a single
server instance. Account registration remains available to visitors.

The Postgres migration adds nullable password hashes and credential versions to
existing accounts without changing training data. All-user SQLite backups include
these fields for recovery; keep those backups private. Workout/catalog exports
do not include credentials.

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
