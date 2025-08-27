#[path = "../src/models/mod.rs"]
mod models;
#[path = "../src/sqlite_db.rs"]
mod sqlite_db;
#[path = "../src/database.rs"]
mod database;
#[path = "../src/weight.rs"]
mod weight;

use sqlite_db::SqliteDb;
use database::Database;
use models::{LiftRegion, LiftType, Muscle, LiftExecution};
use weight::Weight;
use chrono::NaiveDate;

#[test]
fn add_and_list_lift_with_execution() {
    let db = SqliteDb::new(":memory:").expect("db open");
    db.add_lift("Bench", LiftRegion::UPPER, Some(LiftType::BenchPress), &[Muscle::Chest])
        .unwrap();

    let lifts = db.list_lifts(None).unwrap();
    assert_eq!(lifts.len(), 1);
    assert_eq!(lifts[0].name, "Bench");

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
