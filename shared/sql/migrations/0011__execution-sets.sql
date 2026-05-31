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
