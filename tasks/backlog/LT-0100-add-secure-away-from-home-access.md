---
id: LT-0100
title: Add secure access away from home
status: idea
track: distribution
priority: medium
effort: unknown
created: 2026-10-29
updated: 2026-10-29
owner: unassigned
depends_on: [LT-0099]
---

# LT-0100: Add secure access away from home

## Why

The home-network HTTPS address is unavailable on mobile data or another Wi-Fi
network. Users need a deliberate remote-access option if they want to log a
workout or read history away from home.

## Outcome

An authenticated user can use LiftTrax from outside the home network through a
documented, supported connection, while the existing database and reliable home
access remain intact.

## Scope

- In scope: compare remote-access approaches; choose and document the deployment,
  cost, client setup, authentication, certificate renewal, availability, backup,
  and rollback implications; implement the selected approach after review; and
  verify access on a physical phone outside the home network.
- Out of scope: implementation as part of LT-0099, silently opening router ports,
  exposing Postgres or an unauthenticated app, creating a second writable copy of
  training data, and unrelated offline synchronization.

## Acceptance criteria

- [ ] Document feasible approaches and their tradeoffs, including whether the
      home PC must stay awake, recurring costs, and any client installation.
- [ ] Prefer normal browser access without custom certificate enrollment or a
      client app. Any required exception is explained and approved before setup.
- [ ] Review the selected approach and public exposure/security implications
      before publishing the app or changing router/firewall access.
- [ ] Use trusted HTTPS, authenticated account-scoped access, appropriate
      password-operation rate limiting, and a private database connection.
- [ ] Preserve existing account identities, catalog, history, saved workouts,
      and submission receipts. Take and verify backups before any data move;
      define one canonical writable Postgres database.
- [ ] Keep normal home access working during rollout and document rollback that
      preserves writes made through remote access.
- [ ] Handle any new browser origin explicitly: finish or preserve unfinished
      drafts, update auth callbacks when relevant, and document reauthentication.
- [ ] From a physical phone on mobile data or another outside network, verify
      trusted HTTPS, sign-in, existing history, account isolation, and a planned
      workout save. Record actual coverage and any explicit waiver.
- [ ] Document startup/recovery, certificate renewal, secret storage, backup
      operation, service availability, and the supported home/away addresses.
- [ ] Run focused checks and `./gradlew.bat qualityGate` for application changes.

## Notes

- The user selected home-only access for the current work on 2026-10-29.
  This is future work after LT-0099 is reliable on home Wi-Fi.
- Publicly trusted HTTPS alone does not provide network reachability. The
  current DuckDNS record points to a private LAN address.
- Compare against the existing hosted deployment/authentication documentation
  before creating a separate hosting or database design.
