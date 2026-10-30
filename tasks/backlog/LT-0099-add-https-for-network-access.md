---
id: LT-0099
title: Add HTTPS for network access without losing existing data
status: doing
track: platform
priority: high
effort: medium
created: 2026-10-27
updated: 2026-10-30
owner: codex
depends_on: [LT-0084, LT-0092, LT-0095]
---

# LT-0099: Add HTTPS for network access without losing existing data

## Why

Phone access to the local LiftTrax server currently uses HTTP, exposing passwords,
session cookies, and training data in transit. HTTPS also enables the existing
service worker's offline fallback when accessing the server over the network.

The switch must preserve the existing database and account ownership. Unfinished
workout drafts live in browser storage, which is separate for HTTP and HTTPS
origins, so those drafts need an explicit transition plan.

## Outcome

The existing Windows-hosted LiftTrax instance can be used from a phone and desktop
through a trusted HTTPS address, with the same accounts and Postgres data. The
operator has a documented setup, verification, cutover, and rollback procedure.
This task covers home-network access only. Away-from-home access is LT-0100.
Client devices open the address normally, without installing a custom CA,
certificate profile, client application, or per-device hostname configuration.

## Scope

- In scope: a reproducible reverse-proxy setup for the existing local server;
  a centrally resolved hostname and publicly trusted certificate; certificate renewal;
  secure cookies; HTTP redirects; private backend access; data-preserving cutover;
  draft handling; verification; and operator documentation.
- Out of scope: moving to a new database or hosting provider, changing account
  identities, native TLS/key management in Java, automatic cross-origin draft
  transfer, general offline browsing, and automatic background synchronization.

## Acceptance criteria

- [x] During setup and phone verification, the existing HTTP server on port 8080
      remains available and independently restartable. HTTPS uses its own private
      backend and does not reserve port 8080 or redirect the working address by
      default. Any later change to that address is an explicit, tested cutover.
- [x] A documented reverse-proxy configuration provides a stable HTTPS address
      usable from the Windows desktop and a phone on the local network without
      certificate warnings or custom certificate installation on client devices.
      Server/router setup, hostname resolution, certificate renewal, and
      startup/restart instructions are included.
- [x] The proxy forwards to the existing Java HTTP server over a private backend
      connection. Direct unencrypted network access to the backend is prevented;
      browser HTTP requests redirect to HTTPS after cutover.
- [x] The HTTPS deployment uses the existing configured Postgres database and
      account identities. Setup does not create an empty replacement database,
      re-import records, reset credentials, or run destructive data operations.
- [x] Before cutover, create and validate an all-user backup using the existing
      backup tooling. Record a baseline of account/catalog/history/workout data;
      compare stable record IDs, ownership, counts, and representative contents
      after switching, with any deliberate smoke-test writes accounted for.
- [x] A cutover checklist requires checking each previously used browser/profile
      at its original HTTP address for unfinished drafts. Finish and confirm
      submission of pending work before redirecting that address; if work must
      remain unfinished, defer cutover until an explicit preservation/recovery
      procedure is verified. Do not clear browser storage or claim drafts migrate
      automatically. Saved database history remains available through HTTPS.
- [x] Enable secure session and CSRF cookies for HTTPS. Verify sign-in, sign-out,
      CSRF-protected submissions, account isolation, history, and planned-workout
      draft resume/submission through the proxy. Document expected reauthentication
      after restarting the local server.
- [x] Where Supabase authentication is used, configure and verify the HTTPS
      callback URL and provider allowlist. Local-password mode continues working.
- [x] Verify the existing manifest, service worker registration, and offline
      fallback through HTTPS with no mixed-content failures. Describe the current
      offline limits without claiming full offline operation.
- [x] Review proxy effects on password-operation rate limiting, which currently
      uses the direct peer address. Document any shared proxy-address limit and
      mitigation; do not blindly trust client-supplied forwarded address headers.
- [x] A rollback procedure restores the previous launch/access configuration
      against the same database without dropping HTTPS-era writes. Explain how
      to resume or preserve drafts created at the HTTPS origin before rollback.
- [ ] Verify desktop and physical-phone access, including certificate trust,
      redirects, login, and a planned-workout save. If no physical phone is
      available, obtain explicit approval for substitute coverage and record the
      waiver; emulation alone does not verify phone certificate trust.
- [x] Add focused automated coverage for application changes where needed and run
      `./gradlew.bat qualityGate` before implementation is considered complete.
      Update README and authentication/deployment documentation with the setup
      and cutover procedure.

## Notes

- The user explicitly requires preservation of existing database information.
- On 2026-10-29 the user selected home-only access for now and requested LT-0100
  for future access away from home. Resume HTTPS validation alongside the normal
  HTTP server; keep the user's working software available throughout testing.
- Reuse the backup/recovery work in `LT-0091` and `LT-0092`, and the draft behavior
  in `LT-0095`; this task does not replace those features.
- Render already handles public HTTPS at its edge. Keep the Java HTTP backend
  compatible with the documented Render deployment rather than adding redundant
  native TLS there.
- Relevant documentation: `docs/account-authentication.md`,
  `docs/account-data-export-and-recovery.md`, and
  `docs/hosted-deployment-pipeline.md`.

2026-10-28 implementation and verification:

- Added a pinned Caddy 2.11.7 Windows x64 install with the official SHA-512 archive
  checksum, an ignored machine-specific JSON config, and `https.bat` /
  `scripts/https.ps1` for private preview, network startup, status, stop, and
  public-certificate export. The launcher preserves the existing app config,
  checks occupied ports, tracks actual JVM/proxy process identities, and retains
  CA storage across restarts. Java stays on loopback; HTTP ports serve redirects.
- Added `CompareSnapshotsCli` / `PostgresSnapshotComparison` to validate and
  compare complete all-user snapshots, including content/ownership/identity
  changes that leave row counts unchanged. Snapshot metadata times are excluded.
  Startup takes and compares snapshots before reporting success.
- The user confirmed that all unfinished workouts were submitted and agreed to
  perform the physical-phone verification. No device-test waiver was requested
  or granted.
- The configured local instance was switched to network HTTPS. TLS chain/name
  verification using the exported CA succeeded, as did login-page, health, and
  manifest requests. Both HTTP ports returned 308 redirects preserving path and
  query. Listener inspection confirmed network-facing proxy sockets and a
  loopback-only Java backend.
- The first pre-change all-user snapshot and the final post-start snapshot
  contain identical application rows, IDs, ownership, and contents. No synthetic
  production workouts were submitted. Backup paths and process state are in
  ignored `data/https/`, rather than tracked source.
- The real-Caddy integration test ran against isolated H2 test data with its own
  CA. It verified untrusted-certificate rejection, trusted HTTPS, both redirects,
  secure cookies, sign-in/out, CSRF rejection, account-export isolation, manifest
  and offline asset delivery, and a real planned execution saved through HTTPS.
  Existing draft recovery and auth tests also passed in the full suite.
- `./gradlew.bat spotlessApply qualityGate` passed: 341 tests, zero failures or
  errors, two unrelated existing skips; the new HTTPS integration test was not
  skipped. PMD, formatting, and the 90 percent instruction-coverage gate passed.
  Both PowerShell helpers parse successfully. Preview Stop/Status and rejection
  of missing draft acknowledgement / occupied original-server port were checked.
- Added `docs/local-https.md`, ADR-0006, README/auth/deployment links, and an
  administrator-only firewall helper scoped to Caddy TCP 443/80/8080, Private
  networks, and the local subnet. Local password mode is deployed; Supabase
  callback/allowlist instructions are documented for deployments using it.
- Windows certificate trust was approved by the user and verified in the current
  user's root store. Normal Windows HTTPS validation returned HTTP 200 without a
  custom CA override, and desktop Chrome opened the HTTPS Sign In page without
  a certificate-warning interstitial.
- Remaining acceptance work: complete administrator firewall setup (the current home connection is Public and
  Windows blocks Caddy there), enroll the public root on the physical phone,
  verify login/save/redirects without certificate warnings, and check actual
  browser service-worker registration/offline fallback. The supplied helper can
  reclassify the sole current home connection only with the explicit
  `-TrustCurrentNetwork` option. Keep this card in `doing` until these checks pass.

2026-10-28 client-setup scope correction:

- The user wants to avoid setup on client devices. The target deployment must
  use a publicly trusted certificate and centrally managed name resolution;
  installing the local CA on each phone is no longer the completion path.
- The current internal-CA implementation and its enrollment instructions remain
  interim work, not fulfillment of this updated acceptance criterion. Rework
  certificate issuance/renewal after identifying a domain or subdomain the
  operator controls. DNS-based validation can keep the app private to the LAN.
- Preserve the existing Postgres database, accounts, backup comparison, and
  private Java backend while changing the certificate strategy. Do not publish
  the local service to the internet or change hosting as an implicit fallback.

2026-10-29 public-certificate implementation:

- The user supplied a registered DuckDNS hostname and saved its account token
  in the ignored PC-local `config/lifttrax-duckdns.override.json`. No token was
  copied into tracked config, chat, process state, or OS process command lines.
- Replaced network-mode internal TLS with DuckDNS DNS-01 ACME issuance. The
  internal CA is confined to isolated developer preview/testing. Network clients
  require no custom CA, certificate profile, client application, or hosts entry.
- Added a pinned custom build: Caddy 2.11.7, DuckDNS provider 0.5.0, xcaddy 0.4.7,
  and checksum-verified official Go 1.27.3. The installed binary's module versions
  and hash receipt were verified. A first build hit disk-space pressure; removing
  only setup-owned redundant archives allowed the cached retry to succeed.
  Compiler/module caches were then removed from the workspace tools directory;
  the self-contained runtime and verification receipt remain.
- Added Provision mode to obtain and validate the public certificate on a
  temporary loopback listener while leaving the existing app/backend and database
  untouched. It stops its temporary proxy after success and retains public
  certificate state for network mode and automatic renewal.
- DNS configuration explicitly publishes the PC's assigned private LAN IPv4
  address and clears stale A/AAAA values, rather than allowing DuckDNS to
  auto-detect and publish the WAN address. Ordinary Windows DNS resolution now
  returns the private address. No router port forwarding was configured.
- Public certificate issuance succeeded through Let's Encrypt. The certificate
  matches the selected hostname; normal Windows chain validation succeeds through
  the public ISRG chain, independently of the earlier Caddy local root.
- Both actual Caddy configurations adapt and validate with the installed provider.
  Offline PowerShell checks cover rejected public/loopback/invalid addresses,
  explicit LAN-address publication, stale-address clearing, DuckDNS string and
  byte-array response handling, and credential-safe network/rejection errors.
- Updated the README, local HTTPS runbook, authentication guide, version manifest,
  firewall helper, and ADR-0007. The firewall helper targets the new custom binary
  and retains the Private-network/local-subnet-only scope.
- Final `./gradlew.bat spotlessApply qualityGate` passed after the public-provider
  changes: 341 tests, zero failures/errors, two existing skips. The real custom
  Caddy integration test ran and passed. PowerShell syntax, offline behavior,
  real Caddy configuration validation, installed-binary checksum reuse, ignored
  credential/certificate paths, and new-source whitespace checks also passed.
- Remaining before final cutover: user confirmation that any current old-origin
  workout drafts are submitted and approval for the one-time administrator
  network/firewall setup. The old managed app address is still running. After
  approval, switch against the same database with fresh before/after snapshots,
  verify the public address in an ordinary browser, then complete the physical
  phone and service-worker/offline checks. Keep this card in `doing` until those
  checks pass; no physical-device waiver has been granted.

2026-10-29 approved address switch and desktop verification:

- The user confirmed that old-origin workouts were submitted and explicitly
  approved opening the administrator setup, marking the home connection Private,
  and applying the scoped firewall rule.
- Switched the managed pair to the registered DuckDNS HTTPS address using the
  existing application/Postgres configuration. Fresh validated all-user snapshots
  immediately before and after startup contain identical application rows,
  contents, IDs, and ownership. No synthetic production training data was written.
- Ordinary Windows HTTPS requests to health and sign-in returned HTTP 200, and
  desktop Chrome displayed Sign In without certificate warnings. Both HTTP ports,
  including the old HTTP hostname, redirect to the canonical HTTPS address with
  the same path/query. Socket inspection confirms only the proxy faces the LAN;
  Java still listens exclusively on loopback.
- Verified actual service-worker/offline behavior in Chrome by pausing only the
  proxy for 30 seconds with automatic restoration. Reloading displayed the cached
  LiftTrax Offline page at the same HTTPS origin. After proxy restoration, a
  reload displayed Sign In again. Backend/database service continued running;
  the managed proxy process record was updated after restoration.
- The user approved the Windows elevation prompt. Live verification confirms
  the home connection is now Private and `LiftTrax-LocalHttps` is enabled with
  Allow, TCP 443/80/8080, the installed custom Caddy executable, Private profile,
  and LocalSubnet remote-address scope. No backend port was opened. Ordinary
  trusted HTTPS still returns HTTP 200 and both managed processes are healthy.
- The remaining acceptance check is physical-phone access on home Wi-Fi: normal
  certificate trust, sign-in, existing history, redirects, and a planned-workout
  save. The user has been asked to report these results. This card remains
  `doing` until that check passes; no physical-device waiver has been granted.

2026-10-29 home-only scope and parallel operation:

- The user chose home-network-only access for the current work. LT-0100 records
  future secure access away from home and depends on this card.
- Removed port 8080 from the network proxy configuration and occupied-port
  checks. HTTPS owns ports 443/80 with a separate secure loopback backend on
  18080. The existing HTTP app keeps its own process, address, and port 8080.
- Starting parallel HTTPS no longer requires a draft-cutover flag because it
  does not replace the original origin or move the user's logging workflow.
  Finishing/preserving old-origin drafts is still required before the user
  switches workout logging to the new origin.
- Added a real-Caddy configuration check rejecting any network-mode listener on
  8080. Settings checks, both Caddy configuration validations, script syntax,
  `qualityGate`, `installDist`, and whitespace checks passed.
- Left the already-running original HTTP process untouched. Live verification
  before and after HTTPS startup confirmed the same original process identity,
  HTTP login status 200 without redirects, and normal trusted HTTPS login status
  200. Listener inspection confirms independent HTTP/proxy/backend processes.
- Fresh validated all-user snapshots before/after parallel HTTPS startup match
  completely. The saved local credentials remain ignored, and existing Git
  stashes and unrelated exports were preserved.
- Both addresses are available on the home network. Keep the normal address
  available while physical-phone login/history/save checks are pending; this
  card remains `doing` until that verification passes.

2026-10-29 phone connectivity diagnosis and firewall repair:

- The user reported that the normal HTTP app works from the phone on home Wi-Fi,
  but the DuckDNS HTTPS address times out. Router DNS and two public resolvers
  all returned the correct private PC address; server listeners and the scoped
  allow rule were present.
- Found two enabled, explicit Private-network Block rules attached to the exact
  installed Caddy executable. They override its allow rule, explaining the
  difference between successful on-PC checks and failed inbound phone access.
  Also found broader duplicate Public-network Allow rules for the same binary.
- Updated the administrator helper to save prior Caddy rule metadata, disable
  broader duplicate inbound allows, reconcile conflicting Private/Any inbound
  blocks for this exact executable, and retain only the canonical Private /
  LocalSubnet / TCP 80+443 allow. Other applications and the existing HTTP
  server's rules are unchanged. The old, unused Caddy port-8080 exception is
  removed from the canonical rule.
- Applied the repair through Windows elevation. Verification found zero active
  conflicting Private blocks and exactly one enabled inbound allow for this
  Caddy executable, with the intended ports and home-subnet scope. Both HTTP and
  HTTPS login pages still return 200; no server restart or database change was
  needed. A prior-rule audit is retained under ignored `data/https/`.
- Asked the user to retry HTTPS on the physical phone. Keep the card open until
  phone access and the planned-workout save are confirmed.

2026-10-29 physical-phone connection confirmed:

- After the firewall repair, the user confirmed that the HTTPS address opens
  on the physical phone on home Wi-Fi and that sign-in and existing history work.
- The remaining functional acceptance check is a planned-workout save through
  HTTPS on the phone. Keep LT-0099 in `doing` until that result is reported.
- The original HTTP app remains independently available during verification;
  away-from-home access stays deferred to LT-0100.

2026-10-30 restart recovery and publication review:

- After the PC restarted, both managed HTTPS processes were stopped. DuckDNS
  still resolved to the correct assigned private LAN address. Automatic Windows
  startup is not part of this implementation.
- Cleared the stale managed-process record and restarted network HTTPS against
  the existing Postgres configuration and certificate storage. Validated all-user
  snapshots taken immediately before and after startup contained identical
  application rows, IDs, and ownership. Trusted HTTPS health and sign-in returned
  200, and the independent HTTP service remained available.
- The desktop browser retained the offline fallback from the outage. Reloading
  the existing tab restored Sign In without clearing browser storage or drafts;
  the user reported that it seems to work. The runbook now gives the complete
  post-restart sequence and browser recovery step.
- Publication retains the home-only scope and the LT-0100 remote-access backlog
  item. Physical-phone sign-in/history are confirmed; planned-workout save on
  that phone remains unverified, so this card stays in `doing`.
