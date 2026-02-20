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

If no argument is provided, it defaults to `lifts.db` in the current directory.


Generate a wave markdown file:

```bash
gradle run --args='path/to/lifts.db'
java -cp build/libs/lift_trax-0.1.0.jar com.lifttrax.cli.WaveCli path/to/lifts.db 4 wave.md conjugate
```


Program options for the 4th argument:

- `conjugate` (default)
- `hypertrophy` (4-day upper/lower hypertrophy split)
