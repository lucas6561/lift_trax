# lift_trax Java port (incremental)

This is the initial Java + Gradle port slice. It includes:

- A Java `Database` interface that mirrors the Rust trait signatures.
- A SQLite-backed implementation (`SqliteDb`) with read paths needed for dumping data.
- A CLI command that loads the existing `lifts.db` file and prints lifts and executions.

- A Java conjugate wave builder port (`com.lifttrax.workout`) and markdown wave generator CLI (`com.lifttrax.cli.WaveCli`).
- Versioned program schema assets in `shared/programs`, with authoring documentation in `docs/program-schema-v1.md`.
- Versioned planned workout assets in `shared/workouts`, with import/export documentation in `docs/workout-file-format-v1.md`.

Architecture decisions are captured as short ADRs in `docs/adr/`, starting with
the local-first Java/SQLite baseline in
`docs/adr/0001-local-first-java-sqlite.md`.

## Run

From the repository root:

```bash
./gradlew run --args='path/to/lifts.db'
```

If no argument is provided, it defaults to the repository-root `lifts.db` (it auto-detects the repo root by looking for `shared/sql/schema.sql`).


Generate a wave markdown file:

```bash
./gradlew run --args='path/to/lifts.db'
java -cp build/libs/lift-trax-java-0.1.0.jar com.lifttrax.cli.WaveCli path/to/lifts.db 4 wave.md
```

Use a `.json` output path to export the same generated wave as a planned workout
file:

```bash
java -cp build/libs/lift-trax-java-0.1.0.jar com.lifttrax.cli.WaveCli path/to/lifts.db 4 planned-workout.json
```


## Run the network-accessible Web UI

From the repository root:

```bash
./gradlew runWeb --args='path/to/lifts.db 8080'
```

- First arg: SQLite database path (defaults to repository-root `lifts.db`).
- Second arg: port (defaults to `8080`).
- The server binds to `0.0.0.0`, so it is reachable from other devices on the same network at `http://<your-machine-ip>:8080`.

Routes:
- `/` list/search lifts
- `/lift?name=<lift name>` view lift details and execution history
- `/planned-workout-preview` preview a supported imported workout file selected from the Import Workout tab
- `/planned-workout-session` open one imported workout day as a follow-along logging form
- `/save-planned-workout-session` save completed follow-along work into normal execution history

## Add Execution weight input (simple explanation)

The web form accepts a few different weight styles so you can quickly log sets.

- **Normal bar weight:** `225 lb` or `100 kg`
- **Left/right loading:** `45|45 lb` (left side | right side)
- **Bands only:** `red+blue`
- **Bar + chains:** `225 lb+40c` (`c` means chain weight)
- **Bar + bands:** `225 lb+red+blue`
- **No weight:** `none`

If the parser cannot understand your text, LiftTrax keeps it as **custom text** instead of throwing it away.

Implementation note: parsing is handled by `WeightInputParser` (`com.lifttrax.cli`) so UI rendering and parsing are separated and easier to understand/maintain.

## How to read the Java web code (beginner-friendly map)

If you are new to Java, start here:

1. `WebServerCli` receives HTTP requests (like `/` or `/add-execution`).
2. `WebUiRenderer` builds HTML strings for each page/tab.
3. `WeightInputParser` translates weight text (`225 lb+40c`, `red+blue`, etc.) into structured form values.
4. `SqliteDb` reads and writes lift data in SQLite.

This split keeps each class focused on one job, which makes the project easier to understand and change safely.


## Package guide (whole Java project)

To help new developers, each main Java package now includes a `package-info.java` file with plain-language docs:

- `com.lifttrax.cli`: app entry points and web/CLI request handling.
- `com.lifttrax.db`: SQLite data access layer.
- `com.lifttrax.models`: shared domain data records/enums.
- `com.lifttrax.workout`: workout planning and wave logic.

Tip: in most IDEs, opening a package and viewing its documentation shows the `package-info.java` description first.

## Program schema assets

Schema-driven program work starts with the stable latest schema entrypoint,
`shared/programs/schema/program.schema.latest.json`. Representative conjugate
and hypertrophy files are in `shared/programs/examples/`. See
`docs/program-schema-v1.md` for the payload-shape rules that coaches and AI
tools should follow, and `docs/schema-versioning.md` for the compatibility
policy.

## Planned workout assets

Generated workouts use the stable latest schema entrypoint,
`shared/workouts/schema/workout.schema.latest.json`. The current wave generator
can export the latest supported format through `WaveCli` by writing to a
`.json` output file, and the web UI can preview supported older and latest
workout JSON from the Import Workout tab. Each previewed day can open as a
follow-along workout, where planned counts seed editable result fields and
completed exercises save through the normal execution-history path.

## Quality gate workflow

From repository root, run:

```bash
./gradlew qualityGate
```

This single command checks Java formatting, runs PMD static analysis, runs the
test suite, and verifies the JaCoCo coverage threshold.

Static scanning uses the PMD ruleset in `config/pmd/ruleset.xml`. The policy
includes broad correctness/security/best-practice checks plus stricter rules for
braces and empty control flow, unnecessary casts/locals/returns, utility-class
shape, exception misuse, mutable static state, fragile synchronization, and
selected file/string performance mistakes. Exclusions stay in the ruleset with a
short rationale so future tightening has a clear starting point.

Coverage is enforced at **90% instruction coverage** for the currently maintained Java core slice:

- selected `com.lifttrax.workout` planner classes (see `build.gradle` `coverageIncludes`)
- `com.lifttrax.cli.WeightInputParser`
- `com.lifttrax.cli.WebHtml`

This keeps the threshold focused on behavior-heavy code while the remaining Java port surfaces continue to evolve.

## Mutation testing workflow

PIT mutation testing is configured for a narrow first workout-planning slice:

- `com.lifttrax.workout.ConjugateWorkoutBuilder`
- `com.lifttrax.workout.WaveMarkdownWriter`

Run the repeatable local command:

```bash
./gradlew pitest
```

Reports are written to `build/reports/pitest/` with stable paths because
timestamped report folders are disabled. The HTML report is the easiest way to
inspect surviving mutations; the XML report is available for later automation.
The current threshold is modest by design and should be tightened as surviving
mutations are either killed with stronger assertions or deliberately documented.
