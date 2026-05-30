---
id: LT-0050
title: Add compact history mode
status: idea
track: interface
priority: medium
effort: medium
created: 2026-05-30
updated: 2026-05-30
owner: unassigned
depends_on: [LT-0008]
---

# LT-0050: Add compact history mode

## Why

Training history can grow quickly. Users need a dense view for scanning many workouts without losing the important numbers.

## Outcome

History pages can render in a compact mode optimized for comparison across dates, lifts, top sets, volume, and notes.

## Scope

- In scope: compact table or grouped layout, readable mobile fallback, mode selection, and route tests.
- Out of scope: exporting history, advanced filtering, or replacing detailed history views.

## Acceptance criteria

- [ ] Users can view training history in a compact scan-friendly layout.
- [ ] Key values fit cleanly on desktop and phone viewports.
- [ ] The detailed view remains available.
- [ ] Route tests cover compact mode with representative history data.
- [ ] `qualityGate` passes.

## Notes

This should make older data more useful without adding analytics complexity yet.
