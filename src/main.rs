use chrono::Utc;
use clap::{Parser, Subcommand};

mod database;
mod models;
mod sqlite_db;

use database::Database;
use models::Lift;
use sqlite_db::SqliteDb;

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

fn main() -> Result<(), Box<dyn std::error::Error>> {
    let cli = Cli::parse();
    let db: Box<dyn Database> = Box::new(SqliteDb::new("lifts.db")?);
    match cli.command {
        Commands::Add {
            exercise,
            weight,
            reps,
            date,
        } => {
            let date = date.unwrap_or_else(|| Utc::now().date_naive().to_string());
            let lift = Lift {
                date,
                exercise,
                weight,
                reps,
            };
            db.add_lift(&lift)?;
            println!("Lift added.");
        }
        Commands::List { exercise } => {
            let lifts = db.list_lifts(exercise.as_deref())?;
            for l in lifts {
                println!("{}: {} - {} lbs x {}", l.date, l.exercise, l.weight, l.reps);
            }
        }
    }
    Ok(())
}
