# lift_trax

Simple command-line tool to track your lifts using a local SQLite database.

## Build & Run

This project uses [Rust](https://www.rust-lang.org/). Build the binary with Cargo:

```bash
cargo build
```

Commands are run through `cargo run` followed by a subcommand:

```bash
cargo run -- <SUBCOMMAND> [FLAGS]
```

## Commands

### Add a lift

Record a new lift in the database.

```bash
cargo run -- add <EXERCISE> <WEIGHT> <REPS> [--date YYYY-MM-DD]
```

* `<EXERCISE>` – name of the movement (e.g. `Bench`)
* `<WEIGHT>` – weight lifted in pounds
* `<REPS>` – number of repetitions
* `--date` – optional date, defaults to today's date

Examples:

```bash
cargo run -- add Bench 225 5
cargo run -- add Squat 315 3 --date 2024-05-20
```

### List lifts

Display stored lifts in reverse chronological order.

```bash
cargo run -- list [--exercise EXERCISE]
```

Use `--exercise` to filter by a specific movement.

Example:

```bash
cargo run -- list --exercise Bench
```

The output is formatted as:

```
2024-05-20: Bench - 225 lbs x 5
```

## Help

Show available options and flags:

```bash
cargo run -- --help
cargo run -- add --help
cargo run -- list --help
```

## Data Storage

The tool creates a `lifts.db` SQLite file in the current directory to store all
entries. Deleting this file removes all recorded lifts.
