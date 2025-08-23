//! SQLite-backed implementation of the [`Database`] trait.

use chrono::NaiveDate;
use rusqlite::{Connection, OptionalExtension, params, types::Type};

use crate::{
    database::{Database, DbResult},
    models::{Lift, LiftExecution},
};

/// Database persisted to a SQLite file.
pub struct SqliteDb {
    conn: Connection,
}

impl SqliteDb {
    /// Open (or create) a SQLite database at `path`.
    pub fn new(path: &str) -> DbResult<Self> {
        let conn = Connection::open(path)?;
        conn.execute(
            "CREATE TABLE IF NOT EXISTS lifts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE,
                muscles TEXT NOT NULL
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
                weight REAL NOT NULL,
                rpe REAL,
                FOREIGN KEY(lift_id) REFERENCES lifts(id)
            )",
            [],
        )?;
        Ok(Self { conn })
    }

    /// Load all execution records for `lift_id`, newest first.
    fn fetch_executions(&self, lift_id: i32) -> DbResult<Vec<LiftExecution>> {
        let mut stmt = self.conn.prepare(
            "SELECT date, sets, reps, weight, rpe FROM lift_records WHERE lift_id = ?1 ORDER BY date DESC",
        )?;
        let iter = stmt.query_map(params![lift_id], |row| {
            let date_str: String = row.get(0)?;
            let date = NaiveDate::parse_from_str(&date_str, "%Y-%m-%d")
                .map_err(|e| rusqlite::Error::FromSqlConversionFailure(0, Type::Text, Box::new(e)))?;
            Ok(LiftExecution {
                date,
                sets: row.get(1)?,
                reps: row.get(2)?,
                weight: row.get(3)?,
                rpe: row.get(4)?,
            })
        })?;
        let mut executions = Vec::new();
        for exec in iter {
            executions.push(exec?);
        }
        Ok(executions)
    }
}

impl Database for SqliteDb {
    fn add_lift_execution(
        &self,
        name: &str,
        muscles: &[String],
        execution: &LiftExecution,
    ) -> DbResult<()> {
        let mut stmt = self.conn.prepare("SELECT id FROM lifts WHERE name = ?1")?;
        let lift_id: Option<i32> = stmt.query_row(params![name], |row| row.get(0)).optional()?;
        let lift_id = match lift_id {
            Some(id) => id,
            None => {
                let muscles_str = muscles.join(",");
                self.conn.execute(
                    "INSERT INTO lifts (name, muscles) VALUES (?1, ?2)",
                    params![name, muscles_str],
                )?;
                self.conn.last_insert_rowid() as i32
            }
        };
        self.conn.execute(
            "INSERT INTO lift_records (lift_id, date, sets, reps, weight, rpe) VALUES (?1, ?2, ?3, ?4, ?5, ?6)",
            params![
                lift_id,
                execution.date.to_string(),
                execution.sets,
                execution.reps,
                execution.weight,
                execution.rpe
            ],
        )?;
        Ok(())
    }

    fn list_lifts(&self, name: Option<&str>) -> DbResult<Vec<Lift>> {
        let mut lifts = Vec::new();
        if let Some(n) = name {
            let mut stmt = self
                .conn
                .prepare("SELECT id, name, muscles FROM lifts WHERE name = ?1 ORDER BY name")?;
            let iter = stmt.query_map(params![n], |row| {
                let muscles: String = row.get(2)?;
                let muscles_vec = if muscles.is_empty() {
                    Vec::new()
                } else {
                    muscles.split(',').map(|s| s.to_string()).collect()
                };
                Ok(Lift {
                    id: row.get(0)?,
                    name: row.get(1)?,
                    muscles: muscles_vec,
                    executions: Vec::new(),
                })
            })?;
            for lift in iter {
                let mut lift = lift?;
                lift.executions = self.fetch_executions(lift.id)?;
                lifts.push(lift);
            }
        } else {
            let mut stmt = self
                .conn
                .prepare("SELECT id, name, muscles FROM lifts ORDER BY name")?;
            let iter = stmt.query_map([], |row| {
                let muscles: String = row.get(2)?;
                let muscles_vec = if muscles.is_empty() {
                    Vec::new()
                } else {
                    muscles.split(',').map(|s| s.to_string()).collect()
                };
                Ok(Lift {
                    id: row.get(0)?,
                    name: row.get(1)?,
                    muscles: muscles_vec,
                    executions: Vec::new(),
                })
            })?;
            for lift in iter {
                let mut lift = lift?;
                lift.executions = self.fetch_executions(lift.id)?;
                lifts.push(lift);
            }
        }
        Ok(lifts)
    }
}
