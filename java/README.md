# lift_trax JavaFX port

This directory contains a Java + JavaFX port of lift_trax with SQLite-backed lift tracking.

## Requirements

- Java 21+
- Gradle 8+

## Run

```bash
cd java
gradle run -Dorg.gradle.java.home=$HOME/.local/share/mise/installs/java/21.0.2
```

## Build

```bash
cd java
gradle build -Dorg.gradle.java.home=$HOME/.local/share/mise/installs/java/21.0.2
```

The app creates and uses `lifts.db` in the working directory.

## Features ported

- Record lift executions with weight/reps/sets/date/RPE/notes
- Automatically create lifts when they do not yet exist
- Track lift type and muscles
- List lifts with optional exercise and muscle filtering
- Show up to the 3 most recent executions per lift
