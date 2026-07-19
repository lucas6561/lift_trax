ALTER TABLE app_users ADD COLUMN username VARCHAR(30);

CREATE UNIQUE INDEX idx_app_users_username
    ON app_users(username);
