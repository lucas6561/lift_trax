//! SQLite-backed implementation of the [`Database`] trait.

use chrono::NaiveDate;
use rusqlite::{Connection, OptionalExtension, params, types::Type};

use crate::weight::{Weight, WeightUnit};
use crate::{
    database::{Database, DbResult},
    models::{Lift, LiftExecution},
};

/// Current database schema version.
const DB_VERSION: i32 = 1;

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
            "SELECT date, sets, reps, weight, rpe FROM lift_records WHERE lift_id = ?1 ORDER BY date DESC",
        )?;
        let iter = stmt.query_map(params![lift_id], |row| {
            let date_str: String = row.get(0)?;
            let date = NaiveDate::parse_from_str(&date_str, "%Y-%m-%d").map_err(|e| {
                rusqlite::Error::FromSqlConversionFailure(0, Type::Text, Box::new(e))
            })?;
            Ok(LiftExecution {
                date,
                sets: row.get(1)?,
                reps: row.get(2)?,
                weight: Weight::new(WeightUnit::POUNDS, row.get(3)?),
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

/// Initialize the database schema and ensure it matches the expected version.
fn init_db(conn: &Connection) -> DbResult<()> {
    let user_version: i32 = conn.query_row("PRAGMA user_version", [], |row| row.get(0))?;
    if user_version == 0 {
        conn.execute(
            "CREATE TABLE IF NOT EXISTS lifts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE
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
        conn.pragma_update(None, "user_version", &DB_VERSION)?;
    } else if user_version < DB_VERSION {
        run_migrations(conn, user_version)?;
    }
    Ok(())
}

/// Apply migrations from a previous schema version to [`DB_VERSION`].
fn run_migrations(conn: &Connection, from_version: i32) -> DbResult<()> {
    match from_version {
        // No migrations defined yet.
        _ => {}
    }
    conn.pragma_update(None, "user_version", &DB_VERSION)?;
    Ok(())
}

impl Database for SqliteDb {
    fn add_lift(&self, name: &str) -> DbResult<()> {
        self.conn.execute(
            "INSERT INTO lifts (name) VALUES (?1)",
            params![name],
        )?;
        Ok(())
    }

    fn add_lift_execution(
        &self,
        name: &str,
        execution: &LiftExecution,
    ) -> DbResult<()> {
        let mut stmt = self.conn.prepare("SELECT id FROM lifts WHERE name = ?1")?;
        let lift_id: Option<i32> = stmt.query_row(params![name], |row| row.get(0)).optional()?;
        let lift_id = match lift_id {
            Some(id) => id,
            None => {
                self.conn.execute(
                    "INSERT INTO lifts (name) VALUES (?1, ?2)",
                    params![name],
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
                execution.weight.pounds(),
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
                .prepare("SELECT id, name FROM lifts WHERE name = ?1 ORDER BY name")?;
            let iter = stmt.query_map(params![n], |row| {
                let id: i32 = row.get(0)?;
                Ok((
                    id,
                    Lift {
                        name: row.get(1)?,
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
            let mut stmt = self
                .conn
                .prepare("SELECT id, name FROM lifts ORDER BY name")?;
            let iter = stmt.query_map([], |row| {
                let id: i32 = row.get(0)?;
                Ok((
                    id,
                    Lift {
                        name: row.get(1)?,
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
