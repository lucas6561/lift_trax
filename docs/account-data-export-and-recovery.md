# Account Data Export and Hosted Recovery

LiftTrax has two deliberately separate data-protection paths:

- a signed-in user can download only that account's portable training data;
- an operator can protect and recover the complete hosted Postgres service.

An account export is not a database backup. An operator snapshot contains all
accounts and must never be given to an individual user as their export.

## Downloading account data

Open **Account** and choose **Download my data**. LiftTrax returns a JSON file
named with the UTC export time, such as
`lifttrax-account-20261027-153045Z.json`.

The request is authenticated and resolves the data store from the signed-in
account. It does not accept an account ID, username, date range, or alternate
format. Unknown parameters and formats are rejected instead of being ignored.
The response is marked `no-store` because lift notes, workout notes, and
execution history are private data.

The export contains:

- the account's complete lift catalog, including disabled lifts and catalog
  notes;
- every execution and ordered set, including flags, notes, RPE, weights,
  misses, and all supported metric kinds;
- every saved planned-workout document and its LiftTrax schema version; and
- the optional public LiftTrax username.

It excludes other accounts, authentication IDs, email addresses, password
data, session tokens, sharing settings, server-side import bookkeeping, and
provider-managed records. Program and workout files that exist only on a
user's device are not hosted records and therefore cannot be included. A saved
planned workout includes its complete embedded source and metadata.

Account exports can be inspected and archived now. Importing a full account
export is intentionally not automated yet; recovery into the hosted service is
a support/operator operation so ownership mappings and existing records can be
validated before writes occur.

## Versioned JSON contract

The current contract is
`shared/account-export/schema/account-export.schema.v2.json`.
Consumers that intentionally follow the newest contract can use
`shared/account-export/schema/account-export.schema.latest.json`; the latest
file is byte-for-byte identical to the newest numbered schema.

Top-level fields are:

| Field | Meaning |
| --- | --- |
| `schemaVersion` | Account-export contract version. |
| `exportedAt` | UTC time at which the export was assembled. |
| `account` | Portable account metadata; currently only the optional username. |
| `catalog` | Account-owned lift definitions, ordered by name. |
| `executions` | Training history, ordered by date, lift name, and execution ID. |
| `plannedWorkouts` | Saved planned-workout documents, ordered by creation time and ID. |

Version 2 adds catalog `provenance` (origin, optional source namespace/key and
revision, and optional shared-source public label) and execution
`performedExercise` (name, region, main type, muscles, and catalog notes).
`liftName` remains the performed name. Deleted catalog entries do not remove
executions from the export. The frozen v1 schema remains available for older
exports; full-account import remains a reviewed operator operation.

Each object in `plannedWorkouts` has an export record ID, display name,
creation time, and `document`. The document remains governed by its own
`schemaVersion` and the matching planned-workout schema.

Treat an export file as sensitive personal data. Store it somewhere the user
controls and delete unwanted copies from shared download folders.

## Hosted backup expectations

Before production exposure, the operator must record the Supabase plan and
confirm the actual backup window in **Database > Backups**. Supabase's current
[database backup documentation](https://supabase.com/docs/guides/platform/backups)
describes daily backups for paid plans, optional point-in-time recovery, and a
provider restore that makes the project unavailable during recovery. Features
and retention are plan-dependent, so this repository does not treat a plan
name as proof that a usable restore point exists.

The official guidance recommends independent logical exports for projects
without provider backups. LiftTrax's equivalent application-data control is:

```powershell
.\gradlew.bat postgresSqliteBackup --args="data/backups/lifttrax-snapshot.db"
```

This creates a validated, all-account SQLite snapshot of LiftTrax-owned tables
from one repeatable-read Postgres transaction. Store a copy outside Render and
outside the Supabase project. It does not include Supabase Auth, Storage
objects, provider configuration, or secrets. If those features are used, the
operator must protect and recover them through their provider-specific paths.

Required operating checks:

1. Before each schema migration or hosted release, confirm a provider restore
   point or create and retain a fresh LiftTrax operator snapshot.
2. At least monthly, record the newest available provider restore point and
   verify that an off-provider snapshot can be opened and validated.
3. After a schema change, create a new snapshot; do not rely only on an older
   schema version.
4. Before deleting or replacing a Supabase project, copy required backups out
   of the project. Provider documentation states that project deletion removes
   its associated backups.
5. Never expose an all-account operator snapshot through an HTTP route.

## Recovery runbook

### Triage and preserve

1. Stop or restrict application writes if continued use could make the
   incident worse.
2. Record the incident time in UTC, affected accounts/tables, current deployed
   commit, and current `lifttrax_schema_migrations` rows.
3. Preserve the current database before restoring anything. Create an operator
   snapshot when the database is readable, even if it contains the bad state.
4. Identify the closest verified restore point before the bad write or
   corruption. State the expected recovery-point data loss explicitly.

### Recover one account or selected rows

Do not replace production with an older whole-project backup just to recover a
small number of rows. Restore or clone the chosen provider backup into an
isolated recovery project when the configured plan supports it. Supabase
documents [restoring a backup to a new project](https://supabase.com/docs/guides/platform/clone-project)
as a database-level copy that still requires application configuration review.

In the isolated project:

1. locate the user through `app_users`, then its owned `lifter_profiles`;
2. trace catalog, execution, execution-set, saved-workout, submission-receipt,
   and import rows through those ownership keys;
3. compare the candidate rows with the user's latest account export when one
   is available;
4. copy only reviewed rows into production in a transaction, preserving
   foreign-key order and rejecting ownership mismatches; and
5. run the authenticated account-isolation and core logging smoke checks.

There is no generic automated partial-restore command. Use a reviewed incident
script or provider support and retain its exact SQL, row counts, and reviewer.

### Recover the whole service

1. Announce downtime and stop Render traffic or writes.
2. Use the Supabase dashboard to restore the selected daily/PITR point, or
   provision a replacement project from the verified backup when an in-place
   restore is inappropriate.
3. Reapply non-database configuration that the chosen restore path does not
   carry: Render secrets, provider settings, API keys, redirects, and any
   external storage or functions.
4. Confirm migration versions and LiftTrax-owned table counts before enabling
   traffic.
5. Run anonymous auth redirects, authenticated account isolation, lift logging,
   execution history, saved-workout, and account-export smoke checks.
6. Reopen traffic only after the restored service passes those checks. Keep the
   preserved bad-state snapshot until the incident review is complete.

Document the selected restore point, observed data-loss window, validation
results, and any user communication after every recovery.

## Validated operator snapshot restore

The catalog/history lifecycle is implemented in migrations 0011 (performed
snapshots) and 0012 (portable provenance). See
[ADR-0005](adr/0005-catalog-history-lifecycle.md). Use a snapshot taken after
those migrations; the restore command requires an exact application schema
version. To investigate an older snapshot, use its matching release in an
isolated environment before advancing migrations.

Create a new isolated Postgres database. Its cluster must provide the `anon`
and `authenticated` roles used by the security migrations (Supabase normally
provides them). For a standalone disposable cluster, create those non-login
roles first if absent; the automated real-Postgres drill does this itself.
Configure its dedicated connection
with `LIFTTRAX_RECOVERY_JDBC_URL`, `LIFTTRAX_RECOVERY_JDBC_USER`, and
`LIFTTRAX_RECOVERY_JDBC_PASSWORD`, then run:

```powershell
.\gradlew.bat postgresRecovery --args="data/backups/verified-snapshot.db --confirm-isolated-target"
```

The recovery command does not read the normal hosted connection configuration.
It rejects an initialized destination, even if its application tables are empty.
It validates the artifact before initializing the destination: SQLite integrity,
format and schema versions, migration order and names, exact table counts,
ownership, live-reference integrity, catalog provenance, performed metadata,
ordered set indices, saved-workout JSON, and import/receipt relationships.
It then restores every application table in dependency order, checks those
invariants again, resets the next execution identity, and commits. Failed
restores must never receive traffic. Use a new empty database for another
attempt and retain the failed target for investigation as needed.

The command reports the UTC recovery point, elapsed restoration time, and
restored table counts. Before reopening traffic, compare both fixture/affected
account exports, check account isolation, and run authenticated logging and
saved-workout smoke checks. Provider auth, storage, secrets, redirects, and
service configuration remain separate manual recovery steps. Partial-account
production repair still requires a reviewed incident script.

The repeatable drill is `CatalogRecoveryDrillTest`. Its normal run covers a
compatibility database; the real Postgres run is enabled explicitly with
`LIFTTRAX_TEST_POSTGRES_ADMIN_URL` pointing to a disposable loopback cluster
(`jdbc:postgresql://127.0.0.1:<port>/postgres`, test-only postgres user). It creates
and drops uniquely named source and destination databases. It never reads the
hosted connection. Run:

```powershell
.\gradlew.bat test --tests '*CatalogRecoveryDrillTest'
```

Reports are written under `build/reports/recovery-drill-*.md`. They record the
engine, point, elapsed time, observed snapshot window, counts, and manual steps
without connection secrets. The fixture includes two accounts with overlapping
exercise names, changed and deleted definitions, portable provenance, legacy
imports, all ordered sets, receipts, and saved workouts. Account exports before
backup and after restore must match byte-for-byte at the same export timestamp;
a deliberately later execution must be absent. Missing, corrupt, unsupported,
and incomplete artifacts must fail before destination initialization.

See the [recorded October 27 drill](recovery-drill-2026-10-27.md) for measured
results and the limits of this application-data test.
