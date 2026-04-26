# lift_trax

This repository now uses a **root-level Java/Gradle project layout**.

## Project structure

- `src/main/java` – application source code
- `src/test/java` – tests
- `build.gradle` / `settings.gradle` – Gradle project configuration
- `shared/sql` – shared SQL schema assets

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

## Dump lifts only (no executions)

From the repository root:

```bash
./gradlew run --args='path/to/lifts.db --lifts-only'
```

This prints lift/exercise metadata (name, region, main lift type, muscles, notes) and skips execution history.
