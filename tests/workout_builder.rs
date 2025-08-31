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
    // add squat lifts
    db.add_lift("Back Squat", LiftRegion::LOWER, LiftType::Squat, &[], "")
        .unwrap();
    db.add_lift("Front Squat", LiftRegion::LOWER, LiftType::Squat, &[], "")
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
    for week in &wave {
        let mon = week.get(&Weekday::Mon).expect("monday");
        assert!(matches!(mon.lifts[0], WorkoutLift::Single(_)));

        let tue = week.get(&Weekday::Tue).expect("tuesday");
        match &tue.lifts[0] {
            WorkoutLift::Circuit(c) => assert_eq!(c.circuit_lifts.len(), 3),
            _ => panic!("expected circuit"),
        }

        let thu = week.get(&Weekday::Thu).expect("thursday");
        match &thu.lifts[0] {
            WorkoutLift::Circuit(c) => assert_eq!(c.circuit_lifts.len(), 3),
            _ => panic!("expected circuit"),
        }
    }
}
