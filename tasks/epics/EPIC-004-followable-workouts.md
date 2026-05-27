# EPIC-004: Followable workouts

## Goal

Turn generated workouts into followable sessions where a lifter can load the planned work, train through it, enter results, and adjust exercise choices.

## Why

Planning only becomes useful when it connects to execution. The app needs a stable workout output format and a session experience that handles real training details like missed lifts, substituted exercises, and completed set data.

## Success criteria

- Generated workout files have a versioned format.
- The app can load a planned workout.
- The lifter can enter results as the workout progresses.
- Exercise swaps preserve the intent of the plan.
- Planned work and completed work can be compared later.

## Related tasks

- `LT-0023`: Define the workout file format v1.
- `LT-0024`: Load planned workouts into the app.
- `LT-0025`: Build the follow-along workout session.
- `LT-0026`: Add exercise swap rules.
- `LT-0027`: Persist planned versus completed workout data.
