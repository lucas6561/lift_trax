//! Command-line interface for recording and listing lifts.

use chrono::{NaiveDate, Utc, Weekday};
use clap::{Parser, Subcommand};
use std::fs;

use lift_trax_cli::database::Database;
use lift_trax_cli::gui;
use lift_trax_cli::list::{filter_lifts, summarize_recent_executions};
use lift_trax_cli::models::{ExecutionSet, LiftExecution, LiftRegion, LiftType, Muscle, SetMetric};
use lift_trax_cli::sqlite_db::SqliteDb;
use lift_trax_cli::wave_view::workout_lines;
use lift_trax_cli::weight::Weight;
use lift_trax_cli::workout_builder::{self, WorkoutWeek};

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
        #[arg(required_unless_present_all = ["left_weight", "right_weight"])]
        weight: Option<String>,
        /// Weight used on the left side when different from the right
        #[arg(long = "left-weight", requires = "right_weight")]
        left_weight: Option<String>,
        /// Weight used on the right side when different from the left
        #[arg(long = "right-weight", requires = "left_weight")]
        right_weight: Option<String>,
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
        /// Only include lifts that target these muscles
        #[arg(long = "muscle", value_enum)]
        muscles: Vec<Muscle>,
    },
    /// Generate an example conjugate wave
    Wave {
        /// Number of weeks to generate
        #[arg(long, default_value_t = 7)]
        weeks: usize,
    },
    /// Launch graphical interface
    Gui,
}

fn seed_example_lifts(db: &dyn Database) {
    use lift_trax_cli::models::{LiftRegion, LiftType};
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
    let _ = db.add_lift(
        "Sumo Deadlift",
        LiftRegion::LOWER,
        LiftType::Deadlift,
        &[],
        "",
    );
    let _ = db.add_lift(
        "Deficit Deadlift",
        LiftRegion::LOWER,
        LiftType::Deadlift,
        &[],
        "",
    );
    let _ = db.add_lift(
        "Bench Press",
        LiftRegion::UPPER,
        LiftType::BenchPress,
        &[],
        "",
    );
    let _ = db.add_lift(
        "Close-Grip Bench Press",
        LiftRegion::UPPER,
        LiftType::BenchPress,
        &[],
        "",
    );
    let _ = db.add_lift(
        "Floor Press",
        LiftRegion::UPPER,
        LiftType::BenchPress,
        &[],
        "",
    );
    let _ = db.add_lift(
        "Overhead Press",
        LiftRegion::UPPER,
        LiftType::OverheadPress,
        &[],
        "",
    );
    let _ = db.add_lift(
        "Push Press",
        LiftRegion::UPPER,
        LiftType::OverheadPress,
        &[],
        "",
    );
    let _ = db.add_lift(
        "Seated Overhead Press",
        LiftRegion::UPPER,
        LiftType::OverheadPress,
        &[],
        "",
    );
}

fn day_name(day: Weekday) -> &'static str {
    match day {
        Weekday::Mon => "Monday",
        Weekday::Tue => "Tuesday",
        Weekday::Wed => "Wednesday",
        Weekday::Thu => "Thursday",
        Weekday::Fri => "Friday",
        Weekday::Sat => "Saturday",
        Weekday::Sun => "Sunday",
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
            left_weight,
            right_weight,
            notes,
        } => {
            let date = match date {
                Some(d) => NaiveDate::parse_from_str(&d, "%Y-%m-%d")?,
                None => Utc::now().date_naive(),
            };
            let real_weight: Weight = match (weight, left_weight, right_weight) {
                (Some(w), None, None) => w.parse().map_err(|_| "Invalid weight supplied")?,
                (None, Some(left), Some(right)) => format!("{}|{}", left, right)
                    .parse()
                    .map_err(|_| "Invalid left/right weight supplied")?,
                _ => {
                    return Err(
                        "Provide a single weight or both --left-weight and --right-weight".into(),
                    );
                }
            };
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
                deload: false,
                notes: notes.unwrap_or_default(),
            };
            if let Some(main) = lift_type {
                let _ = db.add_lift(&exercise, LiftRegion::UPPER, main, &muscles, "");
            }
            db.add_lift_execution(&exercise, &exec)?;
            println!("Lift execution added.");
        }
        Commands::List { exercise, muscles } => {
            let lifts = db.list_lifts()?;
            let lifts = filter_lifts(lifts, exercise, muscles);
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
                let executions = db.get_executions(&l.name);
                if executions.is_empty() {
                    println!("  - no records");
                } else {
                    let summaries = summarize_recent_executions(&executions, 3);
                    println!("  - {}", summaries.join(" | "));
                }
            }
        }
        Commands::Wave { weeks } => {
            use workout_builder::{ConjugateWorkoutBuilder, WorkoutBuilder};
            //seed_example_lifts(db.as_ref());
            let builder = ConjugateWorkoutBuilder;
            let wave = builder.get_wave(weeks, db.as_ref())?;
            let out_lines = create_markdown(&wave, db.as_ref());
            fs::write("wave.md", out_lines.join("\n"))?;
        }
        Commands::Gui => {
            gui::run_gui(db)?;
        }
    }
    Ok(())
}

fn create_markdown(wave: &Vec<WorkoutWeek>, db: &dyn Database) -> Vec<String> {
    let mut out_lines = Vec::new();
    for (i, week) in wave.iter().enumerate() {
        let week_header = format!("# Week {}", i + 1);
        out_lines.push(week_header);
        for day in [
            Weekday::Mon,
            Weekday::Tue,
            Weekday::Wed,
            Weekday::Thu,
            Weekday::Fri,
            Weekday::Sat,
            Weekday::Sun,
        ] {
            if let Some(w) = week.get(&day) {
                let day_header = format!("## {}", day_name(day));
                out_lines.push(day_header);
                for line in workout_lines(w, db) {
                    out_lines.push(line);
                }
                out_lines.push(String::new());
            }
        }
        out_lines.push(String::new());
    }
    for s in &out_lines {
        println!("{}", s);
    }
    out_lines
}
