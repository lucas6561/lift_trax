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
`LT-0038` will turn the highest-value rules into executable architecture checks.

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
