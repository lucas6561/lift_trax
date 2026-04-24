#[path = "../src/database.rs"]
mod database;
#[path = "../src/models/mod.rs"]
mod models;
#[path = "../src/random_stack.rs"]
mod random_stack;
#[path = "../src/sqlite_db.rs"]
mod sqlite_db;
#[path = "../src/wave_view.rs"]
mod wave_view;
#[path = "../src/weight.rs"]
mod weight;
#[path = "../src/workout_builder/mod.rs"]
mod workout_builder;

use chrono::Weekday;
use database::Database;
use models::{LiftRegion, LiftType, Muscle};
use random_stack::RandomMode;
use sqlite_db::SqliteDb;
use std::fs;
use workout_builder::{ConjugateWorkoutBuilder, WorkoutWeek};

fn create_markdown(wave: &[WorkoutWeek], db: &dyn Database) -> Vec<String> {
    let mut out = Vec::new();
    for (i, week) in wave.iter().enumerate() {
        out.push(format!("# Week {}", i + 1));
        for day in [
            Weekday::Mon,
            Weekday::Tue,
            Weekday::Wed,
            Weekday::Thu,
            Weekday::Fri,
            Weekday::Sat,
            Weekday::Sun,
        ] {
            if let Some(workout) = week.get(&day) {
                let title = match day {
                    Weekday::Mon => "Monday",
                    Weekday::Tue => "Tuesday",
                    Weekday::Wed => "Wednesday",
                    Weekday::Thu => "Thursday",
                    Weekday::Fri => "Friday",
                    Weekday::Sat => "Saturday",
                    Weekday::Sun => "Sunday",
                };
                out.push(format!("## {title}"));
                out.extend(wave_view::workout_lines(workout, db));
                out.push(String::new());
            }
        }
        out.push(String::new());
    }
    out
}

#[test]
fn conjugate_wave_markdown_matches_parity_fixture() {
    let db = SqliteDb::new(":memory:").expect("db open");

    db.add_lift("Squat", LiftRegion::LOWER, LiftType::Squat, &[], "")
        .unwrap();
    db.add_lift("Deadlift", LiftRegion::LOWER, LiftType::Deadlift, &[], "")
        .unwrap();
    db.add_lift(
        "Conventional Deadlift",
        LiftRegion::LOWER,
        LiftType::Deadlift,
        &[],
        "",
    )
    .unwrap();
    db.add_lift(
        "Bench Press",
        LiftRegion::UPPER,
        LiftType::BenchPress,
        &[],
        "",
    )
    .unwrap();
    db.add_lift(
        "Overhead Press",
        LiftRegion::UPPER,
        LiftType::OverheadPress,
        &[],
        "",
    )
    .unwrap();
    db.add_lift(
        "Sled Push",
        LiftRegion::LOWER,
        LiftType::Conditioning,
        &[],
        "",
    )
    .unwrap();
    db.add_lift(
        "Battle Rope",
        LiftRegion::UPPER,
        LiftType::Conditioning,
        &[],
        "",
    )
    .unwrap();
    db.add_lift(
        "Hip Airplane",
        LiftRegion::LOWER,
        LiftType::Mobility,
        &[],
        "",
    )
    .unwrap();
    db.add_lift(
        "Shoulder CARs",
        LiftRegion::UPPER,
        LiftType::Mobility,
        &[],
        "",
    )
    .unwrap();
    db.add_lift(
        "Hamstring Curl",
        LiftRegion::LOWER,
        LiftType::Accessory,
        &[Muscle::Hamstring],
        "",
    )
    .unwrap();
    db.add_lift(
        "Leg Extension",
        LiftRegion::LOWER,
        LiftType::Accessory,
        &[Muscle::Quad, Muscle::Calf],
        "",
    )
    .unwrap();
    db.add_lift(
        "Plank",
        LiftRegion::LOWER,
        LiftType::Accessory,
        &[Muscle::Core],
        "",
    )
    .unwrap();
    db.add_lift(
        "Upper Compound Accessory",
        LiftRegion::UPPER,
        LiftType::Accessory,
        &[Muscle::Lat, Muscle::Tricep, Muscle::Bicep],
        "",
    )
    .unwrap();
    db.add_lift(
        "Delt Giant Set",
        LiftRegion::UPPER,
        LiftType::Accessory,
        &[
            Muscle::RearDelt,
            Muscle::Shoulder,
            Muscle::FrontDelt,
            Muscle::Trap,
        ],
        "",
    )
    .unwrap();
    let wave = ConjugateWorkoutBuilder
        .get_wave_with_mode(1, &db, RandomMode::Deterministic)
        .expect("wave");
    let markdown = create_markdown(&wave, &db).join("\n");
    let expected = fs::read_to_string("../testdata/conjugate_wave_parity.md").expect("fixture");
    assert_eq!(expected.trim(), markdown.trim());
}
