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
