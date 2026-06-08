# 0002: Move toward a hosted web app with managed platform services

Date: 2026-06-08

## Status

Accepted

## Context

LiftTrax currently works as a local-first Java application with an embedded web
server and SQLite database. That remains useful for development and single-user
local training, but it does not satisfy the next product goal: a browser-based
app reachable from anywhere without exposing a local machine.

The current web server is intentionally lightweight and local. Before public
hosting, the app needs user identity, authorization, request hardening, durable
hosted storage, deployment environments, backups, and a migration path for
existing local training data.

## Decision

LiftTrax will move toward a hosted, responsive web application with a
PWA-capable browser experience. The near-term target is not an app-store mobile
app and not a public VM running the current local SQLite server as-is.

The hosted product should use managed platform services for identity and durable
data storage. Provider selection remains a follow-up task, but the architecture
must support:

- desktop and phone browser use through the same web product;
- account-based authentication and user-scoped authorization;
- managed database storage instead of relying on local filesystem persistence;
- export, import, and backup paths for user-owned training data;
- a documented offline boundary, where offline use is limited until conflict
  handling and sync semantics are explicit;
- later PWA installability without requiring native app-store packaging.

The existing local Java and SQLite implementation remains the baseline for local
development and data-model learning while the hosted architecture is introduced
incrementally.

## Consequences

- The product path becomes web-first: browser and phone usability remain central.
- Public hosting cannot ship before authentication, authorization, CSRF
  protection, secure sessions, and route-level hardening are implemented.
- SQLite remains useful locally, but hosted persistence needs an explicit managed
  database strategy and migration design.
- Google Drive is better suited to backup, export, or user-selected file storage
  than to the live transactional database.
- Provider selection should compare managed Postgres-style services,
  SQLite-compatible hosted options, and serverless edge databases against
  security, migration, cost, and implementation complexity.
- Native app packaging is deferred until the hosted browser app proves that a
  browser/PWA surface cannot meet training needs.

## Alternatives considered

- Expose the current local server directly: rejected because the current app is
  local-first and lacks the security layers required for a public URL.
- Host the current app on a free PaaS with local SQLite: rejected because common
  free tiers have ephemeral filesystems or no persistent volumes.
- Use Google Drive as the live database: rejected for the first hosted product
  because Drive is file storage, not a transactional multi-user database.
- Build a native mobile app first: deferred because the browser UI already serves
  desktop and phone use, and native packaging would add platform work before the
  hosted data and security model is settled.
