# 0007: Use DuckDNS and a publicly trusted certificate for local HTTPS

Date: 2026-10-29

## Status

Accepted; supersedes ADR-0006's client certificate enrollment decision.

## Context

The user requires normal phone/browser access without installing a custom root
certificate, certificate profile, client application, or per-device hostname
configuration. They supplied a registered DuckDNS hostname. The existing Windows
server and canonical Postgres database must remain in place and private to the LAN.

## Decision

Use a pinned Caddy build containing the DuckDNS provider. Obtain publicly trusted
ACME certificates through DNS ownership verification, with credentials stored in
an ignored PC-local file and passed only to the Caddy process environment. Caddy
performs renewal automatically while running; clients use their ordinary trust.

Publish the PC's configured private LAN IPv4 address through DuckDNS, explicitly
clearing stale A/AAAA values before replacing A. Reject non-private or unassigned
addresses. Use a router DHCP reservation, and if necessary a hostname-specific
router DNS/rebinding exception. Do not use router port forwarding as a fallback.

Provision and validate the public certificate on a temporary loopback listener
before changing the existing app. Reuse the private Java backend, all-user backup
comparison, existing app config, and origin-specific draft cutover/rollback rules.
Keep internal-CA HTTPS only for isolated development tests.

## Consequences

- Phones require no certificate installation. One-time DNS/token/firewall setup
  belongs to the server/router operator.
- Issuance and renewal depend on outbound connectivity to DuckDNS and the CA.
- The DuckDNS account token remains private on the PC; raw provider logs can
  contain sensitive request details and must not be shared unreviewed.
- Router DNS rebinding protection can require a specific-name exception. Verify
  actual client DNS and phone access rather than assuming central DNS works.
- The app remains home-network-only. Remote access or new hosting is separate work.

## Alternatives considered

- Per-device internal CA enrollment: rejected by the user.
- Buying a domain: unnecessary for the registered free DuckDNS hostname.
- Public HTTP validation/router port forwarding: unnecessary and expands exposure.
- Hosted replacement: changes the existing deployment and is outside this task.
