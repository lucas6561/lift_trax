# M5: Multi-user distribution

## Goal

Prepare LiftTrax for use beyond one local database on one machine.

## Exit criteria

- User identity and ownership rules are designed.
- The app has an agreed deployment and packaging direction.
- Backup, restore, migration, and release expectations are documented.
- The architecture supports future distribution without requiring a rewrite.

## Tasks

- `LT-0015`: Choose the web, mobile, and distribution architecture.
- `LT-0016`: Add user and ownership model design.
- `LT-0017`: Define install, update, and release strategy.
- `LT-0006`: Define backup and restore flow.
- `LT-0076`: Document execution and catalog database boundary.
- `LT-0080`: Update backup and restore for split databases.
- `LT-0082`: Select hosted auth and data platform.
- `LT-0083`: Design hosted user data schema.
- `LT-0084`: Add public web security baseline.
- `LT-0085`: Implement account authentication.
- `LT-0086`: Enforce user-scoped authorization.
- `LT-0087`: Build hosted persistence adapter.
- `LT-0088`: Import local database to hosted account.
- `LT-0092`: Port primary database to Postgres.
- `LT-0089`: Define PWA and offline boundaries.
- `LT-0090`: Create hosted deployment pipeline.
- `LT-0091`: Add hosted backup and export controls.
- `LT-0094`: Add PWA installability shell.
- `LT-0095`: Add offline workout-session drafts.

## Hosted web app sequence

1. `LT-0016`: Define the user, lifter, coach, ownership, and sharing model.
2. `LT-0082`: Choose the first hosted auth, data, and deployment stack.
3. `LT-0083`: Map the local schema into hosted user-owned records.
4. `LT-0084`: Harden routes before any public deployment.
5. `LT-0085`: Add account authentication and session handling.
6. `LT-0086`: Enforce authorization on every hosted data path.
7. `LT-0087`: Introduce the Supabase Postgres persistence adapter for core logging.
8. `LT-0088`: Migrate/import existing local databases into hosted accounts.
9. `LT-0092`: Port primary persistence to Postgres.
10. `LT-0089`: Define PWA installability and offline boundaries.
11. `LT-0090`: Create the repeatable hosted deployment pipeline.
12. `LT-0091`: Add hosted backup and user export controls.
13. `LT-0094`: Add the installable PWA shell without offline writes.
14. `LT-0095`: Add user-scoped offline workout-session drafts.

Planning decisions now recorded:

- `docs/user-ownership-model.md`: users, lifter profiles, coach relationships,
  private-by-default ownership, and local import mapping.
- `docs/adr/0003-hosted-auth-data-platform.md`: Supabase Auth, Supabase
  Postgres, and Render for the first hosted Java beta.
- `docs/hosted-user-data-schema.md`: hosted Postgres ownership, catalog,
  execution, plan/session, and local import schema shape.
- `docs/hosted-persistence-adapter.md`: hosted JDBC/Postgres configuration,
  table boundary, request-scoped store behavior, and focused verification path.
- `docs/pwa-offline-boundaries.md`: installability-only first PWA slice,
  network-only authenticated data, and deferred offline writes.
- `docs/hosted-deployment-pipeline.md`: Render/Supabase environment, secret,
  migration, smoke-check, and rollback expectations.
