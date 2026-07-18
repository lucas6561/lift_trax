# 0001: Keep LiftTrax local-first with Java and SQLite

Date: 2026-06-07

## Status

Superseded by ADR-0004

## Context

LiftTrax is an incremental Java port of an existing training log. The app
already needs several surfaces: command-line tools, a lightweight web UI,
SQLite persistence, schema-driven workout files, and workout-planning logic.

The near-term product is for local training use. It should work from a laptop or
phone on the same network without requiring hosted infrastructure, accounts, or
cloud synchronization before the core logging and planning workflows are solid.

## Decision

LiftTrax remains a local-first Java application backed by SQLite. The Java code
owns the CLI entry points, embedded web server, data access layer, and workout
planning logic. The repository's SQLite schema and migrations remain the durable
source of truth for local training data.

User-facing workflows should share Java domain and data-access code instead of
forking behavior between CLI and web paths. Schema assets for programs and
planned workouts stay as versioned files in the repository so generated or
imported workout data can be validated independently from any one UI.

## Consequences

- Local workflows can stay simple: open a SQLite database, run a CLI command, or
  start the embedded web UI.
- Database changes need explicit migrations, compatibility tests, and backup or
  restore expectations because the local file is user-owned data.
- Web UI features should avoid assuming a separate hosted API until the product
  needs one.
- Future mobile, multi-user, or distribution work should build from this
  local-first baseline instead of replacing it prematurely.
- Future database-splitting decisions, such as separating execution history from
  exercise catalog data, should be captured in their own ADRs.

## Alternatives considered

- Hosted service first: deferred because it would add accounts, deployment, and
  synchronization before the local training loop is complete.
- Desktop-only UI: deferred because the web UI already supports phone access on
  the local network, which matters during training.
- Replace SQLite with a server database: deferred because SQLite fits the
  current local data ownership model and keeps backups inspectable.
