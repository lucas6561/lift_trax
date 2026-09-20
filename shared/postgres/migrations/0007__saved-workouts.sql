CREATE TABLE saved_workouts (
    id VARCHAR(36) PRIMARY KEY,
    lifter_profile_id VARCHAR(36) NOT NULL REFERENCES lifter_profiles(id),
    name VARCHAR(200) NOT NULL,
    workout_json TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX saved_workouts_profile_created_idx
    ON saved_workouts(lifter_profile_id, created_at, id);
