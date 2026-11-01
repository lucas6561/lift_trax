# Source size and complexity ratchet

LiftTrax treats source size and complexity as change-prevention limits, not as
an assertion that every existing class is already small. `pmdMain` applies the
production policy in `config/pmd/ruleset.xml`; `pmdTest` applies the focused test
policy in `config/pmd/ruleset-test.xml`. Both tasks run through `staticAnalysis`
and the normal `qualityGate`.

## Enforced thresholds

| Source | Physical class | Physical method | Class NCSS | Method NCSS | Class cyclomatic | Method cyclomatic | NPath | Methods per class |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Production | 675 | 85 | 450 | 52 | 100 | 13 | 190 | 32 |
| Test | 950 | 90 | 650 | 52 | 100 | 13 | 190 | 45 |

The physical class rules cover classes, records, enums, and annotation types and
measure the complete declaration span, including comments, blank lines, and
embedded HTML, JavaScript, SQL, or other text blocks. Physical method rules also
cover ordinary, standard-constructor, and compact-constructor bodies. NCSS
measures executable source statements and catches dense code that a physical
line count alone can miss. PMD reports cyclomatic complexity at the configured
report level, so a method measured at 13 is a violation. NPath is capped at
190 paths.

Production NCSS is capped at 450 per class and 52 per method. Test classes get
a 650-NCSS allowance because setup and assertion code is intentionally more
verbose, while test methods are held to the same 52-NCSS cap and a
90-physical-line limit. The 32/45 method limits ignore
PMD's simple accessor pattern and are intended to detect classes accumulating
unrelated responsibilities rather than penalize small records or value access.

## 2026-11-01 baseline

The baseline scanned 131 production files (176 class-like declarations and 948
methods) and 65 test files (71 class-like declarations and 649 methods). The
bands below include nested declarations.

| Metric | Production distribution | Test distribution |
| --- | --- | --- |
| Physical file lines | 112 at 0-250; 11 at 251-500; 4 at 501-750; 0 at 751-1,000; 4 above 1,000 | 50 at 0-250; 6 at 251-500; 7 at 501-1,000; 1 at 1,001-2,000; 1 above 2,000 |
| Class NCSS | 154 at 0-100; 17 at 101-250; 4 at 251-500; 1 at 501-700 | 53 at 0-100; 12 at 101-250; 4 at 251-500; 1 at 501-700; 1 above 700 |
| Method NCSS | 834 at 0-15; 90 at 16-30; 23 at 31-60; 1 at 61-100 | 536 at 0-15; 85 at 16-30; 24 at 31-60; 3 at 61-100; 1 above 100 |
| Class cyclomatic | 61 at 0-25; 14 at 26-50; 11 at 51-100; 3 at 101-200; 2 above 200 | 62 at 0-25; 5 at 26-50; 1 at 51-100; 1 above 200 |
| Method cyclomatic | 784 at 0-5; 129 at 6-10; 27 at 11-15; 7 at 16-25; 1 above 25 | 627 at 0-5; 20 at 6-10; 2 at 11-15 |
| Method NPath | 792 at 0-10; 111 at 11-50; 24 at 51-200; 15 at 201-1,000; 6 above 1,000 | 618 at 0-10; 27 at 11-50; 3 at 51-200; 1 above 1,000 |
| Method-count violations | 4 classes above the 35-method policy | 2 classes above the 50-method policy |

The physical-class leaders are `WebUiRenderer` (2,882 declaration lines),
`PlannedWorkoutSessionHtml` (2,084), `WebServerCli` (1,555), and
`HostedPostgresTrainingDataStore` (1,028). The largest production methods are
`PlannedWorkoutSessionHtml.appendScript` (1,245 lines) and
`WebUiRenderer.renderTabbedLayout` (1,133). Test leaders are
`WebServerCliTest` (2,726 declaration lines) and `SqliteDb` (1,252); the longest
test method is
`HttpsProxyIntegrationTest.trustedTlsPreservesAuthenticationIsolationCsrfAndWorkoutSubmission`
at 213 lines.

NCSS leaders are `WebServerCli` at 697 and
`WebUiRenderer.renderWavePlannerForm` at 65 in production, and
`WebServerCliTest` at 1,164 and
`PlannedWorkoutSessionHtmlTest.sessionPageStartsWithEmptyCompletedSetsAndAllowsWorkoutFileSwaps`
at 134 in tests. Cyclomatic leaders are `WebServerCli` at 221 and
`ExecutionInputWidgetHtml.renderWorkAlong` at 28; test code's leaders are
`SqliteDb` at 209 and `SqliteDb.parseMetric` at 13. NPath leaders are
`ExecutionInputWidgetHtml.renderWorkAlong` at 42,467,328 and `render` at
8,388,608; `SqliteDb.parseMetric` leads tests at 2,304. Source declaration
counts are led by `WebServerCli` (72), `HostedPostgresTrainingDataStore` (52),
`WebUiRenderer` (50), and `WebAuth` (46), then by `WebServerCliTest` (103) and
`SqliteDb` (73) in tests. PMD's method-count rule excludes trivial accessors,
so these declaration totals are a conservative upper bound on that metric.

## Zero-exception baseline

The initial unsuppressed run reported 60 production violations and 18 test
violations. Those findings were resolved in code rather than hidden from the
rulesets. Both checked-in PMD rulesets now contain no violation-suppression
XPath expressions and no exact-symbol exception ledger.

LT-0113 established the zero-exception baseline. LT-0119 then split broad tests
and production responsibilities before tightening every size and method-level
complexity threshold shown above. The changes followed existing product
boundaries:

- web page rendering, embedded templates, route groups, authentication HTTP
  mechanics, and planned-workout parsing now have focused collaborators;
- hosted JDBC mapping, execution reads, training queries, schema validation,
  snapshot validation, and workout algorithms use smaller dedicated helpers;
- the web server tests are grouped by behavior, long integration scenarios use
  named phases, and the SQLite compatibility fixture separates queries, schema
  serialization, and writes.

The distribution table above records the pre-remediation measurement that
motivated LT-0113. It is historical evidence, not a list of current violations.
Current compliance is established by `pmdMain`, `pmdTest`, the executable
boundary probes, and the complete `qualityGate`.

## Running and tightening the policy

Run the full gate on Windows with:

```powershell
.\gradlew.bat qualityGate
```

Run just the static checks or executable boundary probes with:

```powershell
.\gradlew.bat pmdMain pmdTest
.\gradlew.bat test --tests com.lifttrax.quality.SourceSizePolicyTest
```

`SourceSizePolicyTest` invokes the checked-in PMD rules against generated
boundary sources. Production classes at 675 lines, production methods at 85,
test classes at 950, and test methods at 90 pass; each corresponding source one
line over fails its physical rule. Because the test and both PMD tasks are
dependencies of `qualityGate`, weakening or disconnecting the limits breaks the
normal local and CI proof.

This is a zero-exception ratchet. Tighten a numeric threshold only after
recording the new distribution and updating boundary probes where applicable.
Never raise a threshold, add an exclusion, or create a suppression merely to
make a build pass. Fix the code so the checked-in production and test rulesets
remain clean.
