---
id: LT-0108
title: Enforce strong local account passwords
status: ready
track: platform
priority: high
effort: medium
created: 2026-10-31
updated: 2026-10-31
owner: unassigned
depends_on: [LT-0085, LT-0099]
---

# LT-0108: Enforce strong local account passwords

## Why

Local passwords are salted and hashed with PBKDF2-HMAC-SHA256, and password
operations are rate limited, but the application currently accepts a password
as short as one character. Local mode does not provide MFA, so a weak password
can undermine otherwise strong session and network protections.

## Outcome

New, changed, and operator-reset local passwords meet a modern length and
common-password policy. Existing users can transition safely without losing
their account identity, training data, drafts, or recovery path.

## Scope

- In scope: registration, password change, operator reset, validation messages,
  existing-account transition, common-password screening, session revocation,
  documentation, and security tests.
- Out of scope: storing plaintext passwords, mandatory composition rules,
  periodic password rotation, Supabase credential policy, or adding MFA.

## Acceptance criteria

- [ ] Local passwords require at least 15 characters while continuing to allow
      spaces, Unicode, pasting, password managers, and long passphrases.
- [ ] Passwords are not trimmed or silently truncated, and the supported maximum
      length is at least 64 characters with a documented denial-of-service bound.
- [ ] Registration, signed-in password changes, and `set-local-password` enforce
      the same policy before storing a new hash.
- [ ] Reject common or known-compromised passwords without sending plaintext or
      a complete reusable password hash to a third party.
- [ ] Password errors explain the policy without revealing whether a username,
      account, or prior password exists.
- [ ] Existing hashes are not guessed or weakened. The rollout provides an
      explicit, tested transition for existing accounts and a safe operator
      recovery path before any user is required to change a password.
- [ ] A successful password change or reset increments the credential version
      and revokes other sessions as it does today.
- [ ] Password throttling, CSRF protection, account scope, generic sign-in
      failures, and secure transport requirements remain intact.
- [ ] Focused tests cover boundary lengths, long passphrases, Unicode and spaces,
      common-password rejection, all three password entry paths, session
      revocation, and existing-account recovery.
- [ ] Authentication documentation is updated and `./gradlew.bat qualityGate`
      passes.

## Notes

The application cannot determine the original length of an existing password
from its hash. Design the transition around a verified password change or
operator reset, not destructive account recreation.
