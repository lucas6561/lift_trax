---
id: LT-0106
title: Make HTTPS the only supported LAN entry point
status: ready
track: platform
priority: high
effort: medium
created: 2026-10-31
updated: 2026-10-31
owner: unassigned
depends_on: [LT-0099]
---

# LT-0106: Make HTTPS the only supported LAN entry point

## Why

The normal local configuration binds the Java HTTP server to every network
interface. When that process is started directly, port 8080 can expose login,
session, and training traffic to the home network without transport encryption.
Trusted HTTPS already exists and should be the only supported phone entry point.

## Outcome

Phone and LAN clients can reach LiftTrax only through the trusted Caddy HTTPS
address. Direct Java HTTP remains available on loopback for desktop operation,
development, and proxying, but is not reachable from another device.

## Scope

- In scope: local bind defaults; HTTPS launcher and backend behavior; listener
  and firewall verification; secure-cookie expectations; rollback; draft and
  data-preservation checks; automated and physical-phone verification.
- Out of scope: away-from-home access, router forwarding, database migration,
  changing account identities, or removing loopback development access.

## Acceptance criteria

- [ ] The standard local Java server binds to loopback by default and does not
      listen on a LAN address or wildcard interface.
- [ ] Network HTTPS terminates on Caddy ports 443/80 and proxies only to a
      loopback Java backend; Postgres is never exposed as part of this change.
- [ ] No enabled Windows Firewall rule exposes direct Java ports 8080 or 18080
      to the LAN, Public networks, or the internet.
- [ ] Starting the supported home-network stack cannot silently fall back to
      unencrypted LAN HTTP when certificate, proxy, or backend startup fails.
- [ ] HTTPS sessions and CSRF cookies remain `Secure`, `HttpOnly` where
      applicable, and retain their existing same-site behavior.
- [ ] The rollout reuses the canonical Postgres database and compares validated
      all-user snapshots before and after the change without altering IDs,
      ownership, catalogs, history, workouts, or credentials.
- [ ] Existing HTTP-origin and HTTPS-origin browser drafts are handled as
      separate storage. The procedure never clears browser storage or claims
      that drafts migrate automatically.
- [ ] Rollback restores a working loopback/local setup against the same database
      without reopening insecure LAN HTTP.
- [ ] Focused tests cover bind defaults, rejected insecure network settings,
      listener topology, redirects, secure cookies, authentication, CSRF, and
      real reverse-proxy behavior.
- [ ] Desktop and physical-phone checks on home Wi-Fi verify trusted HTTPS,
      sign-in, existing history, and a planned-workout save. A separate test
      confirms direct port 8080 is unreachable from the phone.
- [ ] `./gradlew.bat qualityGate` passes.

## Notes

Keep home access available during the rollout by changing one path at a time.
This task changes network exposure, not application data. LT-0100 remains the
separate decision for access away from home.
