CREATE TABLE IF NOT EXISTS workout_submission_receipts (
    lifter_profile_id VARCHAR(36) NOT NULL REFERENCES lifter_profiles(id),
    submission_id TEXT NOT NULL,
    payload_fingerprint TEXT NOT NULL,
    logged_execution_count INTEGER NOT NULL DEFAULT 0,
    skipped_exercises INTEGER NOT NULL DEFAULT 0,
    skipped_sets INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (lifter_profile_id, submission_id)
);
