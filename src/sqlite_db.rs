use rusqlite::{Connection, params};

use crate::{
    database::{Database, DbResult},
    models::Lift,
};

pub struct SqliteDb {
    conn: Connection,
}

impl SqliteDb {
    pub fn new(path: &str) -> DbResult<Self> {
        let conn = Connection::open(path)?;
        conn.execute(
            "CREATE TABLE IF NOT EXISTS lifts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                date TEXT NOT NULL,
                exercise TEXT NOT NULL,
                weight REAL NOT NULL,
                reps INTEGER NOT NULL
            )",
            [],
        )?;
        Ok(Self { conn })
    }
}

impl Database for SqliteDb {
    fn add_lift(&self, lift: &Lift) -> DbResult<()> {
        self.conn.execute(
            "INSERT INTO lifts (date, exercise, weight, reps) VALUES (?1, ?2, ?3, ?4)",
            params![lift.date, lift.exercise, lift.weight, lift.reps],
        )?;
        Ok(())
    }

    fn list_lifts(&self, exercise: Option<&str>) -> DbResult<Vec<Lift>> {
        let mut stmt;
        let lifts_iter;
        if let Some(ex) = exercise {
            stmt = self.conn.prepare(
                "SELECT date, exercise, weight, reps FROM lifts WHERE exercise = ?1 ORDER BY date DESC",
            )?;
            lifts_iter = stmt.query_map(params![ex], |row| {
                Ok(Lift {
                    date: row.get(0)?,
                    exercise: row.get(1)?,
                    weight: row.get(2)?,
                    reps: row.get(3)?,
                })
            })?;
        } else {
            stmt = self
                .conn
                .prepare("SELECT date, exercise, weight, reps FROM lifts ORDER BY date DESC")?;
            lifts_iter = stmt.query_map([], |row| {
                Ok(Lift {
                    date: row.get(0)?,
                    exercise: row.get(1)?,
                    weight: row.get(2)?,
                    reps: row.get(3)?,
                })
            })?;
        }
        let mut lifts = Vec::new();
        for lift in lifts_iter {
            lifts.push(lift?);
        }
        Ok(lifts)
    }
}
