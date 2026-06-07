---
id: LT-0045
title: Add keyboard-friendly logging
status: done
track: interface
priority: medium
effort: medium
created: 2026-05-30
updated: 2026-06-07
owner: unassigned
depends_on: [LT-0002]
---

# LT-0045: Add keyboard-friendly logging

## Why

Desktop users should be able to log and correct training quickly without reaching for the mouse after every set.

## Outcome

Core logging forms support predictable tab order, submit behavior, focus return, and keyboard shortcuts where they are clearly useful.

## Scope

- In scope: tab order, focus management, keyboard submit paths, edit/delete navigation, and tests where practical.
- Out of scope: a full command palette or advanced configurable shortcuts.

## Acceptance criteria

- [x] Logging fields follow a predictable tab order.
- [x] After adding or editing a set, focus returns to the next useful control.
- [x] Keyboard users can reach edit and delete actions.
- [x] Any shortcut behavior is documented in code or tests without adding noisy in-app instructional text.
- [x] `qualityGate` passes.

## Notes

The goal is quiet speed, especially for people entering a backlog of completed sets.

Implemented 2026-06-07 with focus targets for add-execution redirects, keyboard-native
execution edit/delete/add/cancel controls, and focused renderer/server tests that document the
quiet keyboard behavior.

Verification: `./gradlew.bat test` and `./gradlew.bat qualityGate` passed on 2026-06-07.
