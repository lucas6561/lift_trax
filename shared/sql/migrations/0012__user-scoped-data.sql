PRAGMA foreign_keys = OFF;

ALTER TABLE lifts RENAME TO lifts_old;

CREATE TABLE lifts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    owner_user_id TEXT NOT NULL DEFAULT 'local-user',
    name TEXT NOT NULL,
    region TEXT NOT NULL,
    main_lift TEXT,
    muscles TEXT NOT NULL,
    notes TEXT NOT NULL DEFAULT '',
    enabled INTEGER NOT NULL DEFAULT 1,
    UNIQUE(owner_user_id, name)
);

INSERT INTO lifts (id, owner_user_id, name, region, main_lift, muscles, notes, enabled)
SELECT id, 'local-user', name, region, main_lift, muscles, notes, enabled
FROM lifts_old;

DROP TABLE lifts_old;

ALTER TABLE lift_records RENAME TO lift_records_old;

CREATE TABLE lift_records (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    owner_user_id TEXT NOT NULL DEFAULT 'local-user',
    lift_id INTEGER NOT NULL,
    date TEXT NOT NULL,
    sets TEXT NOT NULL,
    warmup INTEGER NOT NULL DEFAULT 0,
    deload INTEGER NOT NULL DEFAULT 0,
    notes TEXT NOT NULL DEFAULT '',
    FOREIGN KEY(lift_id) REFERENCES lifts(id)
);

INSERT INTO lift_records (id, owner_user_id, lift_id, date, sets, warmup, deload, notes)
SELECT
    lr.id,
    COALESCE(l.owner_user_id, 'local-user'),
    lr.lift_id,
    lr.date,
    lr.sets,
    lr.warmup,
    lr.deload,
    lr.notes
FROM lift_records_old lr
LEFT JOIN lifts l ON l.id = lr.lift_id;

DROP TABLE lift_records_old;

CREATE INDEX IF NOT EXISTS idx_lifts_owner_name ON lifts(owner_user_id, name);
CREATE INDEX IF NOT EXISTS idx_lift_records_owner_lift_date
    ON lift_records(owner_user_id, lift_id, date DESC);

PRAGMA foreign_keys = ON;
