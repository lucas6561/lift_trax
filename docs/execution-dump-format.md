# Execution dump format

LiftTrax can export the configured user's complete execution history as
versioned JSON or human-readable text. JSON is the default for
`--executions-only`.

The stable version 1 schema is
`shared/executions/schema/execution-dump.schema.v1.json`. The matching
`execution-dump.schema.latest.json` path follows the newest version when the
format evolves.

## Commands

Export all dates as JSON:

```bash
./gradlew --quiet run --args='--executions-only' > executions.json
```

Export an inclusive date range:

```bash
./gradlew --quiet run --args='--executions-only --from 2026-01-01 --to 2026-03-31' > executions-q1.json
```

`--from` and `--to` are independently optional. Select readable text with
`--format human`:

```bash
./gradlew run --args='--executions-only --format human --from 2026-01-01'
```

All commands are scoped to the resolved CLI user. `--user <username-or-id>`
overrides the machine-local default for one invocation.

## JSON document

```json
{
  "schemaVersion": 1,
  "dateRange": {
    "from": null,
    "to": null
  },
  "executionCount": 1,
  "executions": [
    {
      "id": 42,
      "lift": {
        "name": "Front Squat",
        "region": "LOWER",
        "main": "SQUAT",
        "muscles": ["QUAD", "GLUTE"],
        "notes": "heels elevated"
      },
      "date": "2026-03-12",
      "warmup": false,
      "deload": false,
      "notes": "smooth",
      "sets": [
        {
          "metric": {
            "kind": "reps",
            "reps": 5
          },
          "weight": "185 lb",
          "rpe": 8.0
        }
      ]
    }
  ]
}
```

`dateRange.from` and `dateRange.to` reproduce the requested inclusive bounds;
`null` means that side was unbounded. Executions are ordered by date, lift
name, and execution ID.

Set metrics use one of these shapes:

- `{"kind":"reps","reps":5}`
- `{"kind":"reps_lr","left":5,"right":5}`
- `{"kind":"reps_range","min":8,"max":12}`
- `{"kind":"time_seconds","seconds":45}`
- `{"kind":"distance_feet","feet":100}`

The array order is the recorded set order. `weight`, `rpe`, execution notes,
lift notes, lift main type, and execution ID may be `null` when the stored
record does not supply them.
