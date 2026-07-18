# User-scoped authorization

Date: 2026-06-14

## Purpose

LiftTrax derives every web request's user-scoped Postgres store from the signed
session user. Local-auth and hosted-auth launches use the same persistence
boundary.

This is the enforceable LT-0086 slice. It protects local lift and execution rows
by owner, and the hosted adapter applies the same request-scoped ownership
boundary to core hosted lift/catalog and execution records.

## Legacy import owner model

Legacy SQLite imports may store `owner_user_id` on:

- `lifts`;
- `lift_records`.

Migration `0012__user-scoped-data.sql` backfills existing local data to
`local-user`, which matches the default local-development sign-in from
`/auth/login`.

During an explicit import, that means:

- signing in locally as `local-user` shows existing local data;
- signing in locally as a different user ID starts with an empty private view;
- different users can create lifts with the same name without colliding;
- update and delete calls for another user's execution fail as not found.

## Web request boundary

`WebAuth.currentUser(exchange)` provides the stable authenticated user ID.
`WebServerCli` passes that identity to the Postgres-backed
`TrainingDataStoreProvider`. Normal web routes and renderers use the returned
user-scoped store for dashboard, lift detail, execution listing,
planned-workout history, and execution mutations.

## Current limits

The first hosted account slice does not implement every future sharing feature
yet:

- coach/lifter relationship permissions are still design-only until the coach
  assignment workflow introduces shared records;
- programs and planned-workout definitions remain versioned files rather than
  hosted tables;
- Supabase Postgres Row Level Security remains a defense-in-depth follow-up for
  the deployment/database migration work.

Those pieces remain follow-up scope for the broader hosted roadmap, but private
core logging data is scoped by the signed-in account in Postgres.
