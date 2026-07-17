---
id: LT-0094
title: Add PWA installability shell
status: done
track: interface
priority: medium
effort: medium
created: 2026-06-15
updated: 2026-06-26
owner: codex
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

- [x] `/manifest.webmanifest` exposes app name, icons, start URL, display mode,
      theme color, and background color.
- [x] A service worker caches only static user-neutral assets and a generic
      offline fallback.
- [x] Authenticated pages and history fragments are network-only.
- [x] Tests verify the manifest and service-worker cache allowlist.
- [x] Documentation points back to `docs/pwa-offline-boundaries.md`.

## Notes

Implemented public routes for `/manifest.webmanifest`, `/service-worker.js`,
`/offline.html`, and `/pwa-icon.svg`. `WebHtml` links the manifest and registers
the service worker. The service worker allowlist contains only the manifest,
offline fallback, and icon; navigation requests stay network-first and fall back
to the generic offline page only when the network is unavailable.

Verification:

- `.\gradlew.bat test --tests com.lifttrax.cli.WebServerCliTest`
