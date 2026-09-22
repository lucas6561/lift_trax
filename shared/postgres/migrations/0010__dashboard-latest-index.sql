-- Support the dashboard's latest-execution lookup without scanning each lift's history.
CREATE INDEX IF NOT EXISTS idx_executions_profile_catalog_latest
    ON executions(lifter_profile_id, catalog_entry_id, performed_on DESC, web_execution_id DESC);
