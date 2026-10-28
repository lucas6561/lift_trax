# Exercise catalog files

Open **Add Execution → Import exercise catalog file**. Select a JSON file or
paste its contents, choose **Preview catalog**, review every row, then choose
**Confirm and save catalog**. Preview never writes. Confirmation checks the
file and current catalog again and saves all changes in one transaction.
If the catalog changed after preview, preview again. A failed save retains the
previous catalog and completed history.

The stable schema entrypoint is
`shared/exercise-catalog/schema/exercise-catalog.schema.latest.json`.
The frozen v1 contract is `exercise-catalog.schema.v1.json` in the same folder.
A representative file is
`shared/exercise-catalog/examples/exercise-catalog-v1.json`.

## File contract

```json
{
  "schemaVersion": 1,
  "exercises": [{
    "name": "Paused Bench Press",
    "enabled": true,
    "region": "UPPER",
    "main": "BENCH_PRESS",
    "muscles": ["CHEST", "TRICEP"],
    "notes": "Pause on the chest.",
    "source": {
      "namespace": "coach:my-catalog",
      "key": "paused-bench",
      "revision": "1"
    }
  }]
}
```

Each exercise requires all six definition fields. `main` may be null. Region,
main type, and muscle values use the exact enums in the schema. Notes may be
empty. There are no structured equipment fields yet; describe equipment in
notes. Files accept 1–1000 definitions within the web request size limit.
Names cannot have leading or trailing whitespace. Unknown fields, invalid
enums, duplicate muscle tags, duplicate normalized names, and duplicate source
identities are rejected with a field path or a duplicate-definition error.

`source` is optional. When present, its namespace and key must be nonblank,
case-sensitive strings and both are required; revision is optional. Keep the
pair stable when revising or renaming an exercise. The namespace should name
the publishing catalog and the key should identify one exercise within it.
Revision is an audit label, not an automatic update subscription. Updating a
matched row retains its acquisition origin and any shared-source public label.

## Matching and preview labels

| Label | Meaning |
| --- | --- |
| new | No matching source identity or colliding normalized name; creates an owned row. |
| unchanged | Source identity, definition, active state, and revision already match. |
| changed | Portable source identity matches; confirmation updates that row, including a possible rename. |
| skipped | No source supplied, but the exact name, definition, and active state already match. |
| conflict | A name belongs to another identity, differs without a source identity, or is ambiguous among existing rows. |

Name collisions compare trimmed lowercase names. Matching source identity is
scoped to the importing account's lifter profile. An identity used by another
account does not affect your import. A conflicting file cannot be confirmed;
rename the incoming definition, supply its original identity, or resolve
ambiguous catalog duplicates and preview again. Import never silently adopts
a manual row by name. Renames preserve the row's source identity.

New manual rows have `user_created` provenance. Older rows retain `unknown`
origin. Catalog files use `file_import`, except explicit `lifttrax:starter`
source files, which use `starter`. Shared-user copies use `shared_copy`, a
public source token derived from the source exercise's random identity, and
the sharing username as an audit label. No other account's private user,
profile, authentication, or raw catalog ID is copied into portable provenance.

## Completed history and other file types

Catalog updates affect current definitions and future logging. Completed
executions retain their performed name, region, main type, muscles, and catalog
notes. Edits to execution sets, dates, flags, or training notes preserve those
exercise snapshots. Disabling prevents new logging. Deleting removes only the
current definition and retains its executions and ordered sets. Duplicate-name
rename is refused with instructions; it does not merge completed histories.

Program and planned-workout imports remain separate and never add or overwrite
catalog entries. Unknown exercise names stay loadable, with no catalog-backed
history or metadata. Import or create the exercise before logging it. Import a
catalog first when following a plan that needs its definitions and history
hints. After renaming a catalog entry, update future plan references to its
current name; the current row still finds its earlier executions by identity.

Malformed JSON, unsupported versions, schema validation, duplicate definitions,
preview conflicts, stale previews, and database failures have distinct
messages. Account export v2 includes portable provenance and complete performed
snapshots, including history whose catalog row was deleted. See the
[lifecycle decision](adr/0005-catalog-history-lifecycle.md) and
[recovery runbook](account-data-export-and-recovery.md).
