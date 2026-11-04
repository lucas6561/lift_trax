---
id: LT-0125
title: Resolve equipment conflicts within workout blocks
status: idea
track: training-logic
priority: high
effort: medium
created: 2026-11-04
updated: 2026-11-04
owner: unassigned
depends_on: [LT-0030, LT-0124]
---

# LT-0125: Resolve equipment conflicts within workout blocks

## Why

Circuits and supersets can accidentally require the same limited equipment at
the same time. A plan may look valid exercise by exercise while being awkward
or impossible to perform as one block.

## Outcome

Workout generation, authoring, and preview evaluate simultaneous exercises
against the lifter's saved equipment and either select compatible equipment
setups or explain the unresolved conflict before the workout begins.

## Scope

- In scope: simultaneous-block semantics; inventory quantity and exclusivity;
  alternative setup selection; deterministic validation; authoring and preview
  messages; generator fallback; tests.
- Out of scope: reserving equipment between multiple people, tracking real-time
  gym occupancy, silently changing exercises, or treating sequential exercises
  as simultaneous merely because they share a block container.

## Acceptance criteria

- [ ] The workout model documents which block structures run exercises
      simultaneously or in rotation and which are sequential; conflict checks
      use those semantics instead of flagging every repeated item in a block.
- [ ] Validation considers compound requirements, alternative setups,
      equipment quantity, availability, and exclusive-use rules.
- [ ] When compatible alternatives exist, the resolver chooses a deterministic
      combination and records or displays the selected setup without changing
      the exercise's catalog definition.
- [ ] Workout generation can choose a compatible candidate or setup when one is
      available and never relies on exercise-name checks such as "cable" or
      "dumbbell".
- [ ] When no valid combination exists, authoring and preview identify the
      equipment shortage and affected exercises, then offer actionable choices
      such as changing a setup, making work sequential, or selecting a
      substitution.
- [ ] Unknown or legacy equipment metadata produces a visible unresolved state
      rather than a false claim that the block is conflict-free.
- [ ] The algorithm remains bounded for realistic block sizes and has focused
      tests for no conflict, shared sufficient quantity, exclusive shortage,
      alternative setup resolution, deterministic ties, legacy data, and no
      valid solution.
- [ ] Desktop and phone browser checks cover conflict and resolution messages in
      workout authoring and preview.
- [ ] `qualityGate` passes.

## Notes

This card owns deconflicting equipment inside a simultaneous workout block.
`LT-0058` later uses the same model to recommend a different exercise when no
equipment setup for the planned exercise is workable.
