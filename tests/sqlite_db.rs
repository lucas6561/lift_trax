#[path = "../src/database.rs"]
mod database;
#[path = "../src/models/mod.rs"]
mod models;
#[path = "../src/sqlite_db.rs"]
mod sqlite_db;
#[path = "../src/weight.rs"]
mod weight;

use chrono::NaiveDate;
use database::Database;
use models::{LiftExecution, LiftRegion, LiftType, Muscle};
use sqlite_db::SqliteDb;
use weight::Weight;

#[test]
fn add_and_list_lift_with_execution() {
    let db = SqliteDb::new(":memory:").expect("db open");
    db.add_lift(
        "Bench",
        LiftRegion::UPPER,
        Some(LiftType::BenchPress),
        &[Muscle::Chest],
        "barbell",
    )
    .unwrap();

    let lifts = db.list_lifts(None).unwrap();
    assert_eq!(lifts.len(), 1);
    assert_eq!(lifts[0].name, "Bench");
    assert_eq!(lifts[0].notes, "barbell");

    let exec = LiftExecution {
        id: None,
        date: NaiveDate::from_ymd_opt(2024, 6, 1).unwrap(),
        sets: 3,
        reps: 5,
        weight: Weight::Raw(135.0),
        rpe: Some(9.0),
    };
    db.add_lift_execution("Bench", &exec).unwrap();

    let bench = db.list_lifts(Some("Bench")).unwrap().pop().unwrap();
    assert_eq!(bench.executions.len(), 1);
    let stored = &bench.executions[0];
    assert_eq!(stored.sets, 3);
    assert_eq!(stored.weight.to_string(), "135 lb");
}

#[test]
fn lift_stats_provides_summary() {
    let db = SqliteDb::new(":memory:").expect("db open");
    db.add_lift("Squat", LiftRegion::LOWER, None, &[], "")
        .unwrap();

    let exec1 = LiftExecution {
        id: None,
        date: NaiveDate::from_ymd_opt(2024, 1, 1).unwrap(),
        sets: 3,
        reps: 5,
        weight: Weight::Raw(100.0),
        rpe: None,
    };
    let exec2 = LiftExecution {
        id: None,
        date: NaiveDate::from_ymd_opt(2024, 1, 2).unwrap(),
        sets: 3,
        reps: 5,
        weight: Weight::Raw(120.0),
        rpe: None,
    };
    let exec3 = LiftExecution {
        id: None,
        date: NaiveDate::from_ymd_opt(2024, 1, 3).unwrap(),
        sets: 2,
        reps: 3,
        weight: Weight::Raw(150.0),
        rpe: Some(9.0),
    };
    db.add_lift_execution("Squat", &exec1).unwrap();
    db.add_lift_execution("Squat", &exec2).unwrap();
    db.add_lift_execution("Squat", &exec3).unwrap();

    let stats = db.lift_stats("Squat").unwrap();
    let last = stats.last.unwrap();
    assert_eq!(last.reps, 3);
    assert_eq!(last.weight.to_string(), "150 lb");
    assert_eq!(stats.best_by_reps.get(&5).unwrap().to_string(), "120 lb");
    assert_eq!(stats.best_by_reps.get(&3).unwrap().to_string(), "150 lb");
}
