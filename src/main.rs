use clap::{Parser, Subcommand};
use rusqlite::{params, Connection};
use chrono::Utc;

#[derive(Parser)]
#[command(name = "lift_trax", version, about = "Track your lifts")]
struct Cli {
    #[command(subcommand)]
    command: Commands,
}

#[derive(Subcommand)]
enum Commands {
    /// Add a lift record
    Add {
        /// Exercise name
        exercise: String,
        /// Weight lifted
        weight: f32,
        /// Number of reps
        reps: i32,
        /// Date of lift in YYYY-MM-DD, defaults to today
        #[arg(long)]
        date: Option<String>,
    },
    /// List recorded lifts
    List {
        /// Filter by exercise
        #[arg(long)]
        exercise: Option<String>,
    },
}

struct Lift {
    date: String,
    exercise: String,
    weight: f32,
    reps: i32,
}

fn init_db(conn: &Connection) -> rusqlite::Result<()> {
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
    Ok(())
}

fn main() -> rusqlite::Result<()> {
    let cli = Cli::parse();
    let conn = Connection::open("lifts.db")?;
    init_db(&conn)?;
    match cli.command {
        Commands::Add { exercise, weight, reps, date } => {
            let date = date.unwrap_or_else(|| Utc::now().date_naive().to_string());
            conn.execute(
                "INSERT INTO lifts (date, exercise, weight, reps) VALUES (?1, ?2, ?3, ?4)",
                params![date, exercise, weight, reps],
            )?;
            println!("Lift added.");
        }
        Commands::List { exercise } => {
            match exercise {
                Some(ex) => {
                    let mut stmt = conn.prepare(
                        "SELECT date, exercise, weight, reps FROM lifts WHERE exercise = ?1 ORDER BY date DESC",
                    )?;
                    let lifts = stmt.query_map(params![ex], |row| {
                        Ok(Lift {
                            date: row.get(0)?,
                            exercise: row.get(1)?,
                            weight: row.get(2)?,
                            reps: row.get(3)?,
                        })
                    })?;
                    for lift in lifts {
                        let l = lift?;
                        println!("{}: {} - {} lbs x {}", l.date, l.exercise, l.weight, l.reps);
                    }
                }
                None => {
                    let mut stmt = conn.prepare(
                        "SELECT date, exercise, weight, reps FROM lifts ORDER BY date DESC",
                    )?;
                    let lifts = stmt.query_map([], |row| {
                        Ok(Lift {
                            date: row.get(0)?,
                            exercise: row.get(1)?,
                            weight: row.get(2)?,
                            reps: row.get(3)?,
                        })
                    })?;
                    for lift in lifts {
                        let l = lift?;
                        println!("{}: {} - {} lbs x {}", l.date, l.exercise, l.weight, l.reps);
                    }
                }
            }
        }
    }
    Ok(())
}
