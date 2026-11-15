---
id: LT-0106
title: Make HTTPS the only supported LAN entry point
status: done
track: platform
priority: high
effort: medium
created: 2026-10-31
updated: 2026-11-15
owner: codex
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

- [x] The standard local Java server binds to loopback by default and does not
      listen on a LAN address or wildcard interface.
- [x] Network HTTPS terminates on Caddy ports 443/80 and proxies only to a
      loopback Java backend; Postgres is never exposed as part of this change.
- [x] No enabled Windows Firewall rule exposes direct Java ports 8080 or 18080
      to the LAN, Public networks, or the internet.
- [x] Starting the supported home-network stack cannot silently fall back to
      unencrypted LAN HTTP when certificate, proxy, or backend startup fails.
- [x] HTTPS sessions and CSRF cookies remain `Secure`, `HttpOnly` where
      applicable, and retain their existing same-site behavior.
- [x] The rollout reuses the canonical Postgres database and compares validated
      all-user snapshots before and after the change without altering IDs,
      ownership, catalogs, history, workouts, or credentials.
- [x] Existing HTTP-origin and HTTPS-origin browser drafts are handled as
      separate storage. The procedure never clears browser storage or claims
      that drafts migrate automatically.
- [x] Rollback restores a working loopback/local setup against the same database
      without reopening insecure LAN HTTP.
- [x] Focused tests cover bind defaults, rejected insecure network settings,
      listener topology, redirects, secure cookies, authentication, CSRF, and
      real reverse-proxy behavior.
- [x] Desktop and physical-phone checks on home Wi-Fi verify trusted HTTPS,
      sign-in, existing history, and a planned-workout save. A separate test
      confirms direct port 8080 is unreachable from the phone.
- [x] `./gradlew.bat qualityGate` passes.

## Notes

Keep home access available during the rollout by changing one path at a time.
This task changes network exposure, not application data. LT-0100 remains the
separate decision for access away from home.

2026-11-15 implementation and host verification:

- The local web bind now defaults to `127.0.0.1`. Non-loopback binds fail closed
  unless a hosted edge config explicitly opts in with
  `lifttrax.web.allowNonLoopback=true`; Render and the hosted example retain that
  deliberate edge-facing configuration.
- Network HTTPS refuses a LAN-facing listener on direct port 8080, verifies the
  Java backend owns loopback-only port 18080, and verifies Caddy owns network
  ports 443/80 before publishing readiness. Failed startup stops processes and
  never falls back to direct LAN HTTP.
- The administrator firewall helper retained the Private/LocalSubnet Caddy allow
  on TCP 443/80 and added an all-profile inbound Java block on TCP 8080/18080.
  Its final audited rules target the same JDK and Caddy binaries recorded by the
  live managed pair.
- Restart created validated snapshots
  `before-https-20261115-143848Z.db` and
  `after-https-20261115-143903Z.db`; comparison found identical application rows,
  IDs, and ownership. The managed backend and proxy are both healthy. Listener
  inspection found Caddy on 80/443, Java only on `127.0.0.1:18080`, and nothing
  on 8080. Trusted HTTPS health returned 200, HTTP returned a path/query-preserving
  308, and the live CSRF cookie remained `Secure; HttpOnly; SameSite=Lax`.
- The full quality gate, offline PowerShell checks, both real Caddy config checks,
  real proxy integration, and isolated desktop/phone Chromium journeys passed.
  At that point, the only remaining acceptance step was the explicitly required
  physical-phone check on home Wi-Fi, including direct port 8080 rejection and a
  planned-workout save through HTTPS.

2026-11-15 physical-phone completion verification:

- The user confirmed trusted HTTPS sign-in, existing history, and a successful
  planned-workout save from the physical phone on home Wi-Fi.
- The user separately confirmed that direct HTTP at the PC's LAN address on port
  8080 is unreachable from the phone. HTTPS is therefore the only supported and
  reachable LiftTrax LAN entry point.
