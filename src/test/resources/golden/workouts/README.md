# Reviewed generated workout outputs

`GoldenWorkoutOutputTest` exercises the existing builders, exporter, serializer,
and importer with two representative two-week programs:

| Fixture | Behavior protected |
| --- | --- |
| `conjugate.json` | Alternating max-effort lifts, dynamic-effort percentage progression, warmup and accessory circuits, conditioning, and grouped repeated sets. |
| `hypertrophy.json` | Alternating primary lifts, rep and RPE targets, accessories, warmups, and repeated-set grouping. |

Both use the ordered in-memory catalog from
`ConjugateWorkoutBuilderTest.FakeDb.withParitySeedData()`, extended with a chest
fly accessory required by the hypertrophy builder. This catalog has no
execution history, wall-clock dates, or local database dependencies. The
conjugate builder and dynamic-lift source both receive
`RandomSupport.DETERMINISTIC`; neither uses interactive selection. Export time
is fixed at `2026-09-11T00:00:00Z`. Each test generates twice from fresh inputs
to verify repeatability. These fixtures cover the existing builder path while
the generic schema-to-wave builder (LT-0020) remains in the backlog.

Comparison uses parsed JSON: whitespace and object-key order are ignored.
Array order, numeric values, null/missing fields, metadata, and schema versions
remain significant. Never sort workout days, blocks, exercises, or sets to
hide an ordering regression. The expected files must also import to the same
typed planned workout. Focused behavioral tests remain the primary explanation
of individual training rules.

## Reviewing intentional changes

1. Run `./gradlew.bat test --tests com.lifttrax.workout.GoldenWorkoutOutputTest`.
   It writes candidates to `build/golden/workouts/` before comparing; it never
   overwrites these checked-in fixtures.
2. Inspect the candidate against the corresponding expected file, for example
   `git diff --no-index -- src/test/resources/golden/workouts/conjugate.json build/golden/workouts/conjugate.json`.
   Exit code 1 from this diff means the files differ. Confirm each change is
   intentional and keep or extend behavioral tests for altered training rules.
3. Copy only the approved candidate, for example in PowerShell:
   `Copy-Item -LiteralPath build/golden/workouts/conjugate.json -Destination src/test/resources/golden/workouts/conjugate.json`.
4. Rerun the focused test, review `git diff`, and run `./gradlew.bat qualityGate`.
   Commit the expected-output change with the implementation that explains it.

Do not update a golden solely to make a failing test pass. Generated candidates
stay in the ignored build directory, and no update flag runs implicitly in CI.
