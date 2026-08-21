# AI Planned Workout Guide

Use this guide when you want another AI tool to create a LiftTrax workout JSON
file that can be imported from the web UI. The important distinction is that
LiftTrax imports planned workouts, not authoring programs. Give the AI the
planned-workout schema and ask it to return only JSON.

## Schema To Use

Use the stable latest planned-workout schema:

- Repository path: `shared/workouts/schema/workout.schema.latest.json`
- Raw GitHub URL:
  `https://raw.githubusercontent.com/lucas6561/lift_trax/main/shared/workouts/schema/workout.schema.latest.json`

Do not use a numbered schema snapshot such as `workout.schema.v3.json` for
long-lived prompts. Numbered snapshots stay frozen for compatibility, while
`workout.schema.latest.json` follows the current importable format.

## Prompt Template

Copy this prompt into your AI tool. Then attach or paste the schema from the raw
GitHub URL above.

```text
You are creating a LiftTrax planned workout JSON file.

Use the attached LiftTrax planned-workout JSON schema exactly. Return JSON only.
Do not wrap the JSON in Markdown. Do not include comments, explanations, or
placeholder text. The output must be a single JSON object that conforms to the
schema and can be imported into LiftTrax.

Athlete goal:
[Describe the main goal, such as strength, hypertrophy, conditioning, meet prep,
return from injury, or general training.]

Schedule:
[List available training days per week and preferred day names. Example:
Monday, Wednesday, Friday, Saturday.]

Duration:
[Number of weeks and any planned deload or testing week.]

Available equipment:
[List the equipment the athlete can use. Example: barbell, rack, bench,
dumbbells up to 75 lb, cables, bands, sled.]

Available exercise names:
[Paste known LiftTrax exercise names when the workout should match an existing
database. If names are unknown, ask for common exercise names and include
reasonable substitutionOptions.]

Constraints:
[List injuries, movements to avoid, session time limits, max exercise count,
conditioning limits, progression preferences, or coaching rules.]

Workout requirements:
- Use source.kind "ai".
- Keep completedWorkouts as an empty array.
- Include weeks, days, blocks, exercises, and plannedSets in the order the
  athlete should train them.
- Use planned set metricType values from the schema: reps, reps_lr, reps_range,
  time_seconds, distance_feet, or none.
- Use either percent or rpe as the primary intensity prescription for a planned
  set, not both. Older files written against prior numbered schemas may contain
  both and remain readable for compatibility.
- An rpeCap may accompany percent. It means to start with the percentage-based
  load, never exceed the cap, and reduce the load when necessary. Do not replace
  an RPE-prescribed set's rpe with rpeCap.
- When percent should use another exercise's max or history, set percentOf to
  that exercise's exact LiftTrax name. Omit percentOf when the planned
  exercise itself is the percentage reference.
- Add accommodatingResistance, notes, and substitutionOptions only when they
  are useful and valid for the schema.
- When rest guidance is useful, add a planned-set `rest` object with inclusive
  integer `minimumSeconds` and `maximumSeconds` values. Use equal values for an
  exact duration, and never make the maximum smaller than the minimum.
- Prefer exact exercise names from the available exercise list when provided.
- Make every training day complete enough to preview and train in LiftTrax.
```

## Before Import

Sanity-check the generated file before training from it:

1. Confirm the response is raw JSON only. The first character should be `{` and
   there should be no surrounding Markdown fence.
2. Confirm the top-level fields include `schemaVersion`, `metadata`, `source`,
   `weeks`, and `completedWorkouts`.
3. Confirm `source.kind` is `ai` and `completedWorkouts` is `[]`.
4. Check that each week has days, each day has blocks, each block has exercises,
   and each exercise has `plannedSets`.
5. Check set metrics for common mistakes: reps use `metricType: "reps"` and a
   positive `reps` value, timed work uses `time_seconds` and `seconds`, distance
   work uses `distance_feet` and `distanceFeet`, and rep ranges use
   `reps_range`, `repsMin`, and `repsMax`.
6. Check each optional `rpeCap` is between 0 and 10. A set may use `percent`
   with `rpeCap`, but it must not use `percent` with `rpe`.
7. Check each `percentOf` value names the intended load-reference exercise and
   is used only to clarify a percentage target.
8. Check each optional rest range uses non-negative integer seconds and has
   `maximumSeconds` greater than or equal to `minimumSeconds`.
9. If you use a JSON Schema validator, validate the file against the raw schema
   URL above.
10. In LiftTrax, open the web UI, go to the Import Workout tab, select the JSON
   file, and use App Preview first. The preview should show readable weeks,
   days, blocks, exercise names, and set targets before you choose Work Along.

If the file does not preview cleanly, ask the AI to repair the JSON against the
same schema instead of manually editing large sections by hand.

## Example

A valid planned-workout example lives at:

- `shared/workouts/examples/conjugate-wave-v5.json`

That example shows the same importable planned-workout shape: metadata, source,
weeks, days, blocks, exercise details, planned set targets with percentage
references, RPE caps, rest guidance, and an empty completed-workout list. Its
main-work target is equivalent to:

```json
{
  "metricType": "reps",
  "reps": 4,
  "percent": 70,
  "percentOf": "Conventional Deadlift",
  "rpeCap": 8,
  "deload": false
}
```

This means to start with 70% of the Conventional Deadlift max and reduce the
load if necessary rather than exceeding RPE 8.
