# lift_trax

Simple command-line tool to track your lifts using a local SQLite database.

## Usage

Build the binary:

```bash
cargo build
```

Add a lift (defaults to today's date):

```bash
cargo run -- add Bench 225 5
```

Specify a date (YYYY-MM-DD):

```bash
cargo run -- add Squat 315 3 --date 2024-05-20
```

List all recorded lifts:

```bash
cargo run -- list
```

Filter by exercise:

```bash
cargo run -- list --exercise Bench
```

The tool stores data in `lifts.db` in the current directory.
