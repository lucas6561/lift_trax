//! SQLite-backed implementation of the [`Database`] trait.

use chrono::{NaiveDate, Utc};
use rusqlite::{Connection, OptionalExtension, params, types::Type};
use std::collections::BTreeMap;
use std::fs;
use std::path::Path;
use std::str::FromStr;

use crate::weight::Weight;
use crate::{
    database::{Database, DbResult},
    models::{
        ExecutionSet, Lift, LiftExecution, LiftRegion, LiftStats, LiftType, Muscle, SetMetric,
    },
};

/// Current database schema version.
const DB_VERSION: i32 = 9;

/// Maximum number of timestamped backups to retain.
pub const MAX_BACKUPS: usize = 5;

/// Database persisted to a SQLite file.
pub struct SqliteDb {
    conn: Connection,
}

impl SqliteDb {
    /// Open (or create) a SQLite database at `path`.
    pub fn new(path: &str) -> DbResult<Self> {
        if Path::new(path).exists() {
            let timestamp = Utc::now().format("%Y%m%d%H%M%S");
            let backup_path = format!("{}.backup-{}", path, timestamp);
            fs::copy(path, &backup_path)?;

            // remove old backups
            let db_path = Path::new(path);
            let dir = match db_path.parent() {
                Some(p) if !p.as_os_str().is_empty() => p,
                _ => Path::new("."),
            };
            let prefix = format!("{}.backup-", db_path.file_name().unwrap().to_string_lossy());
            let mut backups: Vec<_> = fs::read_dir(dir)?
                .filter_map(|e| e.ok())
                .filter_map(|e| {
                    let name = e.file_name().to_string_lossy().into_owned();
                    if name.starts_with(&prefix) {
                        Some((name, e.path()))
                    } else {
                        None
                    }
                })
                .collect();
            backups.sort_by(|a, b| a.0.cmp(&b.0));
            while backups.len() > MAX_BACKUPS {
                if let Some((_, path)) = backups.first() {
                    fs::remove_file(path)?;
                }
                backups.remove(0);
            }
        }
        let conn = Connection::open(path)?;
        init_db(&conn)?;
        Ok(Self { conn })
    }

    /// Load all execution records for `lift_id`, newest first.
    fn fetch_executions(&self, lift_id: i32) -> DbResult<Vec<LiftExecution>> {
        let mut stmt = self.conn.prepare(
            "SELECT id, date, sets, warmup, deload, notes FROM lift_records WHERE lift_id = ?1 ORDER BY date DESC",
        )?;
        let iter = stmt.query_map(params![lift_id], |row| {
            let date_str: String = row.get(1)?;
            let date = NaiveDate::parse_from_str(&date_str, "%Y-%m-%d").map_err(|e| {
                rusqlite::Error::FromSqlConversionFailure(1, Type::Text, Box::new(e))
            })?;
            let sets_json: String = row.get(2)?;
            let sets: Vec<ExecutionSet> = serde_json::from_str(&sets_json).unwrap_or_default();
            let warm: i32 = row.get(3)?;
            let deload: i32 = row.get(4)?;
            Ok(LiftExecution {
                id: Some(row.get(0)?),
                date,
                sets,
                warmup: warm != 0,
                deload: deload != 0,
                notes: row.get(5)?,
            })
        })?;
        let mut executions = Vec::new();
        for exec in iter {
            executions.push(exec?);
        }
        Ok(executions)
    }

    fn parse_muscles(muscles_str: String) -> Vec<Muscle> {
        if muscles_str.is_empty() {
            Vec::new()
        } else {
            muscles_str
                .split(',')
                .filter_map(|m| Muscle::from_str(m).ok())
                .collect()
        }
    }

    fn row_to_lift(row: &rusqlite::Row<'_>) -> rusqlite::Result<(i32, Lift)> {
        let id: i32 = row.get(0)?;
        let region_str: String = row.get(2)?;
        let region = LiftRegion::from_str(&region_str).unwrap_or(LiftRegion::UPPER);
        let main_str: Option<String> = row.get(3)?;
        let main = main_str.and_then(|m| LiftType::from_str(&m).ok());
        let muscles_str: String = row.get(4)?;
        let notes: String = row.get(5)?;
        Ok((
            id,
            Lift {
                name: row.get(1)?,
                region,
                main,
                muscles: Self::parse_muscles(muscles_str),
                notes,
            },
        ))
    }

    fn load_lifts<P>(&self, sql: &str, params: P) -> DbResult<Vec<Lift>>
    where
        P: rusqlite::Params,
    {
        let mut stmt = self.conn.prepare(sql)?;
        let iter = stmt.query_map(params, Self::row_to_lift)?;
        let mut lifts = Vec::new();
        for row in iter {
            let (id, mut lift) = row?;
            lifts.push(lift);
        }
        Ok(lifts)
    }

    fn get_last_execution(&self, lift_id: i32) -> DbResult<Option<LiftExecution>> {
        Ok(
            self.conn
                .query_row(
                    "SELECT id, date, sets, warmup, deload, notes FROM lift_records WHERE lift_id = ?1 ORDER BY date DESC LIMIT 1",
                    params![lift_id],
                    |row| {
                        let date_str: String = row.get(1)?;
                        let date = NaiveDate::parse_from_str(&date_str, "%Y-%m-%d").map_err(|e| {
                            rusqlite::Error::FromSqlConversionFailure(1, Type::Text, Box::new(e))
                        })?;
                        let sets_json: String = row.get(2)?;
                        let sets: Vec<ExecutionSet> = serde_json::from_str(&sets_json).unwrap_or_default();
                        let warm: i32 = row.get(3)?;
                        let deload: i32 = row.get(4)?;
                        Ok(LiftExecution {
                            id: Some(row.get(0)?),
                            date,
                            sets,
                            warmup: warm != 0,
                            deload: deload != 0,
                            notes: row.get(5)?,
                        })
                    },
                )
                .optional()?,
        )
    }

    fn collect_best_by_reps(&self, lift_id: i32) -> DbResult<BTreeMap<i32, Weight>> {
        let mut stmt = self
            .conn
            .prepare("SELECT sets FROM lift_records WHERE lift_id = ?1")?;
        let iter = stmt.query_map(params![lift_id], |row| row.get::<_, String>(0))?;
        let mut best = BTreeMap::new();
        for row in iter {
            let sets_json = row?;
            let sets: Vec<ExecutionSet> = serde_json::from_str(&sets_json).unwrap_or_default();
            for set in sets {
                if let SetMetric::Reps(r) = set.metric {
                    let entry = best.entry(r).or_insert(set.weight.clone());
                    if set.weight.to_lbs() > entry.to_lbs() {
                        *entry = set.weight;
                    }
                }
            }
        }
        Ok(best)
    }
}

fn table_exists(conn: &Connection, table: &str) -> DbResult<bool> {
    let exists: Option<String> = conn
        .query_row(
            "SELECT name FROM sqlite_master WHERE type='table' AND name=?1",
            params![table],
            |row| row.get(0),
        )
        .optional()?;
    Ok(exists.is_some())
}

fn has_column(conn: &Connection, table: &str, column: &str) -> DbResult<bool> {
    let mut stmt = conn.prepare(&format!("PRAGMA table_info({})", table))?;
    let mut rows = stmt.query([])?;
    while let Some(row) = rows.next()? {
        let name: String = row.get(1)?;
        if name == column {
            return Ok(true);
        }
    }
    Ok(false)
}

fn detect_version(conn: &Connection) -> DbResult<i32> {
    if !table_exists(conn, "lifts")? {
        return Ok(0);
    }
    let mut v = 1;
    if has_column(conn, "lifts", "region")? {
        v = 2;
    }
    if has_column(conn, "lifts", "main_lift")? {
        v = 3;
    }
    if has_column(conn, "lifts", "muscles")? {
        v = 4;
    }
    if has_column(conn, "lifts", "notes")? {
        v = 5;
    }
    if has_column(conn, "lift_records", "notes")? {
        v = 6;
        if !has_column(conn, "lift_records", "reps")? {
            v = 7;
        }
        if has_column(conn, "lift_records", "warmup")? {
            v = 8;
            if has_column(conn, "lift_records", "deload")? {
                v = 9;
            }
        }
    }
    Ok(v)
}

/// Initialize the database schema and ensure it matches the expected version.
fn init_db(conn: &Connection) -> DbResult<()> {
    let detected_version = detect_version(conn)?;
    if detected_version == 0 {
        // Fresh database with no tables yet.
        conn.execute(
            "CREATE TABLE IF NOT EXISTS lifts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE,
                region TEXT NOT NULL,
                main_lift TEXT,
                muscles TEXT NOT NULL,
                notes TEXT NOT NULL DEFAULT ''
            )",
            [],
        )?;
        conn.execute(
            "CREATE TABLE IF NOT EXISTS lift_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        lift_id INTEGER NOT NULL,
                        date TEXT NOT NULL,
                        sets TEXT NOT NULL,
                        warmup INTEGER NOT NULL DEFAULT 0,
                        deload INTEGER NOT NULL DEFAULT 0,
                        notes TEXT NOT NULL DEFAULT '',
                        FOREIGN KEY(lift_id) REFERENCES lifts(id)
                    )",
            [],
        )?;
        conn.pragma_update(None, "user_version", &DB_VERSION)?;
    } else if detected_version < DB_VERSION {
        // Upgrade legacy schemas stepwise.
        run_migrations(conn, detected_version)?;
    } else {
        // Schema matches the latest version but user_version might be incorrect.
        let user_version: i32 = conn.query_row("PRAGMA user_version", [], |row| row.get(0))?;
        if user_version != DB_VERSION {
            conn.pragma_update(None, "user_version", &DB_VERSION)?;
        }
    }
    Ok(())
}

/// Apply migrations from a previous schema version to [`DB_VERSION`].
fn run_migrations(conn: &Connection, mut from_version: i32) -> DbResult<()> {
    // Apply migrations sequentially so that every intermediate version is handled.
    while from_version < DB_VERSION {
        match from_version {
            1 => {
                // Version 1 stored weight as a numeric column and lacked lift metadata.
                conn.execute(
                    "ALTER TABLE lifts ADD COLUMN region TEXT NOT NULL DEFAULT 'UPPER'",
                    [],
                )?;
                conn.execute(
                    "ALTER TABLE lift_records RENAME COLUMN weight TO weight_old",
                    [],
                )?;
                conn.execute(
                    "ALTER TABLE lift_records ADD COLUMN weight TEXT NOT NULL DEFAULT '0'",
                    [],
                )?;
                conn.execute("UPDATE lift_records SET weight = weight_old", [])?;
                conn.execute("ALTER TABLE lift_records DROP COLUMN weight_old", [])?;
            }
            2 => {
                // Add optional designation for a lift's main variant.
                conn.execute("ALTER TABLE lifts ADD COLUMN main_lift TEXT", [])?;
            }
            3 => {
                // Track muscles targeted by each lift.
                conn.execute(
                    "ALTER TABLE lifts ADD COLUMN muscles TEXT NOT NULL DEFAULT ''",
                    [],
                )?;
            }
            4 => {
                // Free-form notes for lifts were introduced in version 5.
                conn.execute(
                    "ALTER TABLE lifts ADD COLUMN notes TEXT NOT NULL DEFAULT ''",
                    [],
                )?;
            }
            5 => {
                // Execution records also store notes starting with version 6.
                conn.execute(
                    "ALTER TABLE lift_records ADD COLUMN notes TEXT NOT NULL DEFAULT ''",
                    [],
                )?;
            }
            6 => {
                // Migrate execution records to store set details as JSON.
                conn.execute("ALTER TABLE lift_records RENAME TO lift_records_old", [])?;
                conn.execute(
                    "CREATE TABLE lift_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        lift_id INTEGER NOT NULL,
                        date TEXT NOT NULL,
                        sets TEXT NOT NULL,
                        notes TEXT NOT NULL DEFAULT '',
                        FOREIGN KEY(lift_id) REFERENCES lifts(id)
                    )",
                    [],
                )?;
                let mut stmt = conn.prepare(
                    "SELECT id, lift_id, date, sets, reps, weight, rpe, notes FROM lift_records_old",
                )?;
                let mut rows = stmt.query([])?;
                while let Some(row) = rows.next()? {
                    let id: i32 = row.get(0)?;
                    let lift_id: i32 = row.get(1)?;
                    let date: String = row.get(2)?;
                    let set_count: i32 = row.get(3)?;
                    let reps: i32 = row.get(4)?;
                    let weight_str: String = row.get(5)?;
                    let rpe: Option<f32> = row.get(6)?;
                    let notes: String = row.get(7)?;
                    let weight = Weight::from_str(&weight_str).unwrap_or(Weight::Raw(0.0));
                    let set = ExecutionSet {
                        metric: SetMetric::Reps(reps),
                        weight: weight.clone(),
                        rpe,
                    };
                    let sets = vec![set; set_count as usize];
                    let sets_json = serde_json::to_string(&sets)?;
                    conn.execute(
                        "INSERT INTO lift_records (id, lift_id, date, sets, notes) VALUES (?1, ?2, ?3, ?4, ?5)",
                        params![id, lift_id, date, sets_json, notes],
                    )?;
                }
                conn.execute("DROP TABLE lift_records_old", [])?;
            }
            7 => {
                // Track warm-up flag for executions.
                conn.execute(
                    "ALTER TABLE lift_records ADD COLUMN warmup INTEGER NOT NULL DEFAULT 0",
                    [],
                )?;
            }
            8 => {
                // Track whether an execution was part of a deload.
                conn.execute(
                    "ALTER TABLE lift_records ADD COLUMN deload INTEGER NOT NULL DEFAULT 0",
                    [],
                )?;
            }
            _ => {}
        }
        from_version += 1;
    }
    conn.pragma_update(None, "user_version", &DB_VERSION)?;
    Ok(())
}

impl Database for SqliteDb {
    fn add_lift(
        &self,
        name: &str,
        region: LiftRegion,
        main: LiftType,
        muscles: &[Muscle],
        notes: &str,
    ) -> DbResult<()> {
        let muscles_str = muscles
            .iter()
            .map(|m| m.to_string())
            .collect::<Vec<_>>()
            .join(",");
        self.conn.execute(
            "INSERT INTO lifts (name, region, main_lift, muscles, notes) VALUES (?1, ?2, ?3, ?4, ?5)",
            params![
                name,
                region.to_string(),
                main.to_string(),
                muscles_str,
                notes
            ],
        )?;
        Ok(())
    }

    fn add_lift_execution(&self, name: &str, execution: &LiftExecution) -> DbResult<()> {
        let mut stmt = self.conn.prepare("SELECT id FROM lifts WHERE name = ?1")?;
        let lift_id: Option<i32> = stmt.query_row(params![name], |row| row.get(0)).optional()?;
        let lift_id = match lift_id {
            Some(id) => id,
            None => {
                return Err("lift not found".into());
            }
        };
        let sets_json = serde_json::to_string(&execution.sets)?;
        self.conn.execute(
            "INSERT INTO lift_records (lift_id, date, sets, warmup, deload, notes) VALUES (?1, ?2, ?3, ?4, ?5, ?6)",
            params![
                lift_id,
                execution.date.to_string(),
                sets_json,
                if execution.warmup { 1 } else { 0 },
                if execution.deload { 1 } else { 0 },
                execution.notes
            ],
        )?;
        Ok(())
    }

    fn update_lift(
        &self,
        current_name: &str,
        new_name: &str,
        region: LiftRegion,
        main: Option<LiftType>,
        muscles: &[Muscle],
        notes: &str,
    ) -> DbResult<()> {
        let muscles_str = muscles
            .iter()
            .map(|m| m.to_string())
            .collect::<Vec<_>>()
            .join(",");
        self.conn.execute(
            "UPDATE lifts SET name = ?1, region = ?2, main_lift = ?3, muscles = ?4, notes = ?5 WHERE name = ?6",
            params![
                new_name,
                region.to_string(),
                main.map(|m| m.to_string()),
                muscles_str,
                notes,
                current_name
            ],
        )?;
        Ok(())
    }

    fn delete_lift(&self, name: &str) -> DbResult<()> {
        let lift_id: Option<i32> = self
            .conn
            .prepare("SELECT id FROM lifts WHERE name = ?1")?
            .query_row(params![name], |row| row.get(0))
            .optional()?;
        if let Some(id) = lift_id {
            self.conn
                .execute("DELETE FROM lift_records WHERE lift_id = ?1", params![id])?;
            self.conn
                .execute("DELETE FROM lifts WHERE id = ?1", params![id])?;
        }
        Ok(())
    }

    fn update_lift_execution(&self, exec_id: i32, execution: &LiftExecution) -> DbResult<()> {
        let sets_json = serde_json::to_string(&execution.sets)?;
        self.conn.execute(
            "UPDATE lift_records SET date = ?1, sets = ?2, warmup = ?3, deload = ?4, notes = ?5 WHERE id = ?6",
            params![
                execution.date.to_string(),
                sets_json,
                if execution.warmup { 1 } else { 0 },
                if execution.deload { 1 } else { 0 },
                execution.notes,
                exec_id
            ],
        )?;
        Ok(())
    }

    fn delete_lift_execution(&self, exec_id: i32) -> DbResult<()> {
        self.conn
            .execute("DELETE FROM lift_records WHERE id = ?1", params![exec_id])?;
        Ok(())
    }

    fn lift_stats(&self, name: &str) -> DbResult<LiftStats> {
        let lift_id: i32 = self.conn.query_row(
            "SELECT id FROM lifts WHERE name = ?1",
            params![name],
            |row| row.get(0),
        )?;
        let last = self.get_last_execution(lift_id)?;
        let best_by_reps = self.collect_best_by_reps(lift_id)?;
        Ok(LiftStats { last, best_by_reps })
    }

    fn get_lift(&self, name: &str) -> DbResult<Lift> {
        let lifts =  self.load_lifts(
                "SELECT id, name, region, main_lift, muscles, notes FROM lifts WHERE name = ?1 ORDER BY name",
                [name]
            )?;
        match lifts.len() {
            1 => Ok(lifts.into_iter().next().unwrap()),
            0 => Err(format!("Lift {} not found", name).into()), // pick an error that fits your app
            n => Err(format!("Lift {} had {} entries.", name, lifts.len()).into()),
        }
    }
    fn list_lifts(&self) -> DbResult<Vec<Lift>> {
        self.load_lifts(
            "SELECT id, name, region, main_lift, muscles, notes FROM lifts ORDER BY name",
            [],
        )
    }

    fn lifts_by_type(&self, lift_type: LiftType) -> DbResult<Vec<Lift>> {
        self.load_lifts(
            "SELECT id, name, region, main_lift, muscles, notes FROM lifts WHERE main_lift = ?1 ORDER BY name",
            params![lift_type.to_string()],
        )
    }

    fn get_accessories_by_muscle(&self, muscle: Muscle) -> DbResult<Vec<Lift>> {
        self.load_lifts(
            "SELECT id, name, region, main_lift, muscles, notes FROM lifts WHERE main_lift = ?1 AND (',' || muscles || ',') LIKE ?2 ORDER BY name",
            params![LiftType::Accessory.to_string(), format!("%,{},%", muscle.to_string())],
        )
    }

    fn lifts_by_region_and_type(
        &self,
        region: LiftRegion,
        lift_type: LiftType,
    ) -> DbResult<Vec<Lift>> {
        self.load_lifts(
            "SELECT id, name, region, main_lift, muscles, notes FROM lifts WHERE region = ?1 AND main_lift = ?2 ORDER BY name",
            params![region.to_string(), lift_type.to_string()],
        )
    }

    fn get_executions(&self, lift_name: &str) -> Vec<LiftExecution> {
        let lift = self.get_lift(lift_name).unwrap();

        let lift_id: i32 = self.conn.query_row(
            "SELECT id FROM lifts WHERE name = ?1",
            params![lift_name],
            |row| row.get(0),
        ).unwrap();

        return self.fetch_executions(lift_id).unwrap()
    }
}
