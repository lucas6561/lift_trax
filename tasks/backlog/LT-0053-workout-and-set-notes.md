---
id: LT-0053
title: Add workout and set notes
status: idea
track: product
priority: medium
effort: medium
created: 2026-05-30
updated: 2026-05-30
owner: unassigned
depends_on: [LT-0027]
---

# LT-0053: Add workout and set notes

## Why

Numbers alone do not capture pain, equipment changes, missed reps, form notes, or context from the training day.

## Outcome

Users can add notes to a workout and to individual sets, and those notes appear in relevant history and session views.

## Scope

- In scope: note persistence, input fields, display in history, display in active sessions, and tests.
- Out of scope: rich text editing, attachments, media uploads, or coach comments.

## Acceptance criteria

- [ ] Completed workouts can store an overall note.
- [ ] Individual logged sets can store an optional note.
- [ ] Notes appear in history without overwhelming compact views.
- [ ] Tests cover create, edit, delete, and display behavior for notes.
- [ ] `qualityGate` passes.

## Notes

Plain text is enough for the first version.
