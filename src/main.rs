//! Command-line interface for recording and listing lifts.

use chrono::{NaiveDate, Utc, Weekday};
use clap::{Parser, Subcommand};

mod database;
mod gui;
mod models;
mod sqlite_db;
mod weight;
mod workout_builder;

use crate::weight::Weight;
use database::Database;
use models::{ExecutionSet, LiftExecution, LiftRegion, LiftType, Muscle, SetMetric};
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
        /// Lift type used if creating a new lift
        #[arg(long = "type")]
        lift_type: Option<LiftType>,
        /// Free-form notes about this execution
        #[arg(long)]
        notes: Option<String>,
    },
    /// List recorded lifts
    List {
        /// Filter by exercise
        #[arg(long)]
        exercise: Option<String>,
    },
    /// Generate an example conjugate wave
    Wave {
        /// Number of weeks to generate
        #[arg(long, default_value_t = 6)]
        weeks: usize,
    },
    /// Launch graphical interface
    Gui,
}

fn seed_example_lifts(db: &dyn Database) {
    use models::{LiftRegion, LiftType};
    let _ = db.add_lift("Back Squat", LiftRegion::LOWER, LiftType::Squat, &[], "");
    let _ = db.add_lift("Front Squat", LiftRegion::LOWER, LiftType::Squat, &[], "");
    let _ = db.add_lift("Box Squat", LiftRegion::LOWER, LiftType::Squat, &[], "");
    let _ = db.add_lift(
        "Conventional Deadlift",
        LiftRegion::LOWER,
        LiftType::Deadlift,
        &[],
        "",
    );
    let _ = db.add_lift("Sumo Deadlift", LiftRegion::LOWER, LiftType::Deadlift, &[], "");
    let _ = db.add_lift(
        "Deficit Deadlift",
        LiftRegion::LOWER,
        LiftType::Deadlift,
        &[],
        "",
    );
    let _ = db.add_lift("Bench Press", LiftRegion::UPPER, LiftType::BenchPress, &[], "");
    let _ = db.add_lift(
        "Close-Grip Bench Press",
        LiftRegion::UPPER,
        LiftType::BenchPress,
        &[],
        "",
    );
    let _ = db.add_lift("Floor Press", LiftRegion::UPPER, LiftType::BenchPress, &[], "");
    let _ = db.add_lift(
        "Overhead Press",
        LiftRegion::UPPER,
        LiftType::OverheadPress,
        &[],
        "",
    );
    let _ = db.add_lift("Push Press", LiftRegion::UPPER, LiftType::OverheadPress, &[], "");
    let _ = db.add_lift(
        "Seated Overhead Press",
        LiftRegion::UPPER,
        LiftType::OverheadPress,
        &[],
        "",
    );
}

fn workout_desc(w: &workout_builder::Workout) -> String {
    w.lifts
        .iter()
        .map(|l| match l {
            workout_builder::WorkoutLift::Single(s) => s.lift.name.clone(),
            workout_builder::WorkoutLift::Circuit(c) => c
                .circuit_lifts
                .iter()
                .map(|sl| sl.lift.name.clone())
                .collect::<Vec<_>>()
                .join(" -> "),
        })
        .collect::<Vec<_>>()
        .join(", ")
}

fn day_name(day: Weekday) -> &'static str {
    match day {
        Weekday::Mon => "Mon",
        Weekday::Tue => "Tue",
        Weekday::Wed => "Wed",
        Weekday::Thu => "Thu",
        Weekday::Fri => "Fri",
        Weekday::Sat => "Sat",
        Weekday::Sun => "Sun",
    }
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
            lift_type,
            notes,
        } => {
            let date = match date {
                Some(d) => NaiveDate::parse_from_str(&d, "%Y-%m-%d")?,
                None => Utc::now().date_naive(),
            };
            let real_weight: Weight = weight.parse().map_err(|_| "Invalid weight supplied")?;
            let set = ExecutionSet {
                metric: SetMetric::Reps(reps),
                weight: real_weight.clone(),
                rpe,
            };
            let sets_vec = vec![set; sets as usize];
            let exec = LiftExecution {
                id: None,
                date,
                sets: sets_vec,
                warmup: false,
                notes: notes.unwrap_or_default(),
            };
            if let Some(main) = lift_type {
                let _ = db.add_lift(&exercise, LiftRegion::UPPER, main, &muscles, "");
            }
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
                        let set_desc = exec
                            .sets
                            .iter()
                            .map(|s| {
                                let rpe = s.rpe.map(|r| format!(" RPE {}", r)).unwrap_or_default();
                                format!("{} @ {}{}", s.metric, s.weight, rpe)
                            })
                            .collect::<Vec<_>>()
                            .join(", ");
                        let notes_str = if exec.notes.is_empty() {
                            String::new()
                        } else {
                            format!(" - {}", exec.notes)
                        };
                        println!("  - {}: {}{}", exec.date, set_desc, notes_str);
                    }
                }
            }
        }
        Commands::Wave { weeks } => {
            use workout_builder::{ConjugateWorkoutBuilder, WorkoutBuilder};
            //seed_example_lifts(db.as_ref());
            let builder = ConjugateWorkoutBuilder;
            let wave = builder.get_wave(weeks, db.as_ref())?;
            for (i, week) in wave.iter().enumerate() {
                println!("Week {}", i + 1);
                for day in [Weekday::Mon, Weekday::Tue, Weekday::Thu, Weekday::Fri] {
                    if let Some(w) = week.get(&day) {
                        println!("  {}: {}", day_name(day), workout_desc(w));
                    }
                }
                println!();
            }
        }
        Commands::Gui => {
            gui::run_gui(db)?;
        }
    }
    Ok(())
}
