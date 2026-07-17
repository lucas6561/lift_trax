# LiftTrax user and ownership model

Date: 2026-06-14

## Purpose

LiftTrax starts as a single-user local training log. Hosted LiftTrax needs an
explicit ownership model before auth, hosted persistence, program sharing, or
coach-client workflows are implemented.

The model below is intentionally small enough for the first hosted beta while
leaving room for coaches, lifters, and shared programs.

## Core entities

`User`
: A signed-in account from the hosted auth provider. A user can own private
training data, author programs, coach another lifter, or train as a lifter.

`Lifter profile`
: The subject of training data. A normal individual account gets one default
lifter profile owned by the same user. A coach may later have access to client
lifter profiles through explicit relationships.

`Coach relationship`
: An explicit permission edge between a coach user and a lifter profile. It
records the allowed actions, such as assigning programs or viewing selected
training history.

`Program template`
: An authored training asset. It can be private to its creator, assigned to one
or more lifters, or later made available through a controlled sharing flow.

`Planned workout`
: An immutable generated workout snapshot owned by a lifter profile. It may
reference the program template that produced it, but training history must not
depend on mutable template state.

`Workout session`
: One lifter performing one planned workout. It owns draft state, skipped work,
substitutions, notes, and completed set inputs until they are saved into
execution history.

`Execution`
: A completed training result. Executions are private to the owning lifter
profile unless a coach relationship grants access.

`Exercise catalog record`
: A named exercise definition. Starter catalog records can be app-owned and
globally readable. User-created catalog records are owned by a user or lifter
profile. Execution history should keep exercise snapshots so old history
survives catalog edits.

## Ownership defaults

- Private by default: user-owned training data is not readable by other users.
- Every hosted record that can contain private training data must resolve to a
  `lifter_profile_id`, `owner_user_id`, or both.
- Records authored by a coach but assigned to a lifter need both authorship and
  recipient ownership fields. Authorship does not imply access forever.
- Shared access is permission-based, not URL-based. A route may include an ID,
  but the server must also verify the current user's relationship to the record.
- Public or app-owned records are limited to non-private reference data such as
  starter exercise catalog entries.

## Record ownership map

| Record type | Owner | Shared access |
| --- | --- | --- |
| User account | Auth provider user ID | None |
| Lifter profile | Owning user | Coach relationships only |
| Lift/exercise selection | Lifter profile or owning user | Coach can read or suggest only when permitted |
| Execution | Lifter profile | Coach can read only when relationship permits it |
| Program template | Creator user | Can be assigned or copied to lifters |
| Planned workout | Lifter profile | Coach can read assigned plans when permitted |
| Workout session/draft | Lifter profile | Coach access deferred unless explicitly added |
| Exercise catalog starter record | App/system | Globally readable |
| Exercise catalog user record | Owning user or lifter profile | Not shared by default |
| Backup/export bundle | Requesting user/account | Must contain only records the account can export |

## Coach and lifter sharing

The first hosted model supports coach-created programs without turning the whole
app into a broad social product.

1. A coach authors a program template.
2. A coach relationship grants permission to assign a program to a lifter
   profile.
3. Assignment creates a planned workout snapshot or program assignment record
   owned by the lifter profile.
4. The lifter's completed sessions and executions remain lifter-owned.
5. Coach visibility into execution history is granted only by explicit
   relationship permissions.

This keeps the lifter's history private while still allowing a coach to deliver
programming.

## Database impact

Hosted persistence should add or map these concepts before user data is stored
online:

- `users`: local app user row keyed to the auth provider's stable subject.
- `lifter_profiles`: owned by `users`, with one default profile per individual
  account.
- `coach_lifter_relationships`: coach user, lifter profile, permission flags,
  status, and timestamps.
- Ownership columns on private records, favoring `lifter_profile_id` for
  training data and `owner_user_id` for account-level assets.
- Authorship columns on program templates and other coach-created assets.
- Visibility or sharing status for records that can be assigned.
- Indexes on owner and relationship columns used by route-level queries.

The hosted schema design in `LT-0083` should translate this conceptual model
into concrete tables, constraints, and migration steps.

## Local data migration

Existing local `lifts.db` files have an implicit single owner. Importing a local
database into hosted LiftTrax should:

- create or select the signed-in user's default lifter profile;
- assign imported lifts, executions, planned workouts, sessions, and notes to
  that lifter profile;
- preserve source IDs only as import metadata when needed for duplicate
  detection;
- reject or preview unsupported schema versions before writing hosted records;
- use an all-or-nothing import so a failed import does not leave mixed ownership
  state.

## Privacy assumptions

- A user's email or auth identity is account metadata, not display content for
  other users.
- Error messages should not confirm that a private record exists.
- Exports must be account-scoped and must not include another user's private
  data.
- Starter catalog data may be shared globally, but user-created training data is
  private unless a relationship grants access.

## Follow-up tasks

- `LT-0083` turns this model into the hosted user data schema.
- `LT-0085` implements authentication and the server-side identity context.
- `LT-0086` enforces owner and relationship checks on hosted data paths.
- `LT-0088` maps local database imports into a signed-in account.
- `LT-0091` verifies hosted export isolation for account-owned data.
