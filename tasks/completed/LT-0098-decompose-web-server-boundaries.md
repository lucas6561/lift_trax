---
id: LT-0098
title: Decompose web server boundaries
status: done
track: quality
priority: high
effort: medium
created: 2026-07-27
updated: 2026-07-27
owner: unassigned
depends_on: [LT-0013]
---

# LT-0098: Decompose web server boundaries

## Why

`WebServerCli` combined startup, route registration, route behavior, and page
rendering in one large class, making focused tests and future changes harder.

## Outcome

Server startup can be exercised without invoking the process entry point, route
registration has a dedicated boundary, and account-page rendering is isolated
as the first page-specific renderer.

## Scope

- In scope: extract server lifecycle and route registration, extract one
  page-specific renderer, preserve behavior, and add focused tests.
- Out of scope: moving every route handler or page renderer in one change.

## Acceptance criteria

- [x] Route registration is separated from the command-line entry point.
- [x] Tests can start and stop the web server on an ephemeral port.
- [x] Account-page rendering and username suggestion are extracted and tested.
- [x] Existing route behavior remains covered by focused route tests.
- [x] `./gradlew.bat qualityGate` passes.

## Notes

Implemented `WebRouteRegistry`, the closeable `WebServerCli.RunningServer`
lifecycle, and `AccountPageHtml`. This is the first controlled decomposition
slice; remaining page renderers can move independently as they change.
`./gradlew.bat qualityGate` passed on 2026-07-27.
