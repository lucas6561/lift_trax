---
id: LT-0071
title: Publish AI workout schema guide
status: done
track: docs
priority: high
effort: small
created: 2026-06-05
updated: 2026-06-05
owner: Codex
depends_on: [LT-0070]
---

# LT-0071: Publish AI workout schema guide

## Why

People should be able to use the public repository as the source of truth when
asking another large language model to create a LiftTrax-compatible workout.
Right now the schema files exist, but a user can easily grab an old numbered
schema or miss the prompt details needed to get importable JSON back.

## Outcome

The repo includes a clear README-style guide that tells users where to fetch the
latest workout schema from GitHub, how to give it to an AI tool, and how to ask
for a planned workout JSON file that LiftTrax can import.

## Scope

- In scope: a docs page or README section, raw GitHub URL guidance, a reusable AI
  prompt template, expected JSON-only output instructions, validation/import
  steps, and links to examples.
- Out of scope: hosting a separate documentation site, building a prompt UI, or
  guaranteeing that third-party AI tools always produce valid output.

## Acceptance criteria

- [x] Documentation points users at the stable latest planned-workout schema
      path instead of a version-numbered schema snapshot.
- [x] The guide includes a copyable prompt that asks an AI tool for JSON only,
      in accordance with the LiftTrax planned-workout schema.
- [x] The prompt asks users to provide athlete goal, schedule, duration,
      available equipment, available exercise names when known, and constraints.
- [x] The guide explains how to validate or sanity-check the generated file
      before import.
- [x] The guide links to at least one valid planned-workout example.
- [x] README or another discoverable top-level doc links to the guide.

## Notes

This should be written for a non-developer lifter or coach who knows how to open
GitHub and paste context into a model, not only for project contributors. The
guide should steer people toward the planned-workout schema, because that is the
importable generated-workout contract.

Completed with `docs/ai-workout-schema-guide.md`, linked from README. The guide
uses the stable latest planned-workout schema path and raw GitHub URL, includes a
copyable JSON-only AI prompt, names the user inputs to provide, describes
sanity-check and import-preview steps, and links to
`shared/workouts/examples/conjugate-wave-v2.json`.

Verification:

- `./gradlew.bat qualityGate`
