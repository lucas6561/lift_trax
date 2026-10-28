# 0005: Separate catalog and performed-history lifecycles in Postgres

Date: 2026-10-27

## Status

Accepted (LT-0076)

## Decision

Catalog and completed training remain in the single Postgres database. Every
catalog row belongs to an account and its lifter profile. Executions belong to
the lifter profile independently of the current catalog.

An execution captures `performed_catalog_id`, `performed_exercise_name`,
`performed_region`, `performed_main_lift`, `performed_muscles`, and
`performed_catalog_notes`. These fields describe the exercise at the time of
performance. The immutable performed ID is a historical identity, not a foreign
key. `catalog_entry_id` is an optional live navigation/hint reference. History
display, date queries, filtering, recent activity, and exports use snapshots.
Current catalog detail and workout hints can follow the live reference across
a rename. A newly created row with a reused name does not inherit deleted-row
history. Historical-name detail remains available through performed snapshots. Recent
history links carry an execution handle so a reused name cannot redirect an
older performance to an unrelated current definition.

Logging resolves and locks an enabled, account-owned catalog row, then writes
the snapshot and ordered sets in one transaction. Editing dates, flags, notes,
or sets preserves the exercise snapshot. Explicit legacy SQLite imports capture
the source exercise metadata, including when matching an existing local name.
Snapshot backfill requires an ownership-valid catalog match for every row;
missing, cross-account, or malformed metadata aborts the migration. Backfill can
only preserve metadata still present at migration time; earlier renames cannot
be reconstructed.

Rename, reclassification, muscles, notes, and disable affect only the live
catalog. Disable prevents new logging. Delete detaches the live reference in
the same transaction and retains executions and sets. Duplicate-name rename
and implicit merge are refused: choose a distinct name or disable the duplicate.
There is no operation that rewrites past snapshots to merge history.

Internal row IDs identify account-owned records. Optional portable identity is
the `(source_namespace, source_key)` pair, unique within a lifter profile.
`origin`, `source_revision`, and `copied_from_label` describe provenance; they
do not grant authorization. Existing rows are `unknown`, new manual rows are
`user_created`, explicit starter files are `starter`, shared copies are
`shared_copy`, and explicit catalog files are `file_import`. Shared identity
uses a SHA-256 digest of a random source catalog UUID, never a private account,
profile, authentication, or raw source row ID. The public username is an audit
label and is not an identity or authorization key. Copies own their data.

File import matches portable identity first, surviving renames. A name collision
with another identity is a conflict. Without source identity, an exact unchanged
definition can be skipped, but differing name matches are conflicts and never
overwrite silently. Duplicate normalized names or source pairs in a file are
errors. Preview labels new, unchanged, changed, skipped, and conflicting rows;
explicit confirmation revalidates the preview against current catalog state in
one transaction. Program/workout imports never mutate catalog definitions.

## Migration and recovery

Completed implementation order: [LT-0077](../../tasks/completed/LT-0077-add-postgres-exercise-snapshots.md)
snapshots, [LT-0078](../../tasks/completed/LT-0078-add-catalog-identity-provenance.md)
portable provenance, [LT-0079](../../tasks/completed/LT-0079-make-catalog-changes-history-safe.md)
safe mutations, [LT-0080](../../tasks/completed/LT-0080-drill-catalog-history-recovery.md)
isolated restore drill, then [LT-0093](../../tasks/completed/LT-0093-import-exercise-catalog-files.md)
explicit catalog-file import. No workout schema change or second runtime is needed.

Account exports include all snapshots, even detached executions, plus owned
catalog provenance without server ownership keys. Evolve the export contract
with a new numbered version while retaining v1. Operator snapshots carry all
application rows, provenance, snapshots, and migration versions. Restore into
an empty isolated target, validate counts, relationships, ordering, and account
exports before reporting recovery. See the
[account export and recovery runbook](../account-data-export-and-recovery.md).
Provider auth/configuration recovery and reviewed partial-account restore
remain manual operator responsibilities.
