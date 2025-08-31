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

### Add a lift execution

Record a new performance of a lift in the database. If the lift does not
exist yet it will be created when a lift `--type` is supplied.

```bash
cargo run -- add <EXERCISE> <WEIGHT> <REPS> <SETS> [--date YYYY-MM-DD] [--rpe RPE] [--muscle MUSCLE]... [--type LIFT_TYPE]
```

* `<EXERCISE>` – name of the movement (e.g. `Bench`)
* `<WEIGHT>` – weight lifted in pounds
* `<REPS>` – number of repetitions per set. For exercises measured by time or
  distance, provide the seconds or feet in place of a rep count
* `<SETS>` – number of sets performed
* `--date` – optional date, defaults to today's date
* `--rpe` – optional rating of perceived exertion
* `--muscle` – muscles worked by this lift; repeat flag for multiple values. Valid values include `BICEP`, `TRICEP`, `NECK`, `LAT`, `QUAD`, `HAMSTRING`, `CALF`, `LOWER_BACK`, `CHEST`, `FOREARM`, `REAR_DELT`, `FRONT_DELT`, `SHOULDER`, `CORE`, `GLUTE`, and `TRAP`.
* `--type` – type of lift when creating a new lift; valid values include `BENCH_PRESS`, `OVERHEAD_PRESS`, `SQUAT`, `DEADLIFT`, `CONDITIONING`, `ACCESSORY`, and `MOBILITY`.

Examples:

```bash
cargo run -- add Bench 225 5 3
cargo run -- add Squat 315 3 5 --date 2024-05-20 --rpe 8.5 --muscle QUAD --muscle GLUTE
```

### List lifts

Display each lift with all recorded executions, most recent first.

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
Bench (UPPER) [CHEST, TRICEP]
  - 2024-05-20: 3 sets x 5 reps @ 225 lbs RPE 8.5
  - 2024-05-15: 3 sets x 5 reps @ 220 lbs
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
entries. The database schema is versioned using SQLite's `user_version` pragma
so that future releases can migrate existing data. Deleting this file removes
all recorded lifts. To protect existing data, starting the application with an
existing database will create a timestamped backup named
`lifts.db.backup-<YYYYMMDDHHMMSS>` in the same directory.

## Data Model

Lifts capture both static information about the movement and a history of how
it was performed. Each lift stores its name and the muscles it trains. Every
execution record adds the date, number of sets and reps, weight used, and an
optional rating of perceived exertion (RPE).
