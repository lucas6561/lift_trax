---
id: LT-0082
title: Select hosted auth and data platform
status: done
track: platform
priority: high
effort: medium
created: 2026-06-08
updated: 2026-06-14
owner: unassigned
depends_on: [LT-0015, LT-0016]
---

# LT-0082: Select hosted auth and data platform

## Why

The hosted LiftTrax path needs identity and durable user-owned data before the
app can safely be reached from anywhere. The project should choose a concrete
platform direction before reshaping persistence or authentication code.

## Outcome

A short decision document compares realistic hosted auth and database options
and chooses the first implementation stack for private hosted use.

## Scope

- In scope: managed auth options, managed database options, free-tier limits,
  Java compatibility, local data migration, security responsibilities, and
  expected monthly cost for a low-traffic private beta.
- Out of scope: implementing the selected provider.

## Acceptance criteria

- [x] The comparison covers managed Postgres-style storage, SQLite-compatible
      hosted storage, and serverless edge database options.
- [x] The selected stack names the auth provider, database provider, deployment
      target, and local development equivalent.
- [x] The decision explains whether the Java backend remains in the first hosted
      phase or is replaced by a new backend surface.
- [x] Security and cost tradeoffs are documented.
- [x] Follow-up implementation tasks are updated if the chosen stack changes the
      order of the hosted roadmap.

## Notes

Completed with `docs/adr/0003-hosted-auth-data-platform.md`.

The chosen first hosted stack is Supabase Auth, Supabase Postgres, and a Render
web service for the existing Java backend. The hosted sequence remains mostly
unchanged, but downstream tasks now target Supabase/Postgres explicitly:

1. `LT-0083`: Design the hosted schema for Postgres and Supabase Auth user IDs.
2. `LT-0084`: Add the public web security baseline before exposure.
3. `LT-0085`: Implement Supabase Auth session handling.
4. `LT-0086`: Enforce Java-side user authorization, with Supabase RLS as defense
   in depth where useful.
5. `LT-0087`: Build the hosted persistence adapter against Supabase Postgres.
6. `LT-0092`: Complete the broader Postgres port after the adapter and schema
   are clear.
