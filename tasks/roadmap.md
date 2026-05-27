# LiftTrax roadmap

LiftTrax is becoming a practical strength-training log and planning companion: a local-first app that stores lifts and executions, helps plan useful training waves, and makes past work easy to inspect.

## North star

Make it easy to answer three questions:

1. What should I train today?
2. What did I do last time?
3. Is the plan moving me forward?

## Roadmap tracks

### 1. Product core

Make the app feel complete for day-to-day training: add, view, edit, and review lifts and executions without touching the database directly.

Starter tasks:

- `LT-0001`: Define the daily training dashboard.
- `LT-0002`: Add execution editing and deletion.

### 2. Training logic

Turn the conjugate and wave builders into a clearer planning system with predictable inputs, explainable outputs, and room for future programming styles.

Starter tasks:

- `LT-0003`: Design the workout plan model.
- `LT-0004`: Add plan history and regeneration rules.

### 3. Data and persistence

Keep the database trustworthy as the app grows: migrations, seed data, backup/restore, and richer query paths.

Starter tasks:

- `LT-0005`: Add schema migration tracking.
- `LT-0006`: Define backup and restore flow.

### 4. Interface

Improve the lightweight web UI so it feels dense, fast, and usable during an actual lifting session.

Starter tasks:

- `LT-0007`: Improve mobile workout logging flow.
- `LT-0008`: Add lift detail trends.

### 5. Quality and maintainability

Keep the Java port easy to change with focused tests, package boundaries, and simple developer workflows.

Starter tasks:

- `LT-0009`: Expand web route tests.
- `LT-0010`: Document architecture decisions.

## Near-term sequence

1. Complete the day-to-day logging loop: create lift, log execution, view history, edit mistakes.
2. Make the planner persist generated workouts instead of only producing one-off output.
3. Add visual progress signals on lift detail pages.
4. Add migration and backup confidence before changing the data model aggressively.
5. Polish the mobile workout experience.

