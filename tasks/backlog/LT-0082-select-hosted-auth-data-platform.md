---
id: LT-0082
title: Select hosted auth and data platform
status: idea
track: platform
priority: high
effort: medium
created: 2026-06-08
updated: 2026-06-08
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

- [ ] The comparison covers managed Postgres-style storage, SQLite-compatible
      hosted storage, and serverless edge database options.
- [ ] The selected stack names the auth provider, database provider, deployment
      target, and local development equivalent.
- [ ] The decision explains whether the Java backend remains in the first hosted
      phase or is replaced by a new backend surface.
- [ ] Security and cost tradeoffs are documented.
- [ ] Follow-up implementation tasks are updated if the chosen stack changes the
      order of the hosted roadmap.

## Notes

Candidate families include Supabase or Neon for Postgres, Turso for
SQLite-compatible hosted data, and Cloudflare Workers plus D1 for a more
serverless rewrite. The goal is a pragmatic first hosted stack, not a permanent
commitment to one vendor forever.
