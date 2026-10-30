# 0006: Terminate local HTTPS with Caddy

Date: 2026-10-28

## Status

Superseded by ADR-0007 for certificate trust and enrollment. The private Java
backend and data-preserving cutover decisions remain applicable.

## Context

Local phone access needs encrypted passwords/sessions and a secure browser
context for the existing service worker. Changing transport must preserve the
canonical Postgres database, accounts, and unfinished browser drafts. Render
already terminates public HTTPS separately.

## Decision

Use a pinned, checksum-verified Caddy Windows x64 binary with an internal CA for
the local instance. Keep Java HTTP on loopback and use runtime system-property
overrides for secure cookies, bind address, and the HTTPS callback. Reuse the
existing application config, including its ignored JDBC include/override chain.

Provide an isolated-hostname loopback preview and an explicit network cutover.
Compare validated all-user snapshots before and after every managed start.
Require the operator to resolve original-origin drafts before enabling redirects.
Install only the public CA certificate on client devices through a trusted path.

## Consequences

- Java does not manage TLS keys or certificates. Render remains compatible.
- Devices must trust this local CA and resolve the canonical hostname.
- CA storage is private operator state and must persist across restarts.
- The proxy shares the current password-attempt bucket; forwarded addresses
  remain untrusted. Broader exposure needs separate rate-limit work.
- Browser storage is not migrated across HTTP/HTTPS origins. Rollback changes
  access configuration against the same database, retaining HTTPS-era writes.

## Alternatives considered

- Native Java TLS: adds keystore/renewal management without helping Render.
- Public domain/certificate automation: useful later, but adds domain/DNS setup
  for a private LAN deployment.
- Self-signed leaf certificate: renewal and per-device trust are less convenient
  than enrolling a stable local root.
