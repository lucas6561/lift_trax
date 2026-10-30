# Architecture decision records

This folder keeps short architecture decision records for choices that should
stay visible as LiftTrax grows.

Use [TEMPLATE.md](TEMPLATE.md) for new records. Keep records concise, dated, and
focused on one decision. If a later decision changes an earlier one, add a new
record and update the old record's status instead of rewriting history.

## Records

- [0001: Keep LiftTrax local-first with Java and SQLite](0001-local-first-java-sqlite.md)
- [0002: Move toward a hosted web app with managed platform services](0002-hosted-web-pwa-managed-platform.md)
- [0003: Use Supabase and Render for the first hosted beta](0003-hosted-auth-data-platform.md)
- [0004: Use Postgres as the sole runtime database](0004-postgres-only-runtime.md)
- [0005: Separate catalog and performed-history lifecycles in Postgres](0005-catalog-history-lifecycle.md)
- [0006: Terminate local HTTPS with Caddy](0006-local-https-reverse-proxy.md)
- [0007: Use DuckDNS and public certificates without client enrollment](0007-public-https-with-duckdns.md)
