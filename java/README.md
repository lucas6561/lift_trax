# lift_trax Java port (incremental)

This is the initial Java + Gradle port slice. It includes:

- A Java `Database` interface that mirrors the Rust trait signatures.
- A SQLite-backed implementation (`SqliteDb`) with read paths needed for dumping data.
- A CLI command that loads the existing `lifts.db` file and prints lifts and executions.

## Run

From the `java/` directory:

```bash
gradle run --args='path/to/lifts.db'
```

If no argument is provided, it defaults to `lifts.db` in the current directory.
