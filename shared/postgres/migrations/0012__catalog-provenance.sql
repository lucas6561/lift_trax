ALTER TABLE exercise_catalog_entries ADD COLUMN origin VARCHAR(20) NOT NULL DEFAULT 'unknown';
ALTER TABLE exercise_catalog_entries ADD COLUMN source_namespace VARCHAR(200);
ALTER TABLE exercise_catalog_entries ADD COLUMN source_key VARCHAR(200);
ALTER TABLE exercise_catalog_entries ADD COLUMN source_revision VARCHAR(200);
ALTER TABLE exercise_catalog_entries ADD COLUMN copied_from_label VARCHAR(30);
ALTER TABLE exercise_catalog_entries ADD CONSTRAINT catalog_origin CHECK (origin IN ('unknown', 'user_created', 'starter', 'shared_copy', 'file_import'));
ALTER TABLE exercise_catalog_entries ADD CONSTRAINT catalog_source_pair CHECK ((source_namespace IS NULL AND source_key IS NULL AND source_revision IS NULL) OR (source_namespace IS NOT NULL AND source_key IS NOT NULL AND TRIM(source_namespace) <> '' AND TRIM(source_key) <> ''));
CREATE UNIQUE INDEX idx_catalog_profile_source ON exercise_catalog_entries(lifter_profile_id, source_namespace, source_key);
