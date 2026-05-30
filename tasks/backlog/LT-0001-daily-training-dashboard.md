---
id: LT-0001
title: Define the daily training dashboard
status: done
track: product
priority: high
effort: medium
created: 2026-05-27
updated: 2026-05-30
owner: unassigned
depends_on: []
---

# LT-0001: Define the daily training dashboard

## Why

The app needs a clear first screen for training day use. Right now the web UI can list/search lifts and add executions, but it does not yet answer "what am I doing today?" as the primary experience.

## Outcome

The home page shows today's planned or suggested work, recent execution history, and a fast path to log sets.

## Scope

- In scope: dashboard layout, data needed by the page, empty states, and a first-pass implementation.
- Out of scope: advanced analytics, calendar scheduling, and notifications.

## Acceptance criteria

- [x] The default web page presents a daily training view.
- [x] A user can jump from the dashboard to logging a set.
- [x] Recent relevant executions are visible without searching first.
- [x] Web UI tests cover the rendered dashboard states.

## Notes

This should probably build on `WebUiRenderer` and existing database read paths before introducing a larger frontend framework.

Implemented 2026-05-30 with a default Dashboard tab, suggested work links, recent history, empty states, and focused renderer tests.

Verification: `./gradlew.bat qualityGate` passed on 2026-05-30.

Follow-up 2026-05-30: fixed returning to the deferred Dashboard tab and reduced dashboard first-load database work by bulk-loading enabled lift statuses, latest executions, and recent history.
