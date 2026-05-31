# Program Schema v1

LiftTrax program schema v1 is the shared JSON contract for authored training
programs. It is designed for coach-written files, AI-generated files, and future
app-generated templates.

This snapshot remains supported for older files. New files should use the
latest snapshot documented in `docs/schema-versioning.md`.

## Files

- Schema: `shared/programs/schema/program.schema.v1.json`
- Conjugate example: `shared/programs/examples/conjugate-v1.json`
- Hypertrophy example: `shared/programs/examples/hypertrophy-v1.json`

## Authoring Rules

Every program file should be valid JSON and should include these top-level
sections:

- `schemaVersion`: Must be `1`.
- `program`: Human-readable metadata such as name, goal, author, duration, days
  per week, tags, and notes.
- `exercisePools`: Named pools that can point to exact exercises, query existing
  exercises by type/muscle/region, or do both.
- `progressionRules`: Reusable loading, RPE, wave, or manual progression rules.
- `substitutionRules`: Rules for safe substitutions, such as same-pattern main
  lifts or same-muscle accessories.
- `dayTemplates`: Reusable training-day structures made of ordered blocks.
- `weeks`: The actual calendar-like schedule. Each week lists training days and
  either references a day template or provides blocks inline.

Use the enum names from the schema exactly, such as `LOWER`, `UPPER`, `SQUAT`,
`BENCH_PRESS`, `HAMSTRING`, `CHAINS`, and `MONDAY`.

## Blocks And Slots

A day is made from blocks. Blocks describe the training section the athlete sees,
such as `Warm-up`, `Max Effort Single`, `Dynamic Effort`, `Accessory Circuit`,
or `Conditioning`.

Each block has one or more slots. A slot combines:

- `target`: The exercise source, such as a fixed exercise, an exercise pool, a
  database query, or an exercise selected by an earlier block.
- `setScheme`: Sets, metric, percent, RPE, accommodating resistance, and optional
  instructions.
- `progressionRuleRef`: Optional reference to a reusable progression rule.
- `substitutionRuleRef`: Optional reference to a reusable substitution rule.

Use `dayTemplates` when many weeks share the same structure. Use inline day
`blocks` only when a specific week needs a one-off structure.

## AI-Generated Files

When asking an AI tool to create a program file, give it the schema and ask for
JSON only. The prompt should also include:

- The athlete goal and duration.
- Available training days.
- Available exercise names when the file must match an existing LiftTrax
  database.
- Any required equipment limits.
- Whether substitutions are allowed and how conservative they should be.

AI-generated files should prefer named `exercisePools` over repeating the same
exercise details throughout the file. If exact exercise names are unknown, the AI
should include a precise `query` and a short example `exercises` list for the
coach or validator to reconcile later.

## Current Builder Coverage

The conjugate example represents the current builder's lower/upper max effort
days, lower/upper dynamic effort days, max-effort backoff and supplemental work,
dynamic percent waves, accessory circuits, conditioning, and optional forearm
finishers.

The hypertrophy example represents the current builder's alternating lower and
upper primary lifts, primary and secondary hypertrophy loading, warm-ups, and
accessory slots.

## Validation Boundary

This task defines the schema and examples. Field-level validation, reference
checking, database exercise matching, and actionable error messages belong to
`LT-0019`.
