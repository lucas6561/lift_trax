ALTER TABLE executions ADD COLUMN performed_catalog_id VARCHAR(36);
ALTER TABLE executions ADD COLUMN performed_exercise_name TEXT;
ALTER TABLE executions ADD COLUMN performed_region TEXT;
ALTER TABLE executions ADD COLUMN performed_main_lift TEXT;
ALTER TABLE executions ADD COLUMN performed_muscles TEXT;
ALTER TABLE executions ADD COLUMN performed_catalog_notes TEXT;

UPDATE executions SET
    performed_catalog_id = catalog_entry_id,
    performed_exercise_name = (SELECT c.name FROM exercise_catalog_entries c JOIN lifter_profiles p ON p.id = c.lifter_profile_id WHERE c.id = executions.catalog_entry_id AND c.lifter_profile_id = executions.lifter_profile_id AND c.owner_user_id = p.owner_user_id),
    performed_region = (SELECT UPPER(c.region) FROM exercise_catalog_entries c WHERE c.id = executions.catalog_entry_id),
    performed_main_lift = (SELECT NULLIF(UPPER(REPLACE(TRIM(c.main_lift), '_', ' ')), '') FROM exercise_catalog_entries c WHERE c.id = executions.catalog_entry_id),
    performed_muscles = (SELECT c.muscles FROM exercise_catalog_entries c WHERE c.id = executions.catalog_entry_id),
    performed_catalog_notes = (SELECT c.notes FROM exercise_catalog_entries c WHERE c.id = executions.catalog_entry_id);

ALTER TABLE executions ALTER COLUMN performed_catalog_id SET NOT NULL;
ALTER TABLE executions ALTER COLUMN performed_exercise_name SET NOT NULL;
ALTER TABLE executions ALTER COLUMN performed_region SET NOT NULL;
ALTER TABLE executions ALTER COLUMN performed_muscles SET NOT NULL;
ALTER TABLE executions ALTER COLUMN performed_catalog_notes SET NOT NULL;
ALTER TABLE executions ADD CONSTRAINT execution_snapshot_name CHECK (TRIM(performed_exercise_name) <> '');
ALTER TABLE executions ADD CONSTRAINT execution_snapshot_region CHECK (performed_region IN ('UPPER', 'LOWER'));
ALTER TABLE executions ADD CONSTRAINT execution_snapshot_main CHECK (performed_main_lift IS NULL OR performed_main_lift IN ('BENCH PRESS', 'OVERHEAD PRESS', 'SQUAT', 'DEADLIFT', 'CONDITIONING', 'ACCESSORY', 'MOBILITY'));
ALTER TABLE executions ALTER COLUMN catalog_entry_id DROP NOT NULL;
CREATE INDEX idx_executions_profile_performed_name ON executions(lifter_profile_id, performed_exercise_name, performed_on);
CREATE INDEX idx_executions_profile_performed_identity ON executions(lifter_profile_id, performed_catalog_id, performed_on DESC, web_execution_id DESC);
