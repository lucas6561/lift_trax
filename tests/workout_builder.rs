#[path = "../src/database.rs"]
mod database;
#[path = "../src/models/mod.rs"]
mod models;
#[path = "../src/sqlite_db.rs"]
mod sqlite_db;
#[path = "../src/weight.rs"]
mod weight;
#[path = "../src/workout_builder/mod.rs"]
mod workout_builder;

use chrono::Weekday;
use database::Database;
use models::{LiftRegion, LiftType};
use sqlite_db::SqliteDb;
use workout_builder::{ConjugateWorkoutBuilder, WorkoutBuilder, WorkoutLift};

#[test]
fn builds_wave_with_expected_structure() {
    let db = SqliteDb::new(":memory:").expect("db open");
    // add squat and deadlift lifts
    db.add_lift("Back Squat", LiftRegion::LOWER, LiftType::Squat, &[], "")
        .unwrap();
    db.add_lift("Conventional Deadlift", LiftRegion::LOWER, LiftType::Deadlift, &[], "")
        .unwrap();
    // add bench and overhead press lifts
    db.add_lift("Bench Press", LiftRegion::UPPER, LiftType::BenchPress, &[], "")
        .unwrap();
    db.add_lift("Overhead Press", LiftRegion::UPPER, LiftType::OverheadPress, &[], "")
        .unwrap();
    // add upper accessory lifts
    db.add_lift("Curl", LiftRegion::UPPER, LiftType::Accessory, &[], "")
        .unwrap();
    db.add_lift("Row", LiftRegion::UPPER, LiftType::Accessory, &[], "")
        .unwrap();
    db.add_lift("Fly", LiftRegion::UPPER, LiftType::Accessory, &[], "")
        .unwrap();

    let builder = ConjugateWorkoutBuilder;
    let wave = builder.get_wave(2, &db).unwrap();
    assert_eq!(wave.len(), 2);

    // Week 1 should be squat and bench
    let week1 = &wave[0];
    let mon = week1.get(&Weekday::Mon).expect("monday");
    match &mon.lifts[0] {
        WorkoutLift::Single(s) => assert_eq!(s.lift.main, Some(LiftType::Squat)),
        _ => panic!("expected single"),
    }
    let tue = week1.get(&Weekday::Tue).expect("tuesday");
    match &tue.lifts[0] {
        WorkoutLift::Single(s) => assert_eq!(s.lift.main, Some(LiftType::BenchPress)),
        _ => panic!("expected single"),
    }
    match &tue.lifts[1] {
        WorkoutLift::Circuit(c) => assert_eq!(c.circuit_lifts.len(), 3),
        _ => panic!("expected circuit"),
    }
    let thu = week1.get(&Weekday::Thu).expect("thursday");
    match &thu.lifts[0] {
        WorkoutLift::Circuit(c) => assert_eq!(c.circuit_lifts.len(), 3),
        _ => panic!("expected circuit"),
    }

    // Week 2 should be deadlift and overhead press
    let week2 = &wave[1];
    let mon = week2.get(&Weekday::Mon).expect("monday");
    match &mon.lifts[0] {
        WorkoutLift::Single(s) => assert_eq!(s.lift.main, Some(LiftType::Deadlift)),
        _ => panic!("expected single"),
    }
    let tue = week2.get(&Weekday::Tue).expect("tuesday");
    match &tue.lifts[0] {
        WorkoutLift::Single(s) => assert_eq!(s.lift.main, Some(LiftType::OverheadPress)),
        _ => panic!("expected single"),
    }
    match &tue.lifts[1] {
        WorkoutLift::Circuit(c) => assert_eq!(c.circuit_lifts.len(), 3),
        _ => panic!("expected circuit"),
    }
    let thu = week2.get(&Weekday::Thu).expect("thursday");
    match &thu.lifts[0] {
        WorkoutLift::Circuit(c) => assert_eq!(c.circuit_lifts.len(), 3),
        _ => panic!("expected circuit"),
    }
}
