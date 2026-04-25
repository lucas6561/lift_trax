CREATE TABLE IF NOT EXISTS lifts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL UNIQUE,
    region TEXT NOT NULL,
    main_lift TEXT,
    muscles TEXT NOT NULL,
    notes TEXT NOT NULL DEFAULT '',
    enabled INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE IF NOT EXISTS lift_records (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    lift_id INTEGER NOT NULL,
    date TEXT NOT NULL,
    sets TEXT NOT NULL,
    warmup INTEGER NOT NULL DEFAULT 0,
    deload INTEGER NOT NULL DEFAULT 0,
    notes TEXT NOT NULL DEFAULT '',
    FOREIGN KEY(lift_id) REFERENCES lifts(id)
);

CREATE TABLE IF NOT EXISTS execution_sets (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    record_id INTEGER NOT NULL,
    set_index INTEGER NOT NULL,
    metric_kind TEXT NOT NULL,
    metric_a INTEGER NOT NULL DEFAULT 0,
    metric_b INTEGER,
    weight TEXT NOT NULL DEFAULT 'none',
    rpe REAL,
    FOREIGN KEY(record_id) REFERENCES lift_records(id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_execution_sets_record_index
    ON execution_sets(record_id, set_index);
