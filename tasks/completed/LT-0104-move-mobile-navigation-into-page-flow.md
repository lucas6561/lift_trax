---
id: LT-0104
title: Move mobile navigation into the top of the page flow
status: done
track: interface
priority: high
effort: small
created: 2026-10-31
updated: 2026-10-31
owner: unassigned
depends_on: [LT-0101]
---

# LT-0104: Move mobile navigation into the top of the page flow

## Why

The unified mobile navigation is fixed to the bottom of the viewport. It stays
visible while the user scrolls, consumes scarce phone screen space, and forces
important Work Along controls and content to sit above it. During an active
workout, the persistent bar is more obstructive than helpful.

## Outcome

On phone-sized screens, the primary navigation appears near the top of the
page as normal document content. It is available when the page opens and then
scrolls completely out of view, leaving the full viewport available for Work
Along and other focused tasks.

## Scope

- In scope: mobile placement and positioning of the five-destination primary
  navigation; removal of bottom-nav spacing and offsets; Work Along and quick
  logging control placement; responsive, accessibility, and browser tests.
- Out of scope: changing the five destinations, desktop sidebar behavior,
  route or deep-link changes, redesigning the application header, or changing
  workout-session behavior.

## Acceptance criteria

- [x] At phone widths, the primary navigation is rendered near the top of the
      application shell, after the application header and before page content.
- [x] The mobile navigation participates in normal document flow and is not
      fixed or sticky; after scrolling down, it is fully outside the viewport.
- [x] No mobile-only bottom padding or safe-area reservation remains solely for
      the former bottom navigation, and page content is never hidden behind the
      navigation.
- [x] In Work Along at approximately 390x844, no persistent primary navigation
      occupies the viewport, and the session form and save control can use the
      reclaimed vertical space without overlap.
- [x] Sticky save controls in Work Along and quick logging no longer use an
      offset intended to clear the old bottom navigation; they remain usable
      and respect any applicable device safe area.
- [x] The mobile navigation still exposes Today, Train, Workouts, History, and
      Exercises with the existing visual current-section state and accessible
      `aria-current` semantics.
- [x] Keyboard focus order follows the visual order from the header through
      navigation into main content, and the existing Skip to main content link
      continues to bypass navigation correctly.
- [x] Desktop navigation and the existing routes, deep links, browser history,
      authentication, offline drafts, and service-worker behavior are
      unchanged.
- [x] Focused tests cover mobile shell order, non-fixed positioning, removal of
      obsolete bottom spacing and control offsets, current-section semantics,
      and preservation of desktop behavior.
- [x] Real-browser checks at approximately 390x844 cover a long Work Along
      session and quick logging: the navigation is visible at page load,
      scrolls away, does not cover content or controls, and stays gone while
      working farther down the page.
- [x] `./gradlew.bat qualityGate` passes.

## Notes

`ApplicationShellHtml` currently renders the mobile navigation after the main
application frame. In the mobile stylesheet, `.mobile-primary-navigation` is
fixed to the bottom, `body` reserves five rem of bottom space, and Work Along
and quick-log save controls use enlarged bottom offsets to clear that bar.
Moving the navigation requires treating those coupled offsets as one change.

Keep the navigation compact at the top, but favor reclaimed workout space over
making it persistently reachable. Users can scroll back to the top when they
want to change sections.

## Verification

Implemented and verified on 2026-10-31. Mobile primary navigation now follows
the header in document flow. Removed the former bottom bar reservation and
control offsets; mobile save controls respect the device safe area. Scroll
padding now only clears the remaining save control.

Focused shell, shared HTML, authenticated route, Work Along, and quick-logging
renderer tests passed. The full qualityGate passed: 356 tests, zero failures
or errors, and two intentional skips, plus formatting, PMD, and coverage.

Real-browser checks used the in-app Chromium browser with an isolated H2
fixture at 390x844 and 1280x900. Quick logging and a 1,951-pixel Work Along
session showed navigation below the header at load, completely offscreen
after scrolling, and no navigation overlap at the lower form controls.
Quick-log Save used a 4.704-pixel bottom offset; Work Along's final-block
submission remained visible and enabled. There was no horizontal overflow.
Keyboard traversal followed Skip, brand, Account, Sign Out, all five primary
destinations, then page content; Skip focused main-content. Draft refresh
restored Work Along, and desktop retained its sticky sidebar with hidden
mobile navigation. Browser fixture data was disposable; production data
was not used. Chrome extension control stalled, so acceptance checks were
completed in the in-app browser.
