---
id: LT-0015
title: Choose the web, mobile, and distribution architecture
status: done
track: platform
priority: high
effort: medium
created: 2026-05-27
updated: 2026-06-08
owner: unassigned
depends_on: [LT-0010]
---

# LT-0015: Choose the web, mobile, and distribution architecture

## Why

LiftTrax needs to work from desktop browsers and phones now, while leaving room for mobile app packaging and broader distribution later.

## Outcome

An architecture decision documents whether the next product step is responsive web, PWA, packaged mobile wrapper, native app, hosted service, or some staged combination.

## Scope

- In scope: evaluate options, document tradeoffs, pick a near-term path, and identify constraints for future distribution.
- Out of scope: implementing a full mobile app.

## Acceptance criteria

- [x] An ADR records the chosen platform direction.
- [x] The decision covers desktop browser use, phone use, offline expectations, and packaging.
- [x] The decision names what must change before multiple users can safely use the app.
- [x] Follow-up implementation tasks are created or updated.

## Notes

Completed with `docs/adr/0002-hosted-web-pwa-managed-platform.md`. The chosen
direction is a hosted, responsive web app with a PWA-capable browser surface,
managed identity, managed durable data storage, and explicit security,
ownership, migration, deployment, and backup work before public use.

Follow-up roadmap cards:

1. `LT-0016`: Add user and ownership model design.
2. `LT-0082`: Select hosted auth and data platform.
3. `LT-0083`: Design hosted user data schema.
4. `LT-0084`: Add public web security baseline.
5. `LT-0085`: Implement account authentication.
6. `LT-0086`: Enforce user-scoped authorization.
7. `LT-0087`: Build hosted persistence adapter.
8. `LT-0088`: Import local database to hosted account.
9. `LT-0089`: Define PWA and offline boundaries.
10. `LT-0090`: Create hosted deployment pipeline.
11. `LT-0091`: Add hosted backup and export controls.
