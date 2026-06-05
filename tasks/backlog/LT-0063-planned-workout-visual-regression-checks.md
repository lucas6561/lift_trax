---
id: LT-0063
title: Add planned workout visual regression checks
status: idea
track: quality
priority: medium
effort: medium
created: 2026-06-05
updated: 2026-06-05
owner: unassigned
depends_on: [LT-0024, LT-0025, LT-0062]
---

# LT-0063: Add planned workout visual regression checks

## Why

Recent planned-workout work changed dense browser, follow-along, and print layouts. Route tests catch content, but they do not prove the views remain readable at real desktop, phone, and print sizes.

## Outcome

The project has a repeatable visual verification path for planned-workout preview, follow-along, and print views.

## Scope

- In scope: representative fixtures, screenshot or rendered-page checks, desktop and mobile viewports, print-oriented checks, and documentation for the verification flow.
- Out of scope: broad visual testing for every route or pixel-perfect design locking.

## Acceptance criteria

- [ ] Planned-workout preview has a visual check with representative history and target data.
- [ ] Follow-along sessions have a mobile-width visual check that verifies controls and set inputs do not overlap.
- [ ] Print output has a check that catches orphaned headings or unreadable page breaks.
- [ ] The verification path can run locally without depending on live network access.
- [ ] `qualityGate` or documented follow-up verification passes.

## Notes

This should build on the existing renderer tests and the HTTP smoke-check fallback used when browser automation is unavailable.
