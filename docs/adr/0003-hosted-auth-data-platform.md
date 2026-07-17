# 0003: Use Supabase and Render for the first hosted beta

Date: 2026-06-14

## Status

Accepted

## Context

ADR-0002 chose a hosted, responsive web path with managed identity, durable
hosted storage, request hardening, backups, and migration work before public
use. The next decision is the first concrete stack so the hosted schema,
security baseline, auth implementation, and deployment pipeline can target real
interfaces instead of generic "hosted" abstractions.

The first hosted phase should preserve the existing Java backend long enough to
reuse current business logic and tests. A full serverless rewrite may still be
valuable later, but it is not the shortest path to a private hosted beta.

## Decision

Use Supabase for the first hosted auth and data platform:

- Supabase Auth is the managed identity provider.
- Supabase Postgres is the hosted primary data store.
- Supabase Row Level Security may provide defense in depth, but the Java server
  still owns route-level authorization checks.

Use Render as the first deployment target for the Java web service:

- The existing Java backend remains in the first hosted phase.
- Render runs the server process and stores deployment secrets outside source
  control.
- Supabase holds durable account data; Render must not rely on local filesystem
  persistence for training history.

Use local development equivalents that match the hosted boundaries:

- Continue supporting the local SQLite workflow while hosted persistence is
  behind an adapter.
- Add local Postgres-compatible tests for hosted persistence slices.
- Use a test auth context locally before wiring real Supabase callback flows.

Expected cost:

- Prototype and development can start on free tiers while usage fits provider
  limits.
- A private beta should budget for paid always-on hosting and any Supabase plan
  needed to avoid free-tier limits or project pausing.
- Exact monthly cost must be rechecked during `LT-0090`, because provider prices
  and quotas change.

## Consequences

- `LT-0083` should design the hosted schema for Postgres and Supabase Auth user
  IDs.
- `LT-0084` remains required before public exposure; provider features do not
  replace CSRF, headers, request limits, route hardening, or tests.
- `LT-0085` should implement Supabase Auth sign-in, callback/session handling,
  secure cookies, and local-development auth behavior.
- `LT-0086` should enforce user ownership in Java data-access paths and may add
  Supabase RLS policies as an extra guard.
- `LT-0087` should target Supabase Postgres through the hosted persistence
  adapter.
- `LT-0092` remains the broader Postgres port after the adapter and hosted
  schema are clear.
- A Cloudflare Workers/D1 or similar rewrite is deferred until the hosted Java
  path proves insufficient.

## Alternatives considered

- Neon Postgres plus separate auth: attractive for serverless Postgres and cost,
  but it leaves more auth integration work for the first hosted beta.
- Turso/libSQL: attractive because it is SQLite-compatible, but the hosted
  roadmap already needs multi-user ownership, auth integration, and a clearer
  Postgres-compatible path for relational authorization.
- Cloudflare Workers plus D1: attractive for a low-cost edge architecture, but
  it implies a backend rewrite before the product validates hosted use.
- Render Postgres only: simpler vendor surface for deploy plus database, but it
  does not solve managed auth as directly as Supabase.
- Expose the current local Java/SQLite server directly: still rejected by
  ADR-0002 because it lacks the required public-hosting security and persistence
  model.

## Reference notes

Provider details were checked against official documentation on 2026-06-14:

- Supabase billing and Auth docs: https://supabase.com/docs/guides/platform/billing-on-supabase and https://supabase.com/docs/guides/auth
- Neon pricing: https://neon.com/pricing
- Turso pricing: https://turso.tech/pricing
- Cloudflare D1 pricing: https://developers.cloudflare.com/d1/platform/pricing/
- Render pricing: https://render.com/pricing
