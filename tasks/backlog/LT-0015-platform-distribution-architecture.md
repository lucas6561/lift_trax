---
id: LT-0015
title: Choose the web, mobile, and distribution architecture
status: idea
track: platform
priority: high
effort: medium
created: 2026-05-27
updated: 2026-05-27
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

- [ ] An ADR records the chosen platform direction.
- [ ] The decision covers desktop browser use, phone use, offline expectations, and packaging.
- [ ] The decision names what must change before multiple users can safely use the app.
- [ ] Follow-up implementation tasks are created or updated.

## Notes

Start with the least complex path that supports real training use.
