---
id: LT-0113
title: Ratchet source size and complexity limits
status: complete
track: quality
priority: high
effort: medium
created: 2026-11-01
updated: 2026-11-01
owner: unassigned
depends_on: [LT-0012, LT-0013, LT-0028]
---

# LT-0113: Ratchet source size and complexity limits

## Why

The quality gate reported a clean PMD baseline even though several production
classes exceeded 1,000 physical lines, individual renderer methods contained
more than 1,000 lines, and some tests spanned hundreds of lines. The previous
PMD configuration had no class or method size rule, allowed cyclomatic
complexity of 29 per method and 225 per class, set the NPath threshold to
60,000,000, and disabled test-source PMD entirely.

These settings prevented only extreme growth and did not give useful early
warning when a class, method, or test began accumulating responsibilities.

## Outcome

The quality gate enforces documented size and complexity ceilings for
production and test code. The initial hotspots were decomposed until both
rulesets passed with no violation-suppression clauses, so new and existing code
share the same limits.

## Acceptance criteria

- [x] Record the current distribution and worst offenders for physical lines,
      NCSS, cyclomatic complexity, NPath complexity, and methods per class in
      both `src/main` and `src/test`.
- [x] Add an NCSS-based rule for production classes and methods, starting no
      looser than 500 NCSS per class and 60 NCSS per method unless the baseline
      documents a stronger reason for a different threshold.
- [x] Enforce physical-line ceilings as well as NCSS so large embedded HTML,
      JavaScript, SQL, or text blocks cannot evade the size check. Initial
      ceilings are 750 lines per production class, 100 lines per production
      method, 1,000 lines per test class, and 100 lines per test method.
- [x] Lower cyclomatic complexity from 29 per method and 225 per class to 15
      per method and 100 per class.
- [x] Replace the 60,000,000 NPath threshold with PMD's documented 200-path
      warning point.
- [x] Enable and configure method-count rules at 35 production methods and 50
      test methods, excluding PMD's trivial accessor pattern.
- [x] Enable `pmdTest` so oversized or overly complex tests fail the same local
      and CI quality gate.
- [x] Do not use broad package, renderer, CLI, database, or test exclusions.
      Resolve the initial baseline violations in code and retain no PMD
      violation-suppression clauses.
- [x] Add boundary probes proving that code at the selected physical limits
      passes and representative production classes, production methods, test
      classes, and test methods one line over fail the checked-in rules.
- [x] Document each threshold, how it was selected, how to run the checks, and
      the policy for tightening limits without weakening or silently resetting
      the baseline.
- [x] Existing focused tests and `./gradlew.bat qualityGate` pass with zero
      source-size or complexity exceptions.

## Implementation notes

`docs/source-size-and-complexity.md` records the complete 2026-11-01
pre-remediation metric distribution, worst offenders, threshold rationale,
commands, and zero-exception ratchet policy. Physical-line XPath rules measure
declaration spans, including maintained text blocks. `pmdTest` uses a focused
size/complexity ruleset instead of applying unrelated production style opinions
to fixtures.

`SourceSizePolicyTest` runs PMD 7.13 programmatically against generated boundary
sources. It proves 750/100 production and 1,000/100 test boundaries pass and
that each corresponding source one line over is reported. The initial
unsuppressed baseline also failed with 60 production and 18 test violations,
confirming the new tasks rejected the representative hotspots before they were
resolved through decomposition.

The decomposition completed alongside the ratchet is recorded in:

- `LT-0114`: web UI rendering hotspots;
- `LT-0115`: server, authentication, CLI, and session orchestration;
- `LT-0116`: persistence, backup, password, and integrity hotspots;
- `LT-0117`: catalog and workout algorithms;
- `LT-0118`: oversized test suites and fixtures.

Verification on 2026-11-01:

- `./gradlew.bat test --tests com.lifttrax.quality.SourceSizePolicyTest` passed
  all four boundary tests;
- `./gradlew.bat pmdMain pmdTest` passed with zero reported violations and no
  PMD violation-suppression clauses or annotations;
- `./gradlew.bat qualityGate` passed 363 tests with zero failures or errors and
  two intentional environment skips, plus Spotless, production/test PMD, and
  92.658% aggregate instruction coverage.
