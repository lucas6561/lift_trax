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

The stable latest program schema is available at
`shared/programs/schema/program.schema.latest.json`. New program files should
use the latest supported version. Frozen numbered snapshots remain under
`shared/programs/schema/` so old files stay loadable. Example conjugate and
hypertrophy program files live in `shared/programs/examples/`. The v1 shape
guidance is in `docs/program-schema-v1.md`, and the compatibility policy is in
`docs/schema-versioning.md`.

## Workout file format

The stable latest generated workout format is available at
`shared/workouts/schema/workout.schema.latest.json`. New exports use the latest
supported version automatically. Frozen numbered snapshots remain under
`shared/workouts/schema/` so old workout files stay importable. Example planned
workout files live in `shared/workouts/examples/`, display/import guidance is
in `docs/workout-file-format-v1.md`, and the compatibility policy is in
`docs/schema-versioning.md`. If you want another AI tool to create an importable
workout JSON file, start with `docs/ai-workout-schema-guide.md`.

The Workout Waves and Import Workout tabs share the same planned-workout output
options: app preview, print view, Markdown download, and workout JSON download.
The print view uses a compact white layout with print-friendly page breaks.
After previewing an imported file, choose `Start This Day` for the workout you
want to train.
The follow-along screen seeds the planned set counts, accepts completed reps,
left/right reps, seconds, distance, weight, and RPE, and saves completed work
into normal execution history. Workout-file swap choices are available when the
matching lifts exist locally.

Wave generation can still save markdown. To export a loadable planned workout
JSON file instead, use a `.json` output name:

```bash
java -cp build/libs/lift-trax-java-0.1.0.jar com.lifttrax.cli.WaveCli path/to/lifts.db 4 planned-workout.json
```

## Database schema migrations

SQLite migrations live in `shared/sql/migrations/`. When LiftTrax opens a
database, it applies pending files in numeric filename order, records applied
versions in the `schema_migrations` table, and reports the active version through
SQLite's `PRAGMA user_version`.

To add a migration:

1. Add a file named `NNNN__short-description.sql` under
   `shared/sql/migrations/`, using the next schema version.
2. Add the filename to `shared/sql/migrations/index.txt`.
3. Update `shared/sql/schema_version.txt` and the current schema snapshot in
   `shared/sql/schema.sql`.
4. Add or update a database test for the changed schema.

Do not edit a migration after it has shipped. Add a new migration instead.

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
