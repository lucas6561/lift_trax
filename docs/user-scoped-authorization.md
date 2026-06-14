# User-scoped authorization

Date: 2026-06-14

## Purpose

LiftTrax now has a local authorization bridge for the hosted path. The web UI
does not read or write the raw SQLite database directly; authenticated requests
use a user-scoped database view derived from the signed session user.

This is the first enforceable LT-0086 slice. It protects local lift and
execution rows by owner while the hosted Postgres adapter, relationship tables,
and Supabase Row Level Security work remain pending.

## Local owner model

SQLite now stores `owner_user_id` on:

- `lifts`;
- `lift_records`.

Migration `0012__user-scoped-data.sql` backfills existing local data to
`local-user`, which matches the default local-development sign-in from
`/auth/login`.

That means:

- signing in locally as `local-user` shows existing local data;
- signing in locally as a different user ID starts with an empty private view;
- different users can create lifts with the same name without colliding;
- update and delete calls for another user's execution fail as not found.

## Web request boundary

`WebAuth.currentUser(exchange)` provides the stable authenticated user ID.
`WebServerCli` turns that identity into `SqliteDb.forUser(user.id())`, which
returns a `TrainingDataStore` facade. Normal web routes and renderers use that
facade for dashboard, lift detail, execution listing, planned-workout history,
and execution mutations.

Direct unscoped `SqliteDb` methods remain available for CLI and migration
compatibility. Public hosted routes should use the scoped facade, not raw
database methods.

## Current limits

The local SQLite bridge does not implement the full hosted sharing model yet:

- coach/lifter relationship permissions are still design-only;
- persisted programs and planned workouts are not yet owner-scoped tables;
- Supabase Postgres Row Level Security remains a defense-in-depth follow-up;
- the hosted persistence adapter still needs to map this authorization model to
  Postgres.

Those pieces remain part of LT-0086/LT-0087/LT-0092 before public hosting is
safe.
