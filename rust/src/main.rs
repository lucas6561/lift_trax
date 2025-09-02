//! Command-line interface for recording and listing lifts.

use chrono::{NaiveDate, Utc, Weekday};
use clap::{Parser, Subcommand};
use std::fs;

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

fn single_desc(s: &workout_builder::SingleLift, count: usize) -> String {
    let mut parts = vec![format!("**{}**", s.lift.name)];
    if let Some(metric) = &s.metric {
        use SetMetric::*;
        let metric_str = match metric {
            Reps(r) => format!("{} reps", r),
            TimeSecs(t) => format!("{}s", t),
            DistanceFeet(d) => format!("{}ft", d),
        };
        if count > 1 {
            parts.push(format!("{}x {}", count, metric_str));
        } else {
            parts.push(metric_str);
        }
    } else if count > 1 {
        parts.push(format!("{}x", count));
    }
    if let Some(percent) = s.percent {
        parts.push(format!("@ {}%", percent));
    }
    if let Some(ar) = &s.accommodating_resistance {
        use workout_builder::AccommodatingResistance::*;
        match ar {
            None => {}
            Chains => parts.push("Chains".into()),
            Bands => parts.push("Bands".into()),
        }
    }
    parts.join(" ")
}

fn same_single(a: &workout_builder::SingleLift, b: &workout_builder::SingleLift) -> bool {
    a.lift.name == b.lift.name
        && a.metric == b.metric
        && a.percent == b.percent
        && a.accommodating_resistance == b.accommodating_resistance
}

fn format_exec(exec: &LiftExecution) -> String {
    if exec.sets.is_empty() {
        return "no sets recorded".into();
    }
    let first = &exec.sets[0];
    let rpe = first.rpe.map(|r| format!(" RPE {}", r)).unwrap_or_default();
    let metric_str = match first.metric {
        SetMetric::Reps(r) => format!("{} reps", r),
        SetMetric::TimeSecs(t) => format!("{}s", t),
        SetMetric::DistanceFeet(d) => format!("{}ft", d),
    };
    format!(
        "{} sets x {} @ {}{}",
        exec.sets.len(),
        metric_str,
        first.weight,
        rpe
    )
}

fn last_exec_desc(db: &dyn Database, name: &str, warmup: bool) -> Option<String> {
    let lifts = db.list_lifts(Some(name)).ok()?;
    let lift = lifts.into_iter().next()?;
    for exec in lift.executions {
        if exec.warmup == warmup {
            return Some(format_exec(&exec));
        }
    }
    None
}

fn workout_lines(w: &workout_builder::Workout, db: &dyn Database) -> Vec<String> {
    let mut lines = Vec::new();
    let mut i = 0usize;
    while i < w.lifts.len() {
        match &w.lifts[i] {
            workout_builder::WorkoutLift::Single(s) => {
                let mut count = 1usize;
                while i + count < w.lifts.len() {
                    if let workout_builder::WorkoutLift::Single(next) = &w.lifts[i + count] {
                        if same_single(s, next) {
                            count += 1;
                            continue;
                        }
                    }
                    break;
                }
                lines.push(single_desc(s, count));
                if let Some(desc) = last_exec_desc(db, &s.lift.name, false) {
                    lines.push(format!("Last: {}", desc));
                }
                i += count;
            }
            workout_builder::WorkoutLift::Circuit(c) => {
                let line = c
                    .circuit_lifts
                    .iter()
                    .map(|sl| single_desc(sl, 1))
                    .collect::<Vec<_>>()
                    .join(" -> ");
                lines.push(format!(
                    "{} rounds (rest {}s): {}",
                    c.rounds, c.rest_time_sec, line
                ));
                let warmup = c
                    .circuit_lifts
                    .iter()
                    .all(|sl| sl.metric.is_none() && sl.percent == Some(40));
                for sl in &c.circuit_lifts {
                    if let Some(desc) = last_exec_desc(db, &sl.lift.name, warmup) {
                        lines.push(format!("**{}**: {}", sl.lift.name, desc));
                    }
                }
                i += 1;
            }
        }
    }
    lines
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
            let mut out_lines = Vec::new();
            for (i, week) in wave.iter().enumerate() {
                let header = format!("## Week {}", i + 1);
                println!("{}", header);
                out_lines.push(header);
                out_lines.push(String::new());
                for day in [Weekday::Mon, Weekday::Tue, Weekday::Thu, Weekday::Fri] {
                    if let Some(w) = week.get(&day) {
                        let day_header = format!("### {}", day_name(day));
                        println!("{}", day_header);
                        out_lines.push(day_header);
                        for line in workout_lines(w, db.as_ref()) {
                            let bullet = format!("- {}", line);
                            println!("{}", bullet);
                            out_lines.push(bullet);
                        }
                        println!();
                        out_lines.push(String::new());
                    }
                }
            }
            fs::write("wave.txt", out_lines.join("\n"))?;
        }
        Commands::Gui => {
            gui::run_gui(db)?;
        }
    }
    Ok(())
}
