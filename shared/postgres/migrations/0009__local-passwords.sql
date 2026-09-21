ALTER TABLE app_users ADD COLUMN password_hash TEXT;
ALTER TABLE app_users ADD COLUMN password_version TEXT NOT NULL DEFAULT '';
