---
id: LT-0041
title: Add first-time user empty state
status: idea
track: interface
priority: medium
effort: small
created: 2026-05-30
updated: 2026-05-30
owner: unassigned
depends_on: [LT-0001]
---

# LT-0041: Add first-time user empty state

## Why

A new user should immediately understand what to do when there are no lifts, executions, plans, or completed workouts yet.

## Outcome

Empty dashboard and history states guide the user toward the next useful action without showing blank tables or confusing placeholders.

## Scope

- In scope: empty states for the dashboard and key history views, clear calls to action, and route tests.
- Out of scope: a full onboarding wizard, account setup flow, or sample-data generator.

## Acceptance criteria

- [ ] The dashboard has a helpful empty state when no training data exists.
- [ ] Empty states link to the most relevant create or import action.
- [ ] Copy is short, specific, and does not expose technical implementation details.
- [ ] Route tests verify the empty and non-empty variants.
- [ ] `qualityGate` passes.

## Notes

This should make the app feel intentional even before the user has logged anything.
