# Catalog and history recovery drill - 2026-10-27

Engine: PostgreSQL

Recovery point: 2026-10-27T23:53:26.265110Z

Recovery time: 1050 ms

Observed post-snapshot window: 406 ms. One deliberately later execution was excluded as expected.

Restored table counts: {local_import_records=1, lifttrax_schema_migrations=12, exercise_catalog_entries=1, local_imports=1, executions=3, app_users=2, workout_submission_receipts=1, saved_workouts=2, lifter_profiles=2, execution_sets=7}

Validated two isolated account exports byte-for-byte, overlapping names, provenance, renamed and detached snapshots, ordered sets, submission receipts, saved workouts, and next execution identity. Manual steps: start disposable cluster and run test; provider auth/configuration and partial production restore remain outside this drill. No production data used.

## Environment and scope

This drill used PostgreSQL 17.6 in a disposable local loopback cluster, fresh
randomly named source and destination databases, and fixture data only. The
Windows binary bundle was fetched from the
[Zonky PostgreSQL binaries project](https://github.com/zonkyio/embedded-postgres-binaries)
through Maven Central (`embedded-postgres-binaries-windows-amd64:17.6.0`) and
verified against its published SHA-256 before extraction. The cluster is stopped
after verification. No hosted database or provider recovery point was used.

The snapshot contained migrations 1-12 and all ten application tables. The two
account exports were compared at the same fixed export timestamp, so their
complete JSON contents could be compared exactly. One account retained its
source identity through a rename and reclassification; the other had a deleted
catalog entry with retained execution history. A legacy import preserved its
source exercise metadata even though the current matching catalog definition
was different. The restored store also accepted a new execution with the next
available identity.

Failure tests cover missing and corrupt files, unsupported format/schema,
missing table-count metadata, incomplete migration history, broken ownership,
and an already initialized destination. These failures are exceptions, not a
successful recovery result. The separate real-Postgres migration test rejects
cross-account legacy links and malformed muscle data before schema changes,
then verifies that a repaired two-row backfill preserves performed labels
through a subsequent catalog rename.

This measures application-data restoration into an already running local
cluster. It is not a whole-service RTO, a provider PITR test, or a promise about
production backup frequency. The observed fixture window is not a production
RPO guarantee. Provider Auth, Storage, secrets, networking, service
configuration, and reviewed partial-account production repair remain manual
operator responsibilities. Start a disposable loopback cluster and set the
test admin URL to repeat the drill; see the
[recovery runbook](account-data-export-and-recovery.md#validated-operator-snapshot-restore).

## Repository validation

`./gradlew.bat qualityGate` passed: 337 tests, zero failures or skips,
92.34% instruction coverage, formatting and strict static analysis passed.
