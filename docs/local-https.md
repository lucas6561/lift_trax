# Local HTTPS without client-device setup

Network mode uses your DuckDNS hostname and a publicly trusted ACME certificate.
Phones and browsers open the address normally: no custom CA installation,
certificate profile, client app, or per-device hostname entry is needed. Java
continues using the existing Postgres database and account identities.

The app stays on the home network. DuckDNS publishes the PC's **private LAN IPv4**
address, and DNS-based certificate verification needs no inbound internet ports.
Do not configure router port forwarding. The hostname works at home; this setup
does not provide remote access from mobile data or another network.
Away-from-home access is separate future work in LT-0100.

The original HTTP app on port 8080 stays independent during setup and phone
verification. HTTPS uses ports 443/80 and its own private backend on 18080;
starting or stopping the HTTPS pair does not stop or reserve the original server.

## One-time setup on the Windows PC

Prerequisites: Windows x64, PowerShell 7 (`pwsh`), Java on PATH, the existing
Gradle installation, a working application configuration, and a DuckDNS account
with a registered hostname. Run normal server commands as the same Windows user.

1. Copy `config/lifttrax-https.example.json` to
   `config/lifttrax-https.override.json`. Set:
   - `Hostname`: the registered name, such as `your-hostname.duckdns.org`.
   - `AppConfig`: the existing app config, normally
     `config/lifttrax-local.properties`. Keep its ignored JDBC override/include
     chain. Do not copy credentials, change account IDs, or create another database.
   - `LanAddress`: the PC's private IPv4 address on the home network. Reserve this
     address in the router's DHCP settings so it remains stable. The launcher
     rejects public/loopback addresses and addresses not currently assigned to
     the PC.
2. Copy `config/lifttrax-duckdns.example.json` to
   `config/lifttrax-duckdns.override.json`. Save the account's DuckDNS token in
   its `Token` field. Both overrides are ignored by Git. Keep the token on the
   PC, outside chat, source control, screenshots, and shared logs. Caddy needs
   the token while running to verify ownership again at certificate renewal.
3. Install the pinned Caddy build with the DuckDNS provider:

   ```powershell
   .\https.bat -Action Install
   ```

   Versions are recorded in `config/https/toolchain.json`. The installer verifies
   the official Go SDK archive checksum, uses pinned xcaddy/Caddy/provider
   versions with Go module checksum verification, and records the resulting
   binary hash and module build information in ignored `data/https/tools/`.
   It does not install Go system-wide. The first build can take several minutes.
4. Obtain and verify the public certificate before changing the running app:

   ```powershell
   .\https.bat -Action Provision
   ```

   This updates only the selected DuckDNS hostname to `LanAddress`, clearing old
   A/AAAA values first so a stale public IPv6 record is not retained. It starts a
   temporary loopback-only Caddy instance and verifies the issued certificate
   using normal Windows trust. It does not stop the existing server, start Java,
   or write to the database. A 502 response is acceptable during this step if no
   backend is running: certificate validation still succeeds. The temporary
   process is stopped after verification; certificates remain for network mode.
   Allow up to ten minutes for DNS propagation and issuance.
5. Keep the existing HTTP app running normally on port 8080. Stop only an old
   managed HTTPS pair with `https.bat -Action Stop` if one is running, then start:

   ```powershell
   .\https.bat -Action Start -Mode Network
   .\https.bat -Action Status
   ```

   The launcher refuses occupied HTTPS/backend ports; port 8080 is deliberately
   left to the normal app. Use the normal app throughout testing. Finish old-origin
   drafts before moving your workout logging to the new origin, not merely to
   start this parallel verification server.
6. In an **administrator PowerShell 7** window, allow the installed Caddy binary
   through the Windows firewall once:

   ```powershell
   .\scripts\enable-https-firewall.ps1
   ```

   The rule allows Caddy TCP 443/80 from the local subnet on **Private**
   networks. It does not open Java port 18080 or add a Public-network rule. If
   Windows labels the sole current trusted home connection Public, explicitly
   opt in to reclassifying it with:

   ```powershell
   .\scripts\enable-https-firewall.ps1 -TrustCurrentNetwork
   ```

   Private classification also enables other Windows rules scoped to Private
   networks. Use it only for a trusted home connection. With multiple active
   connections, select the correct home network in Windows Settings instead.
   The helper saves the previous rules for this exact Caddy executable, disables
   conflicting Private-network blocks left by Windows app prompts, and disables
   broader duplicate Caddy allows. Explicit blocks otherwise override the scoped
   allow rule. It leaves other applications' firewall rules unchanged.
7. On the PC and a phone connected to home Wi-Fi, open `https://<Hostname>` and
   verify normal sign-in without certificate warnings. Update bookmarks to this
   address only after phone checks pass. HTTP requests on port 80 redirect to
   the configured HTTPS name with the same path/query. The original port 8080
   continues serving the normal app; it does not redirect to the experimental URL.

## Name resolution and client access

Normal home-network DNS must return the PC's configured private IPv4 address for
its DuckDNS name. Windows/browser DNS caching can briefly retain the previous
public address. Verify normal name resolution after the change; do not use a
hosts-file entry or custom client certificate as a shortcut.

Some routers block public DNS names resolving to private addresses as DNS
rebinding protection. If the hostname resolves through a public resolver but
not the router, allow **only this hostname** in the router's protection settings,
or add a router-local DNS override for it. Keep general rebinding protection
on. A router change is central setup, not a setting on each client device.
Client-managed secure DNS, VPNs, or an isolated guest Wi-Fi can also change DNS
or local reachability; verify the actual phone/browser before declaring success.

A new address is a new browser origin. The database is shared, but cookies and
unfinished drafts do not move between the previous HTTP/local-HTTPS address and
the DuckDNS HTTPS address. Local authentication also resets sessions when the
backend restarts, so sign in again with the existing account and password.

## Data-preserving cutover

Before changing the usual address:

1. On every previously used device/browser profile, open each old origin,
   including its hostname, scheme, and port. Check for unfinished workouts.
   Resume and submit them, then confirm completed history is present. If work
   must remain unfinished, defer cutover until a preservation/recovery process
   has been verified. Do not clear browser storage or assume drafts migrate.
2. The network launcher builds the app and takes validated all-user snapshots
   immediately before and after startup using the **same** configured Postgres
   connection as the server. A read-only comparison checks every application row,
   including IDs, ownership, password records, history, saved workouts, and
   submission receipts. Snapshot creation metadata is excluded from comparison.
3. A mismatch aborts startup and retains snapshots for investigation. Legitimate
   concurrent writes can also cause a mismatch; avoid user writes during this
   short window. Never dismiss a difference just because row counts match.
4. Verify the existing catalog/history/workouts through the new HTTPS address,
   account isolation, sign-in/out, and one deliberate planned-workout save.
   Account for deliberate writes in later snapshot comparisons.
5. Verify a draft reload/Resume on the final origin and finish it or explicitly
   discard a synthetic test draft. Check service-worker registration and its
   offline fallback. This does not add offline history browsing or background
   synchronization.

Snapshots, CA/certificate state, logs, process records, and tools stay under
ignored `data/https/`. Snapshots include all users' private application data and
password hashes; keep them in operator-only storage. Follow
`account-data-export-and-recovery.md` for protected off-provider copies.

For another comparison of validated snapshots:

```powershell
java -cp "build/install/lift-trax-java/lib/*" com.lifttrax.cli.CompareSnapshotsCli "<before.db>" "<after.db>"
```

This connects only to the snapshot files and prints no row contents. It fails
on changed records even when counts match; it does not merge or restore data.
Starting a reviewed build can apply normal pending migrations, so retain the
pre-start snapshot. A transport rollback does not reverse schema migrations.

## Operation, renewal, and rollback

Use `https.bat -Action Status` and `https.bat -Action Stop`. The launcher records
actual JVM/proxy executable paths, IDs, and start times, and stops only matching
managed processes. Startup failure cleans up its children and retains snapshots
and certificate files.

Restart the HTTPS application from an ordinary Windows terminal with:

```powershell
.\https.bat -Action Restart
```

Restart pauses automatic recovery, rebuilds the installed application, restarts
the managed backend/proxy with the same mode, validates database snapshots, then
resumes automatic recovery even if startup fails. It does not stop port 8080.
Use `-SkipBuild -SkipDnsUpdate` to restart the current build and DNS mapping.
The batch launcher uses Windows PowerShell to locate PowerShell 7 through PATH,
the standard installation directory, the saved runtime path, or this checkout's
registered startup task. PowerShell 7 need not be on the interactive user's PATH.
The launcher sets RemoteSigned only for its child shells, without changing the
machine or user's saved execution policy; organizational policy still applies.

For normal home use, install the current user's automatic startup task once:

```powershell
pwsh -NoProfile -File scripts/https-startup.ps1 -Action Install
pwsh -NoProfile -File scripts/https-startup.ps1 -Action Status
```

The `LiftTrax Home HTTPS` task runs hidden with ordinary user permissions after
that user signs into Windows, without storing a Windows password. It monitors
the two managed processes every 30 seconds, removes stale reboot records, and
retries failed starts while the network/database comes up. A healthy pair and
developer preview are left alone. It uses the existing installed build and DNS
mapping, so an internet outage does not itself block local startup. LAN address
validation, validated before/after backups, and snapshot comparison still run.
The PC must be awake, the user signed in, and clients on the home network.
This is not a service that runs before Windows sign-in.

Before intentionally stopping HTTPS, remove automatic recovery first:

```powershell
pwsh -NoProfile -File scripts/https-startup.ps1 -Action Uninstall
```

Uninstall removes only this checkout's task and retains data and certificates.
Then stop/start manually as needed:

```powershell
.\https.bat -Action Status
.\https.bat -Action Stop
.\https.bat -Action Start -Mode Network
```

Stop checks recorded process identities before stopping anything. After a reboot,
it removes the stale record while retaining the database, snapshots, and
certificates. It does not stop the independent HTTP server on port 8080.
Once startup succeeds, refresh any browser tab still showing LiftTrax Offline.
The offline page stays visible until navigation or reload; do not clear site
storage, which may contain unfinished workout drafts. Sign in again if prompted.

Caddy renews public certificates automatically while running. Keep the DuckDNS
token and `data/https/public-pki/` available under the same Windows account.
Outbound internet/DNS access to the certificate authority and DuckDNS is needed
for issuance and renewal. If the PC's LAN address changes, update `LanAddress`
and restart after resolving unfinished drafts; prefer a DHCP reservation.

Logs are private `data/https/backend.*.log`, `proxy.*.log`, and `provision.*.log`.
Automatic recovery writes private `data/https/startup.log`.
No request-access log is enabled. Provider errors can include request details;
do not share raw provider logs without removing the DuckDNS token. Do not serve
the repository, credentials, backups, or certificate storage as static files.

The Java backend stays on `127.0.0.1:18080`. Password operations retain the
existing limit of ten attempts per minute per direct peer: users behind Caddy
share the loopback bucket. Respect HTTP 429 / Retry-After. Arbitrary forwarded
address headers remain untrusted. Broader exposure needs separate rate-limit
work; this task does not expose the server to the internet.

For Supabase mode, retain the existing hosted app config and add the exact
canonical HTTPS callback to the provider allowlist. The launcher uses an explicit
`:443/auth/callback` in network mode. Verify provider login before opening that
deployment. Local-password mode does not use OAuth. Render continues terminating
HTTPS at its own edge and does not need this local launcher.

To roll back access, first finish and confirm submission of drafts at the **new
HTTPS origin** on every device/profile. Stop the managed pair and restart the
previous reviewed launch/config against the **same Postgres database**. Do not
restore an older snapshot for a transport rollback: that would discard valid
HTTPS-era writes. Sign in again and verify recent history. Keep the new origin
available until any unfinished new-origin work is safely resolved. No HSTS header
is set by this local proxy to deliberately obstruct access rollback.

## Private developer preview and earlier local CA

`https.bat -Action Start -Mode Preview` is an isolated loopback-only developer
preview on `lifttrax-preview.localhost:8443`. It deliberately uses a test local
CA and is not the phone deployment path. Automated proxy tests use an isolated
CA and isolated H2 data; no production workouts are created by those tests.
`https.bat -Action Certificate` now explains that network clients need no custom
certificate installation.

If you installed the earlier Caddy local root, it is not needed for the new
public certificate. After the new setup is verified and all old-origin drafts
are resolved, remove only that specific root from the devices where it was
installed. Preserve old configuration/certificate state until the rollback and
draft requirements are satisfied; do not remove unrelated trusted roots.

## Verification commands and sources

```powershell
.\scripts\test-https-settings.ps1
.\scripts\test-https-config.ps1
.\gradlew.bat qualityGate
```

The settings checks make no network/database changes. The Caddy integration test
runs when the installed binary is available; it never replaces a physical-phone
check. Record an explicit waiver if alternative device coverage is approved.

- [DuckDNS API and DNS ownership verification](https://www.duckdns.org/spec.jsp)
- [Caddy DuckDNS module](https://github.com/caddy-dns/duckdns)
- [Caddy DNS-based HTTPS validation](https://caddyserver.com/docs/automatic-https)
- [Pinned custom Caddy builds](https://caddyserver.com/docs/build)
- [Browser storage is separate for HTTP and HTTPS](https://developer.mozilla.org/en-US/docs/Web/API/Window/localStorage)
