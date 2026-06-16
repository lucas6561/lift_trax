---
id: LT-0094
title: Add PWA installability shell
status: idea
track: interface
priority: medium
effort: medium
created: 2026-06-15
updated: 2026-06-15
owner: unassigned
depends_on: [LT-0089, LT-0090]
---

# LT-0094: Add PWA installability shell

## Why

The hosted app should be easy to launch from a phone without implying that
training history works offline.

## Outcome

LiftTrax has an installable PWA shell that caches only static, user-neutral
assets and a generic offline fallback.

## Scope

- In scope: manifest, icons, theme metadata, service-worker registration,
  static-asset cache rules, offline fallback, and tests for cache boundaries.
- Out of scope: offline execution logging, background sync, and cached
  authenticated history.

## Acceptance criteria

- [ ] `/manifest.webmanifest` exposes app name, icons, start URL, display mode,
      theme color, and background color.
- [ ] A service worker caches only static user-neutral assets and a generic
      offline fallback.
- [ ] Authenticated pages and history fragments are network-only.
- [ ] Tests verify the manifest and service-worker cache allowlist.
- [ ] Documentation points back to `docs/pwa-offline-boundaries.md`.

## Notes

This implements the installability portion of `LT-0089` without adding offline
write behavior.
