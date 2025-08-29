//! SQLite-backed implementation of the [`Database`] trait.

use chrono::NaiveDate;
use rusqlite::{Connection, OptionalExtension, params, types::Type};
use std::collections::BTreeMap;
use std::str::FromStr;

use crate::weight::Weight;
use crate::{
    database::{Database, DbResult},
    models::{Lift, LiftExecution, LiftRegion, LiftStats, LiftType, Muscle},
};

/// Current database schema version.
const DB_VERSION: i32 = 6;

/// Database persisted to a SQLite file.
pub struct SqliteDb {
    conn: Connection,
}

impl SqliteDb {
    /// Open (or create) a SQLite database at `path`.
    pub fn new(path: &str) -> DbResult<Self> {
        let conn = Connection::open(path)?;
        init_db(&conn)?;
        Ok(Self { conn })
    }

    /// Load all execution records for `lift_id`, newest first.
    fn fetch_executions(&self, lift_id: i32) -> DbResult<Vec<LiftExecution>> {
        let mut stmt = self.conn.prepare(
            "SELECT id, date, sets, reps, weight, rpe, notes FROM lift_records WHERE lift_id = ?1 ORDER BY date DESC",
        )?;
        let iter = stmt.query_map(params![lift_id], |row| {
            let date_str: String = row.get(1)?;
            let date = NaiveDate::parse_from_str(&date_str, "%Y-%m-%d").map_err(|e| {
                rusqlite::Error::FromSqlConversionFailure(1, Type::Text, Box::new(e))
            })?;
            let weight_str: String = row.get(4)?;
            Ok(LiftExecution {
                id: Some(row.get(0)?),
                date,
                sets: row.get(2)?,
                reps: row.get(3)?,
                weight: Weight::from_str(&weight_str).unwrap_or(Weight::Raw(0.0)),
                rpe: row.get(5)?,
                notes: row.get(6)?,
            })
        })?;
        let mut executions = Vec::new();
        for exec in iter {
            executions.push(exec?);
        }
        Ok(executions)
    }
}

/// Initialize the database schema and ensure it matches the expected version.
fn init_db(conn: &Connection) -> DbResult<()> {
    let user_version: i32 = conn.query_row("PRAGMA user_version", [], |row| row.get(0))?;
    if user_version == 0 {
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
                sets INTEGER NOT NULL,
                reps INTEGER NOT NULL,
                weight TEXT NOT NULL,
                rpe REAL,
                notes TEXT NOT NULL DEFAULT '',
                FOREIGN KEY(lift_id) REFERENCES lifts(id)
            )",
            [],
        )?;
        conn.pragma_update(None, "user_version", &DB_VERSION)?;
    } else if user_version < DB_VERSION {
        run_migrations(conn, user_version)?;
    }
    Ok(())
}

/// Apply migrations from a previous schema version to [`DB_VERSION`].
fn run_migrations(conn: &Connection, from_version: i32) -> DbResult<()> {
    match from_version {
        1 => {
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
            conn.execute("ALTER TABLE lifts ADD COLUMN main_lift TEXT", [])?;
            conn.execute(
                "ALTER TABLE lifts ADD COLUMN muscles TEXT NOT NULL DEFAULT ''",
                [],
            )?;
            conn.execute(
                "ALTER TABLE lifts ADD COLUMN notes TEXT NOT NULL DEFAULT ''",
                [],
            )?;
        }
        2 => {
            conn.execute("ALTER TABLE lifts ADD COLUMN main_lift TEXT", [])?;
            conn.execute(
                "ALTER TABLE lifts ADD COLUMN muscles TEXT NOT NULL DEFAULT ''",
                [],
            )?;
            conn.execute(
                "ALTER TABLE lifts ADD COLUMN notes TEXT NOT NULL DEFAULT ''",
                [],
            )?;
        }
        3 => {
            conn.execute(
                "ALTER TABLE lifts ADD COLUMN muscles TEXT NOT NULL DEFAULT ''",
                [],
            )?;
            conn.execute(
                "ALTER TABLE lifts ADD COLUMN notes TEXT NOT NULL DEFAULT ''",
                [],
            )?;
        }
        4 => {
            conn.execute(
                "ALTER TABLE lifts ADD COLUMN notes TEXT NOT NULL DEFAULT ''",
                [],
            )?;
        }
        5 => {
            conn.execute(
                "ALTER TABLE lift_records ADD COLUMN notes TEXT NOT NULL DEFAULT ''",
                [],
            )?;
        }
        _ => {}
    }
    conn.pragma_update(None, "user_version", &DB_VERSION)?;
    Ok(())
}

impl Database for SqliteDb {
    fn add_lift(
        &self,
        name: &str,
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
            "INSERT INTO lifts (name, region, main_lift, muscles, notes) VALUES (?1, ?2, ?3, ?4, ?5)",
            params![
                name,
                region.to_string(),
                main.map(|m| m.to_string()),
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
        self.conn.execute(
            "INSERT INTO lift_records (lift_id, date, sets, reps, weight, rpe, notes) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7)",
            params![
                lift_id,
                execution.date.to_string(),
                execution.sets,
                execution.reps,
                execution.weight.to_string(),
                execution.rpe,
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

    fn update_lift_execution(&self, exec_id: i32, execution: &LiftExecution) -> DbResult<()> {
        self.conn.execute(
            "UPDATE lift_records SET date = ?1, sets = ?2, reps = ?3, weight = ?4, rpe = ?5, notes = ?6 WHERE id = ?7",
            params![
                execution.date.to_string(),
                execution.sets,
                execution.reps,
                execution.weight.to_string(),
                execution.rpe,
                execution.notes,
                exec_id
            ],
        )?;
        Ok(())
    }

    fn lift_stats(&self, name: &str) -> DbResult<LiftStats> {
        let lift_id: i32 = self.conn.query_row(
            "SELECT id FROM lifts WHERE name = ?1",
            params![name],
            |row| row.get(0),
        )?;
        let last = self
            .conn
            .query_row(
                "SELECT id, date, sets, reps, weight, rpe, notes FROM lift_records WHERE lift_id = ?1 ORDER BY date DESC LIMIT 1",
                params![lift_id],
                |row| {
                    let date_str: String = row.get(1)?;
                    let date = NaiveDate::parse_from_str(&date_str, "%Y-%m-%d").map_err(|e| {
                        rusqlite::Error::FromSqlConversionFailure(1, Type::Text, Box::new(e))
                    })?;
                    let weight_str: String = row.get(4)?;
                    Ok(LiftExecution {
                        id: Some(row.get(0)?),
                        date,
                        sets: row.get(2)?,
                        reps: row.get(3)?,
                        weight: Weight::from_str(&weight_str).unwrap_or(Weight::Raw(0.0)),
                        rpe: row.get(5)?,
                        notes: row.get(6)?,
                    })
                },
            )
            .optional()?;
        let mut stmt = self.conn.prepare(
            "SELECT reps, MAX(CAST(REPLACE(weight, ' lb', '') AS REAL)) FROM lift_records WHERE lift_id = ?1 AND weight LIKE '% lb' GROUP BY reps",
        )?;
        let iter = stmt.query_map(params![lift_id], |row| Ok((row.get(0)?, row.get(1)?)))?;
        let mut best = BTreeMap::new();
        for row in iter {
            let (reps, weight): (i32, f64) = row?;
            best.insert(reps, Weight::Raw(weight));
        }
        Ok(LiftStats {
            last,
            best_by_reps: best,
        })
    }

    fn list_lifts(&self, name: Option<&str>) -> DbResult<Vec<Lift>> {
        let mut lifts = Vec::new();
        if let Some(n) = name {
            let mut stmt = self.conn.prepare(
                "SELECT id, name, region, main_lift, muscles, notes FROM lifts WHERE name = ?1 ORDER BY name",
            )?;
            let iter = stmt.query_map(params![n], |row| {
                let id: i32 = row.get(0)?;
                let region_str: String = row.get(2)?;
                let region = LiftRegion::from_str(&region_str).unwrap_or(LiftRegion::UPPER);
                let main_str: Option<String> = row.get(3)?;
                let main = match main_str {
                    Some(m) => LiftType::from_str(&m).ok(),
                    None => None,
                };
                let muscles_str: String = row.get(4)?;
                let notes: String = row.get(5)?;
                let muscles = if muscles_str.is_empty() {
                    Vec::new()
                } else {
                    muscles_str
                        .split(',')
                        .filter_map(|m| Muscle::from_str(m).ok())
                        .collect()
                };
                Ok((
                    id,
                    Lift {
                        name: row.get(1)?,
                        region,
                        main,
                        muscles,
                        notes,
                        executions: Vec::new(),
                    },
                ))
            })?;
            for row in iter {
                let (id, mut lift) = row?;
                lift.executions = self.fetch_executions(id)?;
                lifts.push(lift);
            }
        } else {
            let mut stmt = self.conn.prepare(
                "SELECT id, name, region, main_lift, muscles, notes FROM lifts ORDER BY name",
            )?;
            let iter = stmt.query_map([], |row| {
                let id: i32 = row.get(0)?;
                let region_str: String = row.get(2)?;
                let region = LiftRegion::from_str(&region_str).unwrap_or(LiftRegion::UPPER);
                let main_str: Option<String> = row.get(3)?;
                let main = match main_str {
                    Some(m) => LiftType::from_str(&m).ok(),
                    None => None,
                };
                let muscles_str: String = row.get(4)?;
                let notes: String = row.get(5)?;
                let muscles = if muscles_str.is_empty() {
                    Vec::new()
                } else {
                    muscles_str
                        .split(',')
                        .filter_map(|m| Muscle::from_str(m).ok())
                        .collect()
                };
                Ok((
                    id,
                    Lift {
                        name: row.get(1)?,
                        region,
                        main,
                        muscles,
                        notes,
                        executions: Vec::new(),
                    },
                ))
            })?;
            for row in iter {
                let (id, mut lift) = row?;
                lift.executions = self.fetch_executions(id)?;
                lifts.push(lift);
            }
        }
        Ok(lifts)
    }
}
