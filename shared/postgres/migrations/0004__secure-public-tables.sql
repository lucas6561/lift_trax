-- lifttrax:postgres-only
ALTER TABLE public.app_users ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.lifter_profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.exercise_catalog_entries ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.executions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.execution_sets ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.local_imports ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.local_import_records ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.workout_submission_receipts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.lifttrax_schema_migrations ENABLE ROW LEVEL SECURITY;

REVOKE ALL PRIVILEGES ON TABLE public.app_users FROM anon, authenticated;
REVOKE ALL PRIVILEGES ON TABLE public.lifter_profiles FROM anon, authenticated;
REVOKE ALL PRIVILEGES ON TABLE public.exercise_catalog_entries FROM anon, authenticated;
REVOKE ALL PRIVILEGES ON TABLE public.executions FROM anon, authenticated;
REVOKE ALL PRIVILEGES ON TABLE public.execution_sets FROM anon, authenticated;
REVOKE ALL PRIVILEGES ON TABLE public.local_imports FROM anon, authenticated;
REVOKE ALL PRIVILEGES ON TABLE public.local_import_records FROM anon, authenticated;
REVOKE ALL PRIVILEGES ON TABLE public.workout_submission_receipts FROM anon, authenticated;
REVOKE ALL PRIVILEGES ON TABLE public.lifttrax_schema_migrations FROM anon, authenticated;
REVOKE ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public FROM anon, authenticated;

ALTER DEFAULT PRIVILEGES IN SCHEMA public
    REVOKE ALL PRIVILEGES ON TABLES FROM anon, authenticated;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    REVOKE ALL PRIVILEGES ON SEQUENCES FROM anon, authenticated;
