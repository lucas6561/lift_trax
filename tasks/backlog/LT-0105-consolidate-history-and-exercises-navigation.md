---
id: LT-0105
title: Consolidate History and Exercises navigation
status: ready
track: interface
priority: medium
effort: medium
created: 2026-10-31
updated: 2026-10-31
owner: unassigned
depends_on: [LT-0101]
---

# LT-0105: Consolidate History and Exercises navigation

## Why

History and Exercises are separate primary destinations, but their main pages
currently render the same per-exercise list with the same details, catalog
controls, and expandable execution history. Exercises adds create and import
actions, while History adds Weekly and Query views. As a result, the two
primary tabs look and behave almost identically and make the navigation feel
more complicated than the underlying product.

## Outcome

History becomes the single primary destination for reviewing training records
and reaching exercise-specific information. Exercise Catalog remains available
as a clearly labeled secondary History view, while the standalone Exercises
item is removed from desktop and mobile primary navigation.

## Scope

- In scope: primary and secondary navigation; History and Exercise Catalog
  page responsibilities; current-section state; breadcrumbs; catalog, lift
  detail, sharing, and import entry points; preservation of existing routes;
  focused renderer, accessibility, and browser tests.
- Out of scope: deleting exercise-catalog capabilities, changing stored lifts
  or executions, changing history calculations, route removal, schema changes,
  or redesigning lift detail content.

## Acceptance criteria

- [ ] Desktop and mobile primary navigation contain Today, Train, Workouts,
      and History; the standalone Exercises primary item is removed.
- [ ] History exposes distinct secondary views for Executions, Weekly, Query,
      and Exercise Catalog, with the current view conveyed visually and using
      accessible `aria-current` semantics.
- [ ] The Executions view is focused on recorded training and does not duplicate
      exercise-catalog creation, import, enable/disable, edit, or delete
      controls.
- [ ] Exercise Catalog contains the existing create, import, enable/disable,
      edit, delete, details, sharing, and shared-lift entry points without
      duplicating the full History landing experience.
- [ ] Exercise-specific history remains reachable from the catalog and lift
      detail flows without requiring a second primary navigation destination.
- [ ] Existing `/?tab=exercises` bookmarks and direct links continue to work,
      open the Exercise Catalog secondary view, and mark History as the current
      primary section.
- [ ] Lift detail, exercise-catalog import, and shared-lift import pages use
      History as their primary section and provide clear breadcrumbs back to
      Exercise Catalog.
- [ ] Existing History URLs for Executions, Weekly, and Query continue to work
      with browser refresh, back/forward navigation, and account-scoped data.
- [ ] The four-item mobile navigation remains balanced and usable at
      approximately 390x844, including when LT-0104 moves it into the top page
      flow.
- [ ] Keyboard navigation, the Skip to main content link, authentication,
      CSRF protection, account isolation, offline drafts, and service-worker
      behavior remain unchanged.
- [ ] Focused tests cover the four primary destinations, History secondary
      navigation, legacy Exercises deep links, current-section state,
      breadcrumbs, and separation of execution and catalog controls.
- [ ] Real-browser checks at desktop and approximately 390x844 cover all four
      History views, a lift detail, both catalog import paths, refresh,
      back/forward navigation, and keyboard focus order.
- [ ] `./gradlew.bat qualityGate` passes.

## Notes

`WebUiRenderer` currently uses the same `renderExecutionList` implementation
for `tab=executions` and `tab=exercises`. The shared renderer includes both
catalog-management controls and lazy-loaded execution history, which is the
main reason the two primary destinations feel identical.

Keep History as the primary label because reviewing completed training is the
frequent user task. Exercise Catalog is important but is supporting setup and
maintenance work, so it does not need its own permanent primary destination.
Preserve the `tab=exercises` route as a stable deep link rather than redirecting
or removing it.
