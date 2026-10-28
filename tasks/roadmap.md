# LiftTrax roadmap

LiftTrax is becoming a practical strength-training planning and logging system: quality software with a strict engineering bar, an easy workout experience on desktop and phone, and a schema-driven program builder that can support coaches, generated programs, and future distribution.

## North star

Make it easy for a lifter or coach to answer four questions:

1. What should I train today?
2. What did I do last time?
3. Is the plan moving me forward?
4. Can I create, share, load, and adjust training programs without changing code?

## Product goals

### 1. Quality software

LiftTrax should be readable, documented, tested, and safe to change. A build should fail if static analysis has warnings or errors, formatting is off, tests fail, or covered code drops below the agreed threshold.

Key tasks:

- `LT-0073`: Expand mutation testing to planned workout files.
- `LT-0075`: Add mutation testing ratchet.

The 90 percent whole-code coverage gate, CI quality command, core/active-workout
mutation testing, package-boundary documentation, and first web-server
decomposition are delivered foundations rather than open roadmap work.

### 2. Cross-device application

The app should work comfortably from a desktop browser and a phone. The near-term product can stay web-first, but the architecture should leave room for a packaged mobile app, multiple users, and distribution.

Key tasks:

- `LT-0017`: Define install, update, and release strategy.

The authenticated Postgres web app, PWA shell, account-scoped device drafts,
mobile logging fixes, user ownership, account export, and hosted recovery
runbook are delivered. Packaging and release/update policy remains open.

### 3. Schema-driven workout building

The wave builder should accept a program schema instead of depending only on specific implementations. Coaches should be able to create multi-week programs through a future editor, an AI-generated schema file, or specialized generators such as conjugate and hypertrophy.

Key tasks:

- `LT-0020`: Create a generic schema-to-wave builder.
- `LT-0021`: Convert existing conjugate and hypertrophy builders to schema output.
- `LT-0022`: Design the coach program editor.

The plan model, versioned schemas, validation, latest aliases, and AI authoring
guide are complete; the remaining work is using those contracts as the normal
builder and authoring path.

### 4. Followable workout output

Generated workouts should be saved in a stable format that the application can load, display, follow in real time, and update as the workout changes. The lifter should be able to enter data as they train and swap exercises when the day requires it.

Key tasks:

- `LT-0063`: Add planned workout visual regression checks.
- `LT-0026`: Add exercise swap rules.
- `LT-0027`: Persist planned versus completed workout data.
- `LT-0030`: Add exercise equipment requirements.
- `LT-0044`: Add configurable rest timer.

Loading, previewing, printing, saving, following, and device-resuming planned
workouts are delivered. The LT-0076 through LT-0080 and LT-0093 sequence is also
complete: immutable performed history, safe catalog lifecycle and provenance,
confirmed catalog-file import, and a measured isolated Postgres recovery drill.
See [the lifecycle decision](../docs/adr/0005-catalog-history-lifecycle.md) and
[recovery evidence](../docs/recovery-drill-2026-10-27.md).

## Roadmap tracks

### Product core

Make the app feel complete for day-to-day training: add, view, edit, and review lifts and executions without touching the database directly.

### Training logic

Move from hard-coded builders to program definitions, validation, generated plans, and regeneration rules.

### Data and persistence

Keep the database trustworthy as the app grows: migrations, seed data, backup/restore, plan storage, workout sessions, and user-owned records.

### Interface

Improve the web UI so it feels dense, fast, and usable during an actual lifting session on both desktop and phone.

### Platform and distribution

Prepare the application for multiple users, deployable releases, and eventual mobile app packaging without forcing that jump too early.

Hosted web app foundation (delivered):

1. `LT-0016`: Add user and ownership model design.
2. `LT-0082`: Select hosted auth and data platform.
3. `LT-0083`: Design hosted user data schema.
4. `LT-0084`: Add public web security baseline.
5. `LT-0085`: Implement account authentication.
6. `LT-0086`: Enforce user-scoped authorization.
7. `LT-0087`: Build hosted persistence adapter for Supabase Postgres.
8. `LT-0088`: Import local database to hosted account.
9. `LT-0092`: Make Postgres the sole runtime database and add an on-demand
   Postgres-to-SQLite operator backup.
10. `LT-0089`: Define PWA and offline boundaries.
11. `LT-0090`: Create hosted deployment pipeline.
12. `LT-0091`: Add account-scoped export and hosted recovery controls.
13. `LT-0094`: Add PWA installability shell.
14. `LT-0095`: Add offline workout-session drafts.
15. `LT-0096`: Add friendly account usernames and automatic account selection.

All fifteen foundation cards above are complete. `docs/user-ownership-model.md`
defines the user, lifter, coach, and ownership rules for this path.
`docs/adr/0003-hosted-auth-data-platform.md` selects Supabase Auth, Supabase
Postgres, and a Render-hosted Java web service for the first hosted beta.
`docs/hosted-user-data-schema.md` maps that direction into the first hosted
Postgres schema shape, and `docs/hosted-persistence-adapter.md` documents the
first hosted JDBC/Postgres adapter for core logging. `docs/pwa-offline-boundaries.md`
keeps first-pass PWA work limited to installability and user-neutral caching,
while `docs/hosted-deployment-pipeline.md` records the Render/Supabase
deployment shape. `docs/account-data-export-and-recovery.md` separates
user-owned exports from operator backups and records the recovery runbook.

### Quality and maintainability

Keep the codebase easy to change with strict checks, focused tests, package boundaries, architecture notes, and clear developer workflows.

## Milestones

See `tasks/milestones/` for the working sequence.

1. `M1-quality-baseline`: Make quality gates explicit and hard to bypass.
2. `M2-training-model`: Define plan, program schema, and workout output contracts.
3. `M3-followable-workouts`: Load a generated workout and log it while training.
4. `M4-coach-authoring`: Enable program creation through files first, then editor workflows.
5. `M5-multi-user-distribution`: Add user ownership, packaging, release, and deployment foundations.

## Near-term sequence

1. Define the single-Postgres catalog/history lifecycle in `LT-0076`, then add
   immutable performed-exercise snapshots in `LT-0077`.
2. Add portable catalog provenance (`LT-0078`), make catalog mutations
   history-safe (`LT-0079`), and prove recovery with `LT-0080`.
3. Make the generic schema-to-wave path normal (`LT-0020`, `LT-0021`) before
   expanding coach authoring (`LT-0022`).
4. Finish the workout loop with swaps, planned-versus-completed data,
   equipment-aware choices, and the configurable rest timer (`LT-0026`,
   `LT-0027`, `LT-0030`, `LT-0044`).
5. Add planned-workout visual regression coverage and finish the mutation
   ratchet (`LT-0063`, `LT-0073`, `LT-0075`).
6. Define release/update policy (`LT-0017`) and build catalog-file import on the
   new provenance rules (`LT-0093`).
