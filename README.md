# lift_trax (JavaFX)

This repository is now a Java + JavaFX desktop application with SQLite storage.

## Requirements

- Java 24+
- Gradle 8+

## Build

```bash
gradle build -Dorg.gradle.java.home=$HOME/.local/share/mise/installs/java/24.0.2
```

## Run

```bash
gradle run -Dorg.gradle.java.home=$HOME/.local/share/mise/installs/java/24.0.2
```

## Current GUI coverage

- **Add**: add execution with weight/reps/sets/date/RPE/notes and warmup/deload flags
- **Query**: inspect all executions for a lift and delete selected execution or delete lift
- **List**: list lifts with filters (exercise text, region, type, muscle)
- **Last Week**: date-range execution view (default last 7 days)

The app stores data in `lifts.db` in the working directory.
