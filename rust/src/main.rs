//! Command-line interface for recording and listing lifts.

use chrono::{NaiveDate, Utc, Weekday};
use clap::{Parser, Subcommand};
use std::fs;

use lift_trax_cli::database::Database;
use lift_trax_cli::gui;
use lift_trax_cli::list::filter_lifts;
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

fn summarize_recent_executions(executions: &[LiftExecution], limit: usize) -> Vec<String> {
    if limit == 0 {
        return Vec::new();
    }

    let mut sorted: Vec<&LiftExecution> = executions.iter().collect();
    sorted.sort_by(|a, b| {
        b.date.cmp(&a.date).then_with(|| {
            let a_id = a.id.unwrap_or(i32::MIN);
            let b_id = b.id.unwrap_or(i32::MIN);
            b_id.cmp(&a_id)
        })
    });

    sorted
        .into_iter()
        .take(limit)
        .map(|exec| exec.to_string())
        .collect()
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
                if l.executions.is_empty() {
                    println!("  - no records");
                } else {
                    let summaries = summarize_recent_executions(&l.executions, 3);
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

#[cfg(test)]
mod tests {
    use super::*;
    use chrono::NaiveDate;
    use lift_trax_cli::models::{ExecutionSet, LiftExecution, SetMetric};
    use lift_trax_cli::weight::Weight;

    fn make_execution(id: Option<i32>, date: &str) -> LiftExecution {
        let note = id.map(|value| format!("id {}", value)).unwrap_or_default();
        LiftExecution {
            id,
            date: NaiveDate::parse_from_str(date, "%Y-%m-%d").unwrap(),
            sets: vec![ExecutionSet {
                metric: SetMetric::Reps(5),
                weight: Weight::Raw(135.0),
                rpe: None,
            }],
            warmup: false,
            deload: false,
            notes: note,
        }
    }

    #[test]
    fn summarize_recent_executions_returns_three_newest_entries() {
        let executions = vec![
            make_execution(Some(1), "2024-01-01"),
            make_execution(Some(2), "2024-01-03"),
            make_execution(Some(3), "2024-01-02"),
            make_execution(Some(4), "2024-01-04"),
        ];

        let summaries = summarize_recent_executions(&executions, 3);

        assert_eq!(summaries.len(), 3);
        assert!(summaries[0].starts_with("2024-01-04"));
        assert!(summaries[1].starts_with("2024-01-03"));
        assert!(summaries[2].starts_with("2024-01-02"));
    }

    #[test]
    fn summarize_recent_executions_breaks_ties_with_identifier() {
        let executions = vec![
            make_execution(Some(10), "2024-02-01"),
            make_execution(Some(20), "2024-02-01"),
            make_execution(Some(30), "2024-01-31"),
        ];

        let summaries = summarize_recent_executions(&executions, 2);

        assert_eq!(summaries.len(), 2);
        assert!(summaries[0].ends_with(" - id 20"));
        assert!(summaries[1].ends_with(" - id 10"));
    }

    #[test]
    fn summarize_recent_executions_handles_shorter_history() {
        let executions = vec![make_execution(None, "2024-03-15")];

        let summaries = summarize_recent_executions(&executions, 3);

        assert_eq!(summaries.len(), 1);
        assert!(summaries[0].starts_with("2024-03-15"));
    }
}
