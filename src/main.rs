//! Command-line interface for recording and listing lifts.

use chrono::{NaiveDate, Utc};
use clap::{Parser, Subcommand};

mod database;
mod gui;
mod models;
mod sqlite_db;
mod weight;

use crate::weight::Weight;
use database::Database;
use models::{LiftExecution, LiftRegion, Muscle};
use sqlite_db::SqliteDb;

#[derive(Parser)]
#[command(name = "lift_trax", version, about = "Track your lifts")]
struct Cli {
    #[command(subcommand)]
    command: Commands,
}

#[derive(Subcommand)]
enum Commands {
    /// Add a lift execution
    Add {
        /// Exercise name
        exercise: String,
        /// Weight lifted
        weight: String,
        /// Number of reps
        reps: i32,
        /// Number of sets
        sets: i32,
        /// Date of lift in YYYY-MM-DD, defaults to today
        #[arg(long)]
        date: Option<String>,
        /// Rating of perceived exertion
        #[arg(long)]
        rpe: Option<f32>,
        /// Muscles targeted by this lift
        #[arg(long = "muscle", value_enum)]
        muscles: Vec<Muscle>,
    },
    /// List recorded lifts
    List {
        /// Filter by exercise
        #[arg(long)]
        exercise: Option<String>,
    },
    /// Launch graphical interface
    Gui,
}

fn main() -> Result<(), Box<dyn std::error::Error>> {
    let cli = Cli::parse();
    let db: Box<dyn Database> = Box::new(SqliteDb::new("lifts.db")?);
    match cli.command {
        Commands::Add {
            exercise,
            weight,
            reps,
            sets,
            date,
            rpe,
            muscles,
        } => {
            let date = match date {
                Some(d) => NaiveDate::parse_from_str(&d, "%Y-%m-%d")?,
                None => Utc::now().date_naive(),
            };
            let real_weight: Weight = weight.parse().map_err(|_| "Invalid weight supplied")?;
            let exec = LiftExecution {
                id: None,
                date,
                sets,
                reps,
                weight: real_weight,
                rpe,
            };
            // Ensure the lift exists with a default region and no main designation.
            let _ = db.add_lift(&exercise, LiftRegion::UPPER, None, &muscles, "");
            db.add_lift_execution(&exercise, &exec)?;
            println!("Lift execution added.");
        }
        Commands::List { exercise } => {
            let lifts = db.list_lifts(exercise.as_deref())?;
            for l in lifts {
                let main_str = l.main.map(|m| format!(" [{}]", m)).unwrap_or_default();
                let muscles_str = if l.muscles.is_empty() {
                    String::new()
                } else {
                    format!(
                        " [{}]",
                        l.muscles
                            .iter()
                            .map(|m| m.to_string())
                            .collect::<Vec<_>>()
                            .join(", ")
                    )
                };
                let notes_str = if l.notes.is_empty() {
                    String::new()
                } else {
                    format!(" - {}", l.notes)
                };
                println!(
                    "{} ({}){}{}{}",
                    l.name, l.region, main_str, muscles_str, notes_str
                );
                if l.executions.is_empty() {
                    println!("  - no records");
                } else {
                    for exec in l.executions {
                        let rpe_str = exec.rpe.map(|r| format!(" RPE {}", r)).unwrap_or_default();
                        println!(
                            "  - {}: {} sets x {} reps @ {}{}",
                            exec.date, exec.sets, exec.reps, exec.weight, rpe_str
                        );
                    }
                }
            }
        }
        Commands::Gui => {
            gui::run_gui(db)?;
        }
    }
    Ok(())
}
