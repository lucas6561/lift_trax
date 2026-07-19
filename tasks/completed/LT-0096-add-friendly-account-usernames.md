---
id: LT-0096
title: Add friendly account usernames
status: done
track: platform
priority: high
effort: medium
created: 2026-07-19
updated: 2026-07-19
owner: unassigned
depends_on: [LT-0085, LT-0086, LT-0087]
---

# LT-0096: Add friendly account usernames

## Why

People should not need to remember or repeatedly enter a Supabase authentication
UUID to recognize their account or run a user-scoped operator command.

## Outcome

Each hosted account can choose a memorable, unique LiftTrax username while the
immutable authentication subject remains the authorization boundary. Signed-in
web requests continue selecting the current account automatically, and operator
commands can resolve their existing user option from either username or auth ID.

## Scope

- In scope: username persistence and validation, an authenticated account page,
  username display, username-or-auth-ID operator lookup, documentation, and tests.
- Out of scope: password authentication, using usernames as authorization keys,
  public profiles, coach discovery, and browser-based SQLite import uploads.

## Acceptance criteria

- [x] A signed-in user can set and change a unique, case-insensitive username.
- [x] Usernames have documented normalization and validation rules with clear UI
      errors for invalid or unavailable values.
- [x] The application continues to scope private data using the immutable auth
      subject rather than a mutable username.
- [x] Signed-in web requests select the session account without asking for an ID,
      and authenticated pages display the configured username.
- [x] User-scoped operator commands accept a configured username or auth ID and
      fail safely when the account cannot be resolved.
- [x] Local sign-out/sign-in resolves a username to the existing auth subject
      and defaults to the configured account instead of creating an empty one.
- [x] Focused account, persistence, and CLI tests pass, followed by
      `./gradlew.bat qualityGate`.

## Notes

Do not reuse `lifter_profiles.display_name`: a lifter name is not an account
identifier and does not need to be globally unique.

Implemented with Postgres migration `0002__account-usernames.sql`, the protected
`/account` route, username-aware operator resolution, username preservation in
SQLite operator snapshots, and focused web/persistence tests. Verification:
`./gradlew.bat qualityGate` passed on 2026-07-19.

Reopened after local-development sign-in treated a friendly username as a new
auth subject after logout, selecting an empty account. The login boundary must
resolve usernames to the existing immutable auth subject before signing the
session.

Fixed by resolving the local login identifier through the account provider and
prefilling the machine-local configured account. Username resolution takes
precedence over a colliding accidental auth ID, allowing the original account
to be recovered without data changes. Regression tests and
`./gradlew.bat qualityGate` passed on 2026-07-19; a live read-only catalog check
against the configured original account also confirmed its data remains present.
