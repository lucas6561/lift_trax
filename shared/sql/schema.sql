CREATE TABLE IF NOT EXISTS lifts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    owner_user_id TEXT NOT NULL DEFAULT 'local-user',
    name TEXT NOT NULL,
    region TEXT NOT NULL,
    main_lift TEXT,
    muscles TEXT NOT NULL,
    notes TEXT NOT NULL DEFAULT '',
    enabled INTEGER NOT NULL DEFAULT 1,
    UNIQUE(owner_user_id, name)
);

CREATE TABLE IF NOT EXISTS lift_records (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    owner_user_id TEXT NOT NULL DEFAULT 'local-user',
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

CREATE INDEX IF NOT EXISTS idx_lifts_owner_name ON lifts(owner_user_id, name);

CREATE INDEX IF NOT EXISTS idx_lift_records_owner_lift_date
    ON lift_records(owner_user_id, lift_id, date DESC);

CREATE TABLE IF NOT EXISTS schema_migrations (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    version INTEGER NOT NULL UNIQUE,
    name TEXT NOT NULL UNIQUE,
    applied_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS workout_submission_receipts (
    owner_user_id TEXT NOT NULL,
    submission_id TEXT NOT NULL,
    payload_fingerprint TEXT NOT NULL,
    logged_execution_count INTEGER NOT NULL DEFAULT 0,
    skipped_exercises INTEGER NOT NULL DEFAULT 0,
    skipped_sets INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (owner_user_id, submission_id)
);
