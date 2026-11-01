# Source size and complexity ratchet

LiftTrax treats source size and complexity as change-prevention limits, not as
an assertion that every existing class is already small. `pmdMain` applies the
production policy in `config/pmd/ruleset.xml`; `pmdTest` applies the focused test
policy in `config/pmd/ruleset-test.xml`. Both tasks run through `staticAnalysis`
and the normal `qualityGate`.

## Enforced thresholds

| Source | Physical class | Physical method | Class NCSS | Method NCSS | Class cyclomatic | Method cyclomatic | NPath | Methods per class |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Production | 750 | 100 | 500 | 60 | 100 | 15 | 200 | 35 |
| Test | 1,000 | 100 | 700 | 60 | 100 | 15 | 200 | 50 |

The physical class rules cover classes, records, enums, and annotation types and
measure the complete declaration span, including comments, blank lines, and
embedded HTML, JavaScript, SQL, or other text blocks. Physical method rules also
cover ordinary, standard-constructor, and compact-constructor bodies. NCSS
measures executable source statements and catches dense code that a physical
line count alone can miss. PMD reports cyclomatic complexity at the configured
report level, so a method measured at 15 is a violation. NPath uses PMD's
documented 200-path warning point.

Production NCSS starts at 500 per class and 60 per method, as required by the
initial policy. Test classes get a 700-NCSS allowance because setup and
assertion code is intentionally more verbose, while their methods retain the
same 60-NCSS and 100-physical-line limits. The 35/50 method limits ignore
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

## Temporary production exceptions

Each row below maps to one exact class or method selector in the production
ruleset. For every row, the objective removal condition is that the named
symbol passes every listed rule at the thresholds above; the matching XPath
branch must be deleted in the same change. An exception never applies to an
entire package, file, renderer category, CLI category, database category, or to
future overloads.

These exceptions are temporarily necessary because safely separating the
listed behavior requires focused regression work outside this policy-only card;
the linked decomposition item owns that work and its removal proof.

| Exact symbol | Baseline violation | Decomposition item |
| --- | --- | --- |
| `CatalogImportPlan.preview/2` | NPath 205 | `LT-0117` |
| `DumpDatabaseCli.parseArgs/1` | NPath 257 | `LT-0115` |
| `ExecutionInputWidgetHtml.render/5` | 193 physical lines; cyclomatic 24; NPath 8,388,608 | `LT-0114` |
| `ExecutionInputWidgetHtml.renderWorkAlong/2` | 202 physical lines; cyclomatic 28; NPath 42,467,328 | `LT-0114` |
| `LiftDetailResponse.handle/2` | 104 physical lines; cyclomatic 16; NPath 486 | `LT-0114` |
| `PlannedWorkoutSessionHtml` | 2,084 physical lines | `LT-0114` |
| `PlannedWorkoutSessionHtml.renderResumePanel/1` | 111 physical lines | `LT-0114` |
| `PlannedWorkoutSessionHtml.appendScript/1` | 1,245 physical lines | `LT-0114` |
| `PlannedWorkoutSessionService.saveSubmittedResults/8` | 107 physical lines; cyclomatic 25; NPath 18,720 | `LT-0115` |
| `WebAuth` | cyclomatic 120; more than 35 non-accessor methods | `LT-0115` |
| `WebRouteRegistry.register/3` | 166 physical lines | `LT-0114` |
| `WebServerCli` | 1,555 physical lines; NCSS 697; cyclomatic 221; more than 35 non-accessor methods | `LT-0115` |
| `WebStartup.configuration/2` | cyclomatic 16; NPath 288 | `LT-0115` |
| `WebUiRenderer` | 2,882 physical lines; cyclomatic 210; more than 35 non-accessor methods | `LT-0114` |
| `WebUiRenderer.renderTabbedLayout/12` | 1,133 physical lines; NPath 512 | `LT-0114` |
| `WebUiRenderer.renderWavePlannerForm/3` | 176 physical lines; NCSS 65; NPath 872 | `LT-0114` |
| `WebUiRenderer.renderLastWeekContent/4` | NPath 240 | `LT-0114` |
| `WebUiRenderer.renderAddExecutionForm/4` | 101 physical lines | `LT-0114` |
| `WebUiRenderer.addExecutionSetLogFallbackScript/0` | 194 physical lines | `LT-0114` |
| `WebUiRenderer.renderExecutionList/5` | 120 physical lines | `LT-0114` |
| `WebUiRenderer.renderExecutionRows/2` | 119 physical lines; NPath 609 | `LT-0114` |
| `HostedLiftSharing.copy/3` | NPath 324 | `LT-0116` |
| `HostedPostgresTrainingDataStore` | 1,028 physical lines; cyclomatic 169; more than 35 non-accessor methods | `LT-0116` |
| `LocalPasswords.matches/2` | NPath 333 | `LT-0116` |
| `PostgresSqliteBackupService.applicationTables/0` | 128 physical lines | `LT-0116` |
| `SnapshotIntegrity.validate/1` | 104 physical lines; cyclomatic 23; NPath 9,900 | `LT-0116` |
| `PlannedWorkoutExporter.blockType/3` | NPath 288 | `LT-0117` |
| `PlannedWorkoutMarkdownWriter.appendBlock/3` | NPath 257 | `LT-0117` |
| `PlannedWorkoutText.plannedSet/1` | NPath 256 | `LT-0117` |
| `ProgramSchemaValidator` | cyclomatic 127 | `LT-0117` |
| `WarmupLoading.choose/4` | cyclomatic 19; NPath 27,648 | `LT-0117` |
| `WarmupLoading.plateCandidates/3` | NPath 216 | `LT-0117` |
| `WaveMarkdownWriter.workoutLines/2` | cyclomatic 15 | `LT-0117` |
| `WorkingSetWarmups.template/1` | NPath 320 | `LT-0117` |
| `WorkoutHistoryFormatter.trainingMaxValue/1` | cyclomatic 19; NPath 1,968 | `LT-0117` |

## Temporary test exceptions

The same objective removal rule applies: make the exact symbol pass every
listed limit, remove its XPath branch, and keep `pmdTest` clean.

| Exact symbol | Baseline violation | Decomposition item |
| --- | --- | --- |
| `CatalogRecoveryDrillTest.drill/3` | 119 physical lines; NCSS 61 | `LT-0118` |
| `HttpsProxyIntegrationTest.trustedTlsPreservesAuthenticationIsolationCsrfAndWorkoutSubmission/0` | 213 physical lines; NCSS 75 | `LT-0118` |
| `LocalMultiUserTest.twoLocalAccountsCanRegisterLogIndependentlyAndSwitchWithoutSavingAnOldForm/0` | 133 physical lines | `LT-0118` |
| `PlannedWorkoutSessionHtmlTest.sessionPageStartsWithEmptyCompletedSetsAndAllowsWorkoutFileSwaps/0` | 168 physical lines; NCSS 134 | `LT-0118` |
| `WebServerCliTest` | 2,726 physical lines; NCSS 1,164; more than 50 non-accessor methods | `LT-0118` |
| `WebServerCliTest.renderTaskLayoutKeepsSecondaryViewsAndExistingFlows/0` | 133 physical lines; NCSS 82 | `LT-0118` |
| `WebServerCliTest.liftCrudFragmentAndDetailRoutesCoverSuccessfulAndInvalidRequests/0` | 103 physical lines | `LT-0118` |
| `WebServerCliTest.plannedWorkoutRoutesCoverPreviewDownloadsResumeAndIdempotentEmptySaves/0` | 110 physical lines | `LT-0118` |
| `SqliteDb` | 1,252 physical lines; cyclomatic 209; more than 50 non-accessor methods | `LT-0118` |
| `SqliteDb.parseMetric/1` | NPath 2,304 | `LT-0118` |

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
boundary sources. Production classes at 750 lines, production methods at 100,
test classes at 1,000, and test methods at 100 pass; each corresponding source
one line over fails its physical rule. Because the test and both PMD tasks are
dependencies of `qualityGate`, weakening or disconnecting the limits breaks the
normal local and CI proof.

This is a ratchet. When decomposition lowers a hotspot, remove or narrow its
exception immediately. Tighten a numeric threshold only after recording the
new distribution and updating boundary probes where applicable. Never raise a
threshold, add a broad exclusion, or silently regenerate an exception list to
make a build pass. A genuinely necessary temporary exception requires an exact
symbol selector, measured value, focused backlog item, reason, and objective
removal condition in this document.
