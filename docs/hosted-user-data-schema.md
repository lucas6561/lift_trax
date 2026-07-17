# Hosted user data schema

Date: 2026-06-14

## Purpose

This document turns the LiftTrax ownership model into the first hosted schema
shape for Supabase Postgres. It is a design artifact, not a production
migration. The implementation work remains in `LT-0087`, `LT-0088`, and
`LT-0092`.

The goals are:

- preserve existing local training history during import;
- make private training data owner-scoped from the first hosted write;
- support a bounded coach-to-lifter program assignment path;
- keep completed execution history readable even when exercise catalog data
  changes;
- leave the local SQLite path available until the hosted adapter and Postgres
  cutover are proven.

## Identity root

Supabase Auth owns authentication. LiftTrax should store an application-level
profile row keyed to the stable Supabase auth user ID.

### `app_users`

| Column | Notes |
| --- | --- |
| `id uuid primary key` | LiftTrax app user ID. Can match `auth.users.id` if the implementation keeps a one-to-one mapping. |
| `auth_user_id uuid not null unique` | Stable Supabase Auth subject. |
| `display_name text` | Optional app display name. |
| `created_at timestamptz not null` | Creation time. |
| `updated_at timestamptz not null` | Last profile update. |

`auth_user_id` is the identity bridge for server-side auth context. It should
not be exposed as the only privacy boundary; all private queries still need
owner or relationship predicates.

## Lifter ownership

Training data belongs to a lifter profile. A normal user gets one default
profile, while coach/client workflows can grant a coach access to another
profile.

### `lifter_profiles`

| Column | Notes |
| --- | --- |
| `id uuid primary key` | Hosted lifter profile ID. |
| `owner_user_id uuid not null references app_users(id)` | Account that owns the profile. |
| `name text not null` | Profile name shown to the owner. |
| `is_default boolean not null default false` | One default profile per account. |
| `created_at timestamptz not null` | Creation time. |
| `updated_at timestamptz not null` | Last profile update. |

Indexes and constraints:

- unique partial index on `(owner_user_id)` where `is_default`;
- index on `owner_user_id` for all private training queries.

### `coach_lifter_relationships`

| Column | Notes |
| --- | --- |
| `id uuid primary key` | Relationship ID. |
| `coach_user_id uuid not null references app_users(id)` | Coach account. |
| `lifter_profile_id uuid not null references lifter_profiles(id)` | Lifter profile being coached. |
| `status text not null` | `pending`, `active`, `revoked`, or `declined`. |
| `can_assign_programs boolean not null default false` | Allows program assignment. |
| `can_view_training_history boolean not null default false` | Allows selected history reads. |
| `created_at timestamptz not null` | Creation time. |
| `updated_at timestamptz not null` | Last permission update. |

Indexes and constraints:

- unique index on `(coach_user_id, lifter_profile_id)`;
- index on `(lifter_profile_id, status)`;
- check constraint for known `status` values.

## Exercise catalog and execution history

The current local SQLite database stores editable exercises in `lifts` and
completed history in `lift_records` plus `execution_sets`. Hosted Postgres
should keep those concepts but make the lifecycle boundary explicit.

### `exercise_catalog_entries`

| Column | Notes |
| --- | --- |
| `id uuid primary key` | Hosted catalog entry ID. |
| `scope text not null` | `system`, `user`, or `lifter`. |
| `owner_user_id uuid references app_users(id)` | Required for user-scoped entries. |
| `lifter_profile_id uuid references lifter_profiles(id)` | Optional for lifter-specific entries. |
| `name text not null` | Current exercise name. |
| `region text not null` | Current LiftTrax region value. |
| `main_lift text` | Current LiftTrax main-lift value. |
| `muscles text not null default ''` | Current muscle tags until normalized. |
| `notes text not null default ''` | Catalog notes. |
| `enabled boolean not null default true` | Whether it appears in active selectors. |
| `created_at timestamptz not null` | Creation time. |
| `updated_at timestamptz not null` | Last edit time. |

Visibility rules:

- `system` entries are globally readable starter catalog records.
- `user` entries are readable by the owner and by relationships only if a later
  permission grants catalog sharing.
- `lifter` entries are visible to the profile owner and permitted coaches.

Indexes and constraints:

- index on `(scope, enabled, name)`;
- index on `owner_user_id`;
- index on `lifter_profile_id`;
- check constraint requiring an owner for `user` scope and no owner for
  `system` scope.

### `executions`

Hosted `executions` are the Postgres counterpart to local `lift_records`.

| Column | Notes |
| --- | --- |
| `id uuid primary key` | Hosted execution ID. |
| `lifter_profile_id uuid not null references lifter_profiles(id)` | Owner of the completed training result. |
| `catalog_entry_id uuid references exercise_catalog_entries(id)` | Optional live catalog reference for new records. |
| `performed_exercise_name text not null` | Snapshot used for history display. |
| `performed_region text not null` | Snapshot of exercise region. |
| `performed_main_lift text` | Snapshot of main-lift value. |
| `performed_muscles text not null default ''` | Snapshot of tags. |
| `date date not null` | Training date. |
| `warmup boolean not null default false` | Current local flag. |
| `deload boolean not null default false` | Current local flag. |
| `notes text not null default ''` | Execution notes. |
| `source_import_id uuid references local_imports(id)` | Import batch, when applicable. |
| `source_local_record_id bigint` | Original `lift_records.id`, used only for import traceability. |
| `created_at timestamptz not null` | Creation time. |
| `updated_at timestamptz not null` | Last edit time. |

Snapshot fields are required so history survives a renamed, disabled, deleted,
or reclassified catalog entry. This coordinates with `LT-0076`, `LT-0077`,
`LT-0078`, and `LT-0079`.

Indexes:

- `(lifter_profile_id, date desc)`;
- `(lifter_profile_id, performed_exercise_name, date desc)`;
- `(catalog_entry_id)` for live catalog joins when available;
- `(source_import_id, source_local_record_id)` for duplicate import detection.

### `execution_sets`

Hosted `execution_sets` are the Postgres counterpart to local
`execution_sets`.

| Column | Notes |
| --- | --- |
| `id uuid primary key` | Hosted set ID. |
| `execution_id uuid not null references executions(id) on delete cascade` | Parent execution. |
| `set_index integer not null` | Original order within the execution. |
| `metric_kind text not null` | Current `SetMetric` kind. |
| `metric_a integer not null default 0` | First metric value. |
| `metric_b integer` | Optional second metric value. |
| `weight text not null default 'none'` | Current weight text representation. |
| `rpe numeric` | Optional RPE. |

Constraints:

- unique index on `(execution_id, set_index)`;
- check constraint for known `metric_kind` values once the Java enum is stable
  enough to mirror in SQL.

The legacy `lift_records.sets` JSON column should be treated as import and
compatibility data only after hosted sets are available.

## Programs, plans, and sessions

The hosted schema should mirror the model in `docs/workout-plan-model.md`.
Program templates are authored input, planned workouts are immutable generated
snapshots, workout sessions are in-progress attempts, and executions are
completed history.

### `program_templates`

| Column | Notes |
| --- | --- |
| `id uuid primary key` | Program template ID. |
| `author_user_id uuid not null references app_users(id)` | Creator. |
| `title text not null` | Program name. |
| `schema_version text not null` | Program schema version, such as `v1`. |
| `template_json jsonb not null` | Authored program payload. |
| `visibility text not null default 'private'` | `private`, `assigned`, or later `public`. |
| `created_at timestamptz not null` | Creation time. |
| `updated_at timestamptz not null` | Last edit time. |

### `program_assignments`

| Column | Notes |
| --- | --- |
| `id uuid primary key` | Assignment ID. |
| `program_template_id uuid not null references program_templates(id)` | Source template. |
| `assigned_by_user_id uuid not null references app_users(id)` | Coach or owner assigning it. |
| `lifter_profile_id uuid not null references lifter_profiles(id)` | Recipient. |
| `status text not null` | `active`, `completed`, `revoked`, or `archived`. |
| `created_at timestamptz not null` | Assignment time. |

Assignment requires either profile ownership or an active coach relationship
with `can_assign_programs = true`.

### `planned_workouts`

| Column | Notes |
| --- | --- |
| `id uuid primary key` | Planned workout snapshot ID. |
| `lifter_profile_id uuid not null references lifter_profiles(id)` | Owner. |
| `program_template_id uuid references program_templates(id)` | Optional source template. |
| `program_assignment_id uuid references program_assignments(id)` | Optional assignment source. |
| `workout_schema_version text not null` | Planned-workout schema version. |
| `source_kind text not null` | Imported, generated, manual, etc. |
| `planned_workout_json jsonb not null` | Immutable generated workout file payload. |
| `generated_at timestamptz` | Source generation time, when known. |
| `created_at timestamptz not null` | Hosted creation time. |

Planned workout rows are immutable once followed. Corrections should create a
new row or revision rather than mutating history.

### `workout_sessions`

| Column | Notes |
| --- | --- |
| `id uuid primary key` | Workout session ID. |
| `lifter_profile_id uuid not null references lifter_profiles(id)` | Owner. |
| `planned_workout_id uuid references planned_workouts(id)` | Planned source, when any. |
| `session_date date not null` | Training date. |
| `status text not null` | `draft`, `saved`, `abandoned`, or `archived`. |
| `session_json jsonb not null` | Draft state, substitutions, skips, and notes. |
| `created_at timestamptz not null` | Creation time. |
| `updated_at timestamptz not null` | Last draft update. |

### `session_execution_links`

| Column | Notes |
| --- | --- |
| `session_id uuid not null references workout_sessions(id)` | Source session. |
| `execution_id uuid not null references executions(id)` | Saved execution. |
| `planned_exercise_id text` | Planned occurrence reference from the workout file. |
| `planned_set_ids text[]` | Planned set references covered by this execution. |

Primary key: `(session_id, execution_id)`.

This table keeps planned-vs-completed comparison from requiring completed
executions to store the full planned payload.

## Local import and schema-version tracking

### `local_imports`

| Column | Notes |
| --- | --- |
| `id uuid primary key` | Import batch ID. |
| `requested_by_user_id uuid not null references app_users(id)` | Signed-in user. |
| `target_lifter_profile_id uuid not null references lifter_profiles(id)` | Destination profile. |
| `source_kind text not null` | `sqlite_db`, `backup_bundle`, or later formats. |
| `source_schema_version integer` | Local SQLite schema version. |
| `source_fingerprint text` | Hash or durable duplicate-detection key. |
| `status text not null` | `previewed`, `imported`, or `failed`. |
| `created_at timestamptz not null` | Import start. |
| `completed_at timestamptz` | Import completion. |
| `error_summary text` | Failure summary safe to show to the importing user. |

### Import mapping from local `lifts.db`

| Local source | Hosted destination |
| --- | --- |
| implicit single owner | signed-in `app_users` row plus default `lifter_profiles` row |
| `lifts` | `exercise_catalog_entries` with `scope = 'lifter'` or `scope = 'user'` |
| `lift_records` | `executions`, assigned to the target lifter profile |
| `execution_sets` | `execution_sets`, assigned through each hosted execution |
| `schema_migrations` and schema version | `local_imports.source_schema_version` and import validation |
| planned workout JSON files | `planned_workouts`, only when imported through a future hosted file/import flow |

Import should run as an all-or-nothing transaction for a single target profile.
Repeated imports should use `local_imports.source_fingerprint` plus source local
IDs to preview duplicates before writing.

## Authorization rules

Every hosted data path should start from one of these predicates:

- current user owns `lifter_profiles.owner_user_id`;
- current user owns `program_templates.author_user_id`;
- current user has an active `coach_lifter_relationships` row with the needed
  permission;
- record is a `system` catalog entry.

Supabase Row Level Security can mirror these predicates as defense in depth, but
the Java backend must still apply them before rendering pages or mutating data.

## Index summary

First hosted migrations should prioritize these indexes:

- `app_users(auth_user_id)`;
- `lifter_profiles(owner_user_id)`;
- `coach_lifter_relationships(coach_user_id, status)`;
- `coach_lifter_relationships(lifter_profile_id, status)`;
- `exercise_catalog_entries(scope, enabled, name)`;
- `exercise_catalog_entries(owner_user_id)`;
- `exercise_catalog_entries(lifter_profile_id)`;
- `executions(lifter_profile_id, date desc)`;
- `executions(lifter_profile_id, performed_exercise_name, date desc)`;
- `execution_sets(execution_id, set_index)` unique;
- `program_templates(author_user_id, updated_at desc)`;
- `program_assignments(lifter_profile_id, status)`;
- `planned_workouts(lifter_profile_id, created_at desc)`;
- `workout_sessions(lifter_profile_id, status, session_date desc)`;
- `local_imports(requested_by_user_id, created_at desc)`.

## Follow-up implementation notes

- `LT-0087` should start with `app_users`, `lifter_profiles`,
  `exercise_catalog_entries`, `executions`, and `execution_sets` so core logging
  works against Supabase Postgres.
- `LT-0088` should implement `local_imports` and the local `lifts.db` mapping.
- `LT-0092` should turn the Postgres design into primary migrations after the
  adapter proves the core workflows.
- `LT-0076` through `LT-0079` remain relevant locally; hosted execution
  snapshots should not wait until a local catalog split is fully complete.
- `LT-0091` should export account-scoped rows through lifter ownership and coach
  relationship permissions, never by raw table dump.
