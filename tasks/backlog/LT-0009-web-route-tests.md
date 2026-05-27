---
id: LT-0009
title: Expand web route tests
status: idea
track: quality
priority: medium
effort: medium
created: 2026-05-27
updated: 2026-05-27
owner: unassigned
depends_on: []
---

# LT-0009: Expand web route tests

## Why

The lightweight web UI is becoming a real product surface. Route behavior should be protected as forms, redirects, errors, and database writes expand.

## Outcome

Web route tests cover the most important page and form flows, including happy paths and validation failures.

## Scope

- In scope: route-level tests, form handling tests, and response status expectations.
- Out of scope: full browser automation unless a UI change specifically needs it.

## Acceptance criteria

- [ ] Home, lift detail, add execution, and error paths have route coverage.
- [ ] Tests verify status codes and meaningful page content.
- [ ] Database-writing routes are tested against isolated test data.
- [ ] Coverage expectations remain focused and maintainable.

## Notes

Build on the existing `WebServerCliTest`, `WebUiRendererTest`, and `WebHtmlTest` patterns.

