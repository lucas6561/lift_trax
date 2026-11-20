---
id: LT-0075
title: Add mutation testing ratchet
status: done
track: quality
priority: medium
effort: medium
created: 2026-06-07
updated: 2026-11-19
owner: unassigned
depends_on: [LT-0031, LT-0073, LT-0074]
---

# LT-0075: Add mutation testing ratchet

## Why

Once mutation testing covers multiple high-value slices, the project needs a
repeatable way to keep scores from drifting backward without making every local
quality check painfully slow.

## Outcome

Mutation testing has a documented ratchet policy, stable reports, and a clear
command or CI path that prevents accidental score regressions for the covered
target set.

## Scope

- In scope: mutation score baseline review, threshold ratcheting, docs, report retention expectations, and a decision about whether mutation testing belongs in CI, a scheduled check, or a separate release-readiness command.
- Out of scope: requiring a perfect mutation score, adding every package to PIT at once, or slowing the normal `qualityGate` beyond a practical local workflow.

## Acceptance criteria

- [x] Current PIT mutation and coverage scores are documented for each covered target slice.
- [x] Mutation thresholds are raised only after surviving mutants are triaged or intentionally baselined.
- [x] The repo has a repeatable command for the ratcheted mutation check, separate from `qualityGate` unless runtime is acceptable.
- [x] Documentation explains when developers should run mutation testing locally and how to inspect `build/reports/pitest/`.
- [x] If CI is available, a follow-up or implementation path is recorded for publishing PIT reports as build artifacts.
- [x] `qualityGate` passes.

## Notes

This should come after the first two expansion cards so the ratchet is based on
real report data instead of an optimistic threshold.

The measured 2026-11-19 baseline is 398 of 445 mutants killed (89 percent), 91
percent mutated-class line coverage, and 94 percent test strength. The ratchet
sets conservative floors of 85 percent mutation score and 90 percent coverage,
exposed through `./gradlew.bat mutationGate`. Core generation, planned-workout
files, and active-workout input/save respectively killed 98/113, 194/216, and
106/116 mutants. A separate scheduled/manual GitHub Actions workflow runs the
gate and publishes `build/reports/pitest/`; `qualityGate` remains unchanged for
normal development speed. Both gates passed on 2026-11-19.
