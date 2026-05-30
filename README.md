# lift_trax

This repository now uses a **root-level Java/Gradle project layout**.

## Project structure

- `src/main/java` – application source code
- `src/test/java` – tests
- `build.gradle` / `settings.gradle` – Gradle project configuration
- `shared/sql` – shared SQL schema assets
- `shared/programs` - program schema and example training program assets
- `shared/workouts` - generated workout schema and example planned workout assets
- `docs` - project documentation, including program schema authoring guidance

## Run

From the repository root:

```bash
./gradlew run --args='path/to/lifts.db'
```

If no argument is provided, it defaults to repository-root `lifts.db` (auto-detected via `shared/sql/schema.sql`).

## Web UI

From the repository root:

```bash
./gradlew runWeb --args='path/to/lifts.db 8080'
```

- Arg 1: SQLite database path (defaults to repository-root `lifts.db`)
- Arg 2: port (defaults to `8080`)
- Server bind address: `0.0.0.0`

## Java project notes

See `JAVA_PROJECT.md` for detailed package and feature documentation.

## Program schema

Program schema v1 lives in `shared/programs/schema/program.schema.v1.json`.
Example conjugate and hypertrophy program files live in
`shared/programs/examples/`. Authoring guidance is in
`docs/program-schema-v1.md`.

## Workout file format

Generated workout format v1 lives in
`shared/workouts/schema/workout.schema.v1.json`. Example planned workout files
live in `shared/workouts/examples/`, and display/import guidance is in
`docs/workout-file-format-v1.md`.

The web UI imports planned workout JSON from the Import Workout tab.

Wave generation can still save markdown. To export a loadable planned workout
JSON file instead, use a `.json` output name:

```bash
java -cp build/libs/lift-trax-java-0.1.0.jar com.lifttrax.cli.WaveCli path/to/lifts.db 4 planned-workout.json
```

## Quality gate

Before considering a change shippable, run the single project quality gate from
the repository root:

```bash
./gradlew qualityGate
```

This checks Java formatting, runs PMD static analysis, runs the test suite, and
verifies the JaCoCo coverage threshold.

PMD scans production Java sources for correctness, security, best-practice,
style, maintainability, concurrency, and selected file/string performance risks.
Intentional rule exclusions are documented in `config/pmd/ruleset.xml`.

## Dump lifts only (no executions)

From the repository root:

```bash
./gradlew run --args='path/to/lifts.db --lifts-only'
```

This prints lift/exercise metadata (name, region, main lift type, muscles, notes) and skips execution history.
