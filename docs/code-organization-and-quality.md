# Code organization and quality standards

LiftTrax keeps business rules independent from delivery and persistence details
so important training behavior can be tested without starting a web server or a
database. Keep these boundaries simple and prefer small, feature-focused
classes over central classes that accumulate unrelated responsibilities.

## Package responsibilities

| Package or area | Responsibility |
| --- | --- |
| `com.lifttrax.models` | Small immutable training values such as lifts, executions, sets, weights, regions, and muscles. Models must not depend on the web, CLI, or a concrete database. |
| `com.lifttrax.workout` | Program schemas, workout generation, planned-workout files, progression, and history interpretation. It may use model and datastore contracts, but must not render HTTP pages or parse web forms. |
| `com.lifttrax.db` | Persistence contracts, Postgres implementations, migrations, imports, and backups. Database code converts stored rows to models and must not contain HTML or route behavior. |
| `com.lifttrax.cli` | Command entry points and the embedded web delivery layer. Route registration, HTTP handling, authentication, and page rendering should remain separate concerns even though they share this package today. |
| `com.lifttrax.config` | Environment, system-property, and local configuration resolution. It must not own product behavior. |
| `shared/` | Versioned SQL migrations, schemas, and portable examples consumed across runtime and tooling boundaries. |

New code should point dependencies toward models and explicit contracts. Domain
or workout code must not depend on `cli`; models must not depend on `db`,
`workout`, or `cli`; and HTTP page rendering must not issue SQL directly.
`PackageDependencyTest` enforces the highest-value rules through
[ArchUnit](https://www.archunit.org/userguide/html/000_Index.html) bytecode checks
as part of the normal JUnit suite and `qualityGate`:

- Models cannot depend on delivery, persistence, workouts, configuration, SQL,
  or the embedded HTTP server.
- Workout code cannot depend on the CLI/web layer, HTTP server, or SQL. Its
  dependencies into `db` are limited to `Database` and `TrainingDataStore`.
- Program schema classes and planned-workout file, JSON, and version contracts
  cannot depend on persistence, including datastore interfaces.
- Persistence cannot depend on delivery, workout logic, or the HTTP server.
- HTML renderers cannot use JDBC or concrete SQL driver APIs.
- Configuration cannot depend on application models or product layers.

The checks inspect Gradle's production class directories explicitly because
this build also compiles main sources into test output. Empty scans fail.
Violations report the rule, offending classes, and dependency locations.
Run just these checks with
`./gradlew.bat test --tests com.lifttrax.architecture.PackageDependencyTest`.

There are no suppressed violations of these rules. Current boundaries still
allow renderers to read history through `Database`, account rendering to use
`AccountProfile`, and CLI startup to construct concrete datastores. The legacy
Swing selectors/editors still live in `workout`; moving desktop presentation
out of that package is deferred, so this first rule set forbids HTTP delivery
dependencies there but does not forbid Swing. No broad package relocation is
required for these checks.

## Test expectations

- Model and workout changes need focused unit tests for happy paths, boundaries,
  invalid input, and meaningful alternatives.
- Database changes need integration tests against isolated data, including user
  ownership, migrations, failed writes, and round-trip mapping.
- CLI argument changes need parser tests for defaults, valid combinations, and
  specific errors.
- Web handlers need route tests covering the HTTP method, response status,
  meaningful content or redirect state, authorization, and writes against
  isolated data.
- HTML renderers need focused string/structure tests; use a browser smoke check
  when JavaScript interaction or responsive layout is materially changed.
- Schema changes need valid and invalid fixtures, version-dispatch tests, and
  confirmation that the latest alias matches the newest numbered schema.
- Generated workout changes must also pass `GoldenWorkoutOutputTest`; its
  reviewed conjugate/hypertrophy outputs and intentional-update workflow live
  in `src/test/resources/golden/workouts/README.md`.

Tests should assert behavior rather than implementation trivia. Production code
is subject to the repository-wide 90% instruction-coverage gate; new code is
expected to bring its own coverage instead of relying on unrelated tests.

## Required checks

Run `./gradlew.bat qualityGate` on Windows or `./gradlew qualityGate` elsewhere.
The gate checks Google Java Format through Spotless, strict PMD analysis, JUnit,
and repository-wide JaCoCo instruction coverage. GitHub Actions runs the same
command for every pull request and push to `main`, and preserves the test, PMD,
and JaCoCo reports.

Run `./gradlew.bat pitest` when changing mutation-tested workout-file or active
workout-save behavior. Review survivors in `build/reports/pitest/`; do not lower
thresholds merely to make a change pass.

## Naming and change shape

- Use action-oriented names for commands and services, value-oriented names for
  records, and page-oriented names ending in `Html` for renderer-only classes.
- Keep route registration, HTTP orchestration, rendering, domain behavior, and
  persistence in separate methods or classes.
- Prefer immutable records and copied collections at boundaries.
- Add narrowly scoped helpers only when they express a reusable concept; avoid
  moving unrelated behavior into generic utility classes.
- Never put credentials or personal machine defaults in tracked source files.

## When to add an ADR

Add or update an architecture decision record when a change selects or replaces
a platform, persistence model, authentication or ownership boundary, schema
compatibility policy, deployment shape, or durable cross-package dependency
direction. Routine implementation, a local refactor within an established
boundary, or a reversible test-tool adjustment does not require an ADR.
