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

- `LT-0011`: Raise coverage gate to 90 percent.
- `LT-0012`: Add a single quality gate command.
- `LT-0031`: Add mutation testing for core training logic.
- `LT-0073`: Expand mutation testing to planned workout files.
- `LT-0074`: Expand mutation testing to active workout logging.
- `LT-0075`: Add mutation testing ratchet.
- `LT-0013`: Document package boundaries and coding standards.
- `LT-0014`: Add CI quality checks.

### 2. Cross-device application

The app should work comfortably from a desktop browser and a phone. The near-term product can stay web-first, but the architecture should leave room for a packaged mobile app, multiple users, and distribution.

Key tasks:

- `LT-0001`: Define the daily training dashboard.
- `LT-0002`: Add execution editing and deletion.
- `LT-0007`: Improve mobile workout logging flow.
- `LT-0069`: Fix phone execution tab weight visibility.
- `LT-0015`: Choose the web, mobile, and distribution architecture.
- `LT-0016`: Add user and ownership model design.
- `LT-0017`: Define install, update, and release strategy.
- `LT-0082`: Select hosted auth and data platform.
- `LT-0084`: Add public web security baseline.
- `LT-0085`: Implement account authentication.
- `LT-0086`: Enforce user-scoped authorization.
- `LT-0089`: Define PWA and offline boundaries.
- `LT-0076`: Document execution and catalog database boundary.
- `LT-0080`: Update backup and restore for split databases.

### 3. Schema-driven workout building

The wave builder should accept a program schema instead of depending only on specific implementations. Coaches should be able to create multi-week programs through a future editor, an AI-generated schema file, or specialized generators such as conjugate and hypertrophy.

Key tasks:

- `LT-0003`: Design the workout plan model.
- `LT-0018`: Define the program schema v1.
- `LT-0019`: Build a schema validation layer.
- `LT-0061`: Add schema version compatibility.
- `LT-0070`: Add latest schema entrypoints.
- `LT-0071`: Publish AI workout schema guide.
- `LT-0020`: Create a generic schema-to-wave builder.
- `LT-0021`: Convert existing conjugate and hypertrophy builders to schema output.
- `LT-0022`: Design the coach program editor.

### 4. Followable workout output

Generated workouts should be saved in a stable format that the application can load, display, follow in real time, and update as the workout changes. The lifter should be able to enter data as they train and swap exercises when the day requires it.

Key tasks:

- `LT-0023`: Define the workout file format v1.
- `LT-0024`: Load planned workouts into the app.
- `LT-0025`: Build the follow-along workout session.
- `LT-0048`: Show history in active workout.
- `LT-0062`: Unify planned workout output formats.
- `LT-0063`: Add planned workout visual regression checks.
- `LT-0026`: Add exercise swap rules.
- `LT-0027`: Persist planned versus completed workout data.
- `LT-0030`: Add exercise equipment requirements.
- `LT-0077`: Add exercise snapshots to execution history.
- `LT-0078`: Add exercise catalog database foundation.
- `LT-0079`: Move exercise catalog to catalog database.
- `LT-0093`: Import exercise catalog files.
- `LT-0064`: Add active workout draft and resume.

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

Hosted web app path:

1. `LT-0016`: Add user and ownership model design.
2. `LT-0082`: Select hosted auth and data platform.
3. `LT-0083`: Design hosted user data schema.
4. `LT-0084`: Add public web security baseline.
5. `LT-0085`: Implement account authentication.
6. `LT-0086`: Enforce user-scoped authorization.
7. `LT-0087`: Build hosted persistence adapter for Supabase Postgres.
8. `LT-0088`: Import local database to hosted account.
9. `LT-0092`: Port primary database to Postgres.
10. `LT-0089`: Define PWA and offline boundaries.
11. `LT-0090`: Create hosted deployment pipeline.
12. `LT-0091`: Add hosted backup and export controls.

`docs/user-ownership-model.md` defines the user, lifter, coach, and ownership
rules for this path. `docs/adr/0003-hosted-auth-data-platform.md` selects
Supabase Auth, Supabase Postgres, and a Render-hosted Java web service for the
first hosted beta. The sequence still keeps security, authentication,
authorization, and hosted persistence ahead of public exposure.

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

1. Finish `M1-quality-baseline` so every future change has a reliable safety net.
2. Complete the day-to-day logging loop: dashboard, create lift, log execution, view history, edit mistakes.
3. Design the plan model, program schema v1, and workout file format v1 before changing persistence aggressively.
4. Build a generic schema-to-wave path and convert existing specific builders to emit that schema.
5. Load generated workouts into the app and support a follow-along session with data entry and exercise swaps.
6. Move the platform track through the hosted web app path before exposing LiftTrax beyond local-network use.
