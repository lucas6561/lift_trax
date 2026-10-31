---
id: LT-0102
title: Add week navigation to the app preview
status: done
track: interface
priority: medium
effort: small
created: 2026-10-31
updated: 2026-10-31
owner: unassigned
depends_on: [LT-0062, LT-0101]
---

# LT-0102: Add week navigation to the app preview

## Why

The planned-workout App Preview renders every week in one long page. For a
multi-week program, users have to scroll through earlier weeks to inspect or
return to a specific week. The preview should make week-to-week movement as
direct as choosing a training week in Work Along.

## Outcome

Users can move to the previous or next week and select any available week from
clear controls in the App Preview. Navigation remains convenient on desktop and
phone without hiding or changing the workout content.

## Scope

- In scope: App Preview week selector; previous/next controls; current-week
  indication; stable week targets; keyboard, mobile, and browser behavior;
  focused renderer and browser tests.
- Out of scope: changing workout schema semantics, choosing the Work Along
  session week, modifying print/Markdown/JSON output, editing workouts, or
  persisting a preferred week across different workout files.

## Acceptance criteria

- [x] A preview containing more than one week shows a visible week selector and
      Previous Week / Next Week controls before the rendered weeks.
- [x] The selector uses the workout's actual week numbers and display order,
      including valid non-contiguous week numbers rather than assuming `1..N`.
- [x] Selecting a week or using Previous/Next moves the viewport and keyboard
      focus to that week's heading without submitting or reloading the preview.
- [x] The current week is communicated visually and accessibly, and the
      Previous/Next controls are disabled or unavailable at the first and last
      week boundaries.
- [x] Each rendered week has a stable direct target so a week-specific URL
      fragment can be refreshed, bookmarked, and used with browser
      back/forward navigation.
- [x] Week navigation remains easy to use in the unified application shell on
      desktop and at approximately 390x844 without covering preview actions or
      workout content.
- [x] A single-week preview remains compact and does not show misleading
      Previous/Next controls.
- [x] App Preview actions and content remain unchanged, and Work Along,
      print, Markdown, and JSON output behavior is unaffected.
- [x] Focused tests cover single-week and multi-week rendering, actual week
      numbers, boundary behavior, direct targets, keyboard semantics, and
      desktop/mobile browser navigation.
- [x] `./gradlew.bat qualityGate` passes.

## Notes

`PlannedWorkoutHtml` currently appends every preview week sequentially without
week navigation. Work Along already renders a week selector, which is a useful
interaction reference, but preview navigation must remain independent from
starting or saving a workout session.

Prefer progressive enhancement: direct week targets should remain usable even
if client-side scripting is unavailable.

Implemented and verified on 2026-10-31. App Preview now has sticky, responsive
week controls with real file-order week numbers, focusable heading fragments,
accessible current-week state, and disabled boundary buttons. Single-week
previews keep only the direct target; native week links work without JavaScript.

Focused renderer tests cover single/multi-week output, non-contiguous weeks in
non-numeric display order (5, 2, 9), direct targets, and preservation of other
outputs. Chromium checks passed at 1280x900 and 390x844 for Enter/Space button
activation, keyboard selection, heading focus/visibility, boundary state,
no-reload navigation, direct fragments, back/forward, refresh, single-week
previews, and no-script links. Screenshots were visually reviewed.

The full quality gate passed with 359 tests, two intentional skips, and 92.46%
instruction coverage. A separate generated build/cache folder avoided report
locks from another active build in this checkout. The production database was
not used; browser verification ran against the disposable H2 fixture.

Repeat browser checks with `runBrowserFixture`, then:
`node scripts/application-shell-browser-check.mjs <fixture-url> <session-cookie> preview-weeks`.
