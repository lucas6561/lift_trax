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
fn alternates_main_lifts_across_weeks() {
    let db = SqliteDb::new(":memory:").expect("db open");

    // add squat and deadlift lifts
    db.add_lift("Back Squat", LiftRegion::LOWER, LiftType::Squat, &[], "")
        .unwrap();
    db.add_lift("Front Squat", LiftRegion::LOWER, LiftType::Squat, &[], "")
        .unwrap();
    db.add_lift("Box Squat", LiftRegion::LOWER, LiftType::Squat, &[], "")
        .unwrap();
    db.add_lift("Conventional Deadlift", LiftRegion::LOWER, LiftType::Deadlift, &[], "")
        .unwrap();
    db.add_lift("Sumo Deadlift", LiftRegion::LOWER, LiftType::Deadlift, &[], "")
        .unwrap();
    db.add_lift("Deficit Deadlift", LiftRegion::LOWER, LiftType::Deadlift, &[], "")
        .unwrap();

    // add bench and overhead press lifts
    db.add_lift("Bench Press", LiftRegion::UPPER, LiftType::BenchPress, &[], "")
        .unwrap();
    db.add_lift("Close-Grip Bench Press", LiftRegion::UPPER, LiftType::BenchPress, &[], "")
        .unwrap();
    db.add_lift("Floor Press", LiftRegion::UPPER, LiftType::BenchPress, &[], "")
        .unwrap();
    db.add_lift("Overhead Press", LiftRegion::UPPER, LiftType::OverheadPress, &[], "")
        .unwrap();
    db.add_lift("Push Press", LiftRegion::UPPER, LiftType::OverheadPress, &[], "")
        .unwrap();
    db.add_lift("Seated Overhead Press", LiftRegion::UPPER, LiftType::OverheadPress, &[], "")
        .unwrap();

    let builder = ConjugateWorkoutBuilder;
    let wave = builder.get_wave(6, &db).unwrap();
    assert_eq!(wave.len(), 6);

    let expected_lower = [
        LiftType::Squat,
        LiftType::Deadlift,
        LiftType::Squat,
        LiftType::Deadlift,
        LiftType::Squat,
        LiftType::Deadlift,
    ];
    let expected_upper = [
        LiftType::BenchPress,
        LiftType::OverheadPress,
        LiftType::BenchPress,
        LiftType::OverheadPress,
        LiftType::BenchPress,
        LiftType::OverheadPress,
    ];

    for (i, week) in wave.iter().enumerate() {
        let mon = week.get(&Weekday::Mon).expect("monday");
        match &mon.lifts[0] {
            WorkoutLift::Single(s) => assert_eq!(s.lift.main, Some(expected_lower[i])),
            _ => panic!("expected single"),
        }
        assert_eq!(mon.lifts.len(), 1);

        let tue = week.get(&Weekday::Tue).expect("tuesday");
        match &tue.lifts[0] {
            WorkoutLift::Single(s) => assert_eq!(s.lift.main, Some(expected_upper[i])),
            _ => panic!("expected single"),
        }
        assert_eq!(tue.lifts.len(), 1);

        assert!(week.get(&Weekday::Thu).is_none());
    }
}
