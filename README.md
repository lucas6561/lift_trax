# lift_trax

This repository now uses a **root-level Java/Gradle project layout**.

## Project structure

- `src/main/java` – application source code
- `src/test/java` – tests
- `build.gradle` / `settings.gradle` – Gradle project configuration
- `shared/postgres` - ordered Postgres application migrations
- `shared/sql` – legacy SQLite import/test schema assets
- `shared/programs` - program schema and example training program assets
- `shared/workouts` - generated workout schema and example planned workout assets
- `docs` - project documentation, including program schema authoring guidance

## Run

From the repository root:

```bash
./gradlew run --args='--user local-user'
```

All database-aware commands use the configured Postgres database. Set
`LIFTTRAX_HOSTED_JDBC_URL`, `LIFTTRAX_HOSTED_JDBC_USER`, and
`LIFTTRAX_HOSTED_JDBC_PASSWORD`, or place equivalent properties in the selected
configuration file. Do not commit a file containing credentials.

## Web UI

From the repository root:

```bash
./gradlew runWeb --args='8080'
```

- Arg 1: port (defaults to `8080`)
- Server bind address: `0.0.0.0`

The hosted command has the same Postgres-only runtime behavior but selects the
ignored hosted configuration file:

```bash
./gradlew runHostedWeb --args='8080'
```

`runHostedWeb` reads `config/lifttrax-hosted.properties`, which is ignored by
Git so local database credentials do not get committed. Start from
`config/lifttrax-hosted.example.properties`, fill in the hosted JDBC settings,
then run the command above. Neither web command accepts a SQLite path.

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
./gradlew generateWave --args='4 planned-workout.json'
```

User-scoped local CLI commands read their default account from an optional,
machine-specific config. Copy
`config/lifttrax-local.override.example.properties` to
`config/lifttrax-local.override.properties` and set:

```properties
lifttrax.config.include=lifttrax-hosted.properties
lifttrax.cli.userId=your-username
```

The override file is ignored by Git and layered over
`config/lifttrax-local.properties`. The include reuses the JDBC URL, username,
and password from the existing ignored `config/lifttrax-hosted.properties`, so
credentials are not duplicated. The value can be a LiftTrax username or the
underlying authentication ID. Passing `--user <username-or-id>` or setting
`LIFTTRAX_CLI_USER_ID` still overrides the local default.

## Database schema migrations

Runtime Postgres migrations live in `shared/postgres/migrations/`. LiftTrax
applies pending files in numeric filename order and records them in
`lifttrax_schema_migrations` before serving requests or running a database-aware
operator command.

To add a migration:

1. Add a file named `NNNN__short-description.sql` under
   `shared/postgres/migrations/`, using the next schema version.
2. Add the filename to `shared/postgres/migrations/index.txt`.
3. Add or update a Postgres-mode integration test for the changed schema.

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

## Mutation testing

Core workout wave generation has a focused PIT mutation-test target. Run it
locally from the repository root:

```bash
./gradlew pitest
```

The first target slice is `com.lifttrax.workout.ConjugateWorkoutBuilder` and
`com.lifttrax.workout.WaveMarkdownWriter`, exercised by the workout test suite.
PIT writes repeatable HTML and XML reports to `build/reports/pitest/`; surviving
mutations in that report are the backlog for future assertion tightening. The
initial thresholds are intentionally modest so mutation testing can land without
requiring every workout package to be mutation-clean immediately.

## Dump lifts only (no executions)

From the repository root:

```bash
./gradlew run --args='--lifts-only'
```

This uses the locally configured CLI user and prints lift/exercise metadata
(name, region, main lift type, muscles, notes) without execution history.
Disabled lifts are omitted by default. Include the entire catalog with:

```bash
./gradlew run --args='--lifts-only --include-disabled'
```

Use `--user <username-or-id>` to select a different account for one invocation.

## Database backups

LiftTrax can create a complete point-in-time SQLite `.db` artifact from the
configured Postgres database. This is an operator snapshot of every
LiftTrax-owned table and every user's rows; it is not a live database, restore
command, or synchronization mode. The supplied destination is a base filename;
the published file always includes its UTC creation date and time, for example
`lifttrax-snapshot-20260718-143522Z.db`.

Create a backup:

```bash
./gradlew postgresSqliteBackup --args='data/backups/lifttrax-snapshot.db'
```

The command refuses to overwrite the resulting timestamped destination. After
verifying the path, an operator can explicitly replace it:

```bash
./gradlew postgresSqliteBackup --args='data/backups/lifttrax-snapshot.db --confirm-overwrite'
```

The snapshot is read from one repeatable-read Postgres transaction, written to
a temporary SQLite file, validated by expected tables and per-table row counts,
then atomically published. Failed snapshots remove the temporary artifact.
Provider-level Postgres restoration remains an operator/provider procedure.
