# lift_trax Java port (incremental)

This is the initial Java + Gradle port slice. It includes:

- A Java `Database` interface that mirrors the Rust trait signatures.
- A SQLite-backed implementation (`SqliteDb`) with read paths needed for dumping data.
- A CLI command that loads the existing `lifts.db` file and prints lifts and executions.

- A Java conjugate wave builder port (`com.lifttrax.workout`) and markdown wave generator CLI (`com.lifttrax.cli.WaveCli`).

## Run

From the `java/` directory:

```bash
gradle run --args='path/to/lifts.db'
```

If no argument is provided, it defaults to the repository-root `lifts.db` (it auto-detects the repo root by looking for `shared/sql/schema.sql`).


Generate a wave markdown file:

```bash
gradle run --args='path/to/lifts.db'
java -cp build/libs/lift_trax-0.1.0.jar com.lifttrax.cli.WaveCli path/to/lifts.db 4 wave.md
```


## Run the network-accessible Web UI

From the `java/` directory:

```bash
gradle runWeb --args='path/to/lifts.db 8080'
```

- First arg: SQLite database path (defaults to repository-root `lifts.db`).
- Second arg: port (defaults to `8080`).
- The server binds to `0.0.0.0`, so it is reachable from other devices on the same network at `http://<your-machine-ip>:8080`.

Routes:
- `/` list/search lifts
- `/lift?name=<lift name>` view lift details and execution history

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


## Test + coverage workflow

From `java/`, run:

```bash
./gradlew test jacocoTestReport jacocoTestCoverageVerification
```

Coverage is enforced at **80% instruction coverage** for the currently maintained Java core slice:

- selected `com.lifttrax.workout` planner classes (see `build.gradle` `coverageIncludes`)
- `com.lifttrax.cli.WeightInputParser`
- `com.lifttrax.cli.WebHtml`

This keeps the threshold focused on behavior-heavy code while the remaining Java port surfaces continue to evolve.
