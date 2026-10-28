# Postgres runtime and SQLite operator snapshots

Date: 2026-07-18

## Runtime boundary

Postgres is the only LiftTrax runtime database. Local web, hosted web, catalog
and execution inspection, workout-wave generation, planned-workout session
saves, local database import targets, and administrative database operations all
connect through `TrainingDataStoreProvider` to the configured Postgres database.

Program definitions and planned-workout definitions remain schema-versioned
files, so loading, previewing, printing, and exporting those definitions are
database-neutral. Starting or saving a planned-workout session writes its
completed executions through the same Postgres store as manual logging.

The legacy SQLite schema remains readable only for an explicit one-way local
database import and for tests. No normal executable accepts a SQLite path as a
live store. A validated operator artifact can be restored into a new isolated
Postgres target; SQLite is never a runtime or synchronization store.

## Executable inventory

| Java main / Gradle task | Classification | Database behavior |
| --- | --- | --- |
| `WebServerCli` / `runWeb` | Postgres-backed | Uses local auth configuration and configured Postgres; optional argument is only the port. |
| `WebServerCli` / `runHostedWeb` | Postgres-backed | Uses hosted configuration and configured Postgres; optional argument is only the port. |
| `DumpDatabaseCli` / `run` | Postgres-backed | Uses the configured username/auth ID unless `--user` overrides it, then prints that account's catalog/history. |
| `WaveCli` / `generateWave` | Postgres-backed | Uses the configured username/auth ID unless `--user` overrides it while generating a file. |
| `ImportHostedDatabaseCli` / `importHostedDatabase` | Postgres-backed target | Reads a legacy SQLite source without modifying it and writes only to Postgres. |
| `PostgresSqliteBackupCli` / `postgresSqliteBackup` | Postgres-backed operator command | Reads every LiftTrax-owned Postgres table and creates a non-runtime SQLite artifact. |

## Configuration and secrets

Use these environment variables, or the corresponding
`lifttrax.hosted.*` properties in an ignored configuration file:

```text
LIFTTRAX_HOSTED_JDBC_URL
LIFTTRAX_HOSTED_JDBC_USER
LIFTTRAX_HOSTED_JDBC_PASSWORD
```

Copy `config/lifttrax-hosted.example.properties` to
`config/lifttrax-hosted.properties` for a local operator configuration. Never
commit credentials. `config/lifttrax-local.properties` deliberately contains no
database secret and can receive credentials from the environment.

## Ordered application schema

`shared/postgres/migrations/index.txt` is the ordered migration manifest.
Applied migrations are recorded in `lifttrax_schema_migrations`. The current
application schema contains:

- `app_users` and `lifter_profiles`;
- `exercise_catalog_entries`;
- `executions` and `execution_sets`;
- `local_imports` and `local_import_records`;
- `saved_workouts` and `workout_submission_receipts`;
- `lifttrax_schema_migrations`.

Supabase `auth`, storage, and provider-internal schemas are not LiftTrax-owned
application tables and are intentionally excluded.

Migration `0004__secure-public-tables.sql` enables Row Level Security on every
LiftTrax-owned table in the exposed `public` schema and removes Data API access
for the Supabase `anon` and `authenticated` roles. The Java runtime continues
to connect directly with its protected JDBC credentials; no browser client has
direct table access.

## Creating a SQLite snapshot

```powershell
.\gradlew.bat postgresSqliteBackup --args="data/backups/lifttrax-snapshot.db"
```

An existing destination is rejected. Replacing a verified destination requires
an explicit confirmation:

```powershell
.\gradlew.bat postgresSqliteBackup --args="data/backups/lifttrax-snapshot.db --confirm-overwrite"
```

The destination argument is a base filename. The published filename includes
the same UTC creation time stored in snapshot metadata, such as
`lifttrax-snapshot-20260718-143522Z.db`.

The command:

1. opens one repeatable-read Postgres transaction;
2. copies every LiftTrax-owned table, including all users and ownership IDs, to
   a uniquely named temporary SQLite file;
3. records the snapshot creation time, format version, Postgres schema version,
   and expected row count for each table;
4. reopens and validates the SQLite artifact against the expected tables and
   row counts;
5. atomically publishes the destination only after validation succeeds.

On failure it rolls back the Postgres read transaction, removes the temporary
artifact, leaves any existing destination untouched, and exits nonzero with the
destination and underlying cause. The SQLite file is a point-in-time inspection
and recovery artifact, not an application database. Restoring Postgres remains
a deliberate provider/operator recovery procedure. The explicit `postgresRecovery`
command accepts a validated current-version snapshot and a separately configured
new isolated target. See [the recovery runbook](account-data-export-and-recovery.md).

## Catalog and history lifecycle

Migrations 0011 and 0012 separate mutable catalog definitions from performed
exercise snapshots and add optional portable source identity/provenance.
History and account exports use snapshots; deletion detaches current catalog
references without deleting completed records. Normal logging requires an
enabled owned catalog entry. See [ADR-0005](adr/0005-catalog-history-lifecycle.md)
and the [catalog file guide](exercise-catalog-import.md).
