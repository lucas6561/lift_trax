# Hosted Persistence Adapter

Date: 2026-06-16

## Purpose

LiftTrax can now run the core web logging workflow through a hosted
JDBC/Postgres persistence boundary while keeping SQLite as the default local
store.

The adapter is intentionally focused on the first hosted beta path:

- app users;
- default lifter profiles;
- exercise catalog entries;
- executions;
- execution sets.

This gives authenticated users separate hosted lift catalogs and training
history without forcing the CLI and every future persistence path to move at
once.

## Configuration

SQLite remains the default local web command:

```powershell
.\gradlew.bat runWeb --args="data/lifts.db 8080"
```

Hosted mode uses a separate web command:

```powershell
.\gradlew.bat runHostedWeb --args="data/lifts.db 8080"
```

`runHostedWeb` sets `lifttrax.dataStore=hosted-postgres` for that process.
The remaining hosted connection settings still come from secrets:

```text
LIFTTRAX_HOSTED_JDBC_URL=jdbc:postgresql://<host>:5432/<database>
LIFTTRAX_HOSTED_JDBC_USER=<database user>
LIFTTRAX_HOSTED_JDBC_PASSWORD=<database password>
```

The same hosted connection values can be supplied as system properties:

```text
lifttrax.hosted.jdbcUrl
lifttrax.hosted.jdbcUser
lifttrax.hosted.jdbcPassword
```

No hosted credentials should be committed.

## Runtime Behavior

`WebServerCli` uses `TrainingDataStoreProvider.fromEnvironment(...)` at
startup. In SQLite mode, requests still use the existing local database. In
hosted mode, each authenticated request resolves the signed `WebAuth` user into:

- an `app_users` row keyed by the stable auth subject;
- a default `lifter_profiles` row owned by that app user;
- a user-scoped `TrainingDataStore` for core web handlers.

The hosted adapter maps the current Java web routes' integer execution handles
to `executions.web_execution_id` while preserving a UUID `executions.id` for the
hosted schema. That keeps edit/delete routes working during the first hosted
slice without abandoning the planned UUID data model.

## Verification

`HostedPostgresTrainingDataStoreTest` runs against H2 in PostgreSQL mode. It
covers:

- core lift and execution reads/writes through the hosted adapter;
- cross-user visibility and mutation rejection;
- history, latest execution, lift detail, stats, and enabled-status reads;
- missing hosted JDBC configuration.

Run the focused path with:

```powershell
.\gradlew.bat test --tests com.lifttrax.db.HostedPostgresTrainingDataStoreTest
```

The final proof for hosted persistence changes remains:

```powershell
.\gradlew.bat qualityGate
```

## Local Database Import

The hosted import path validates a source SQLite database with the existing
backup/restore validator, fingerprints the file, and imports it into the signed
hosted account's default lifter profile.

Preview a local database without hosted credentials:

```powershell
.\gradlew.bat importHostedDatabase --args="data/lifts.db --preview"
```

Import into the configured hosted store:

```powershell
.\gradlew.bat importHostedDatabase --args="data/lifts.db --user <auth-user-id>"
```

Repeated imports of the same source database into the same hosted lifter profile
are skipped by `local_imports.source_fingerprint`. Individual source
`lift_records` are also tracked in `local_import_records` so future import work
can reason about duplicates by source table and local ID.
