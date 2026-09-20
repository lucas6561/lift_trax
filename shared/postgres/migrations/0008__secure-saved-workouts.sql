-- lifttrax:postgres-only
ALTER TABLE public.saved_workouts ENABLE ROW LEVEL SECURITY;
REVOKE ALL PRIVILEGES ON TABLE public.saved_workouts FROM anon, authenticated;
