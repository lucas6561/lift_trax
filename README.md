# lift_trax

This repository contains implementations of lift tracking tools.

- `rust/` – Rust command-line application for recording lifts. See `rust/README.md` for build and usage details.
- `java/` – Java sources for a similar tool.
- A workout builder CLI is available via `gradle run -PmainClass=org.lift.trax.workoutbuilder.WorkoutBuilderCli --args="<dbPath> <weeks>"`.

