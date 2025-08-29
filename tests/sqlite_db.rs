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
use rusqlite::Connection;


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
        notes: "solid".into(),
    };
    db.add_lift_execution("Bench", &exec).unwrap();

    let bench = db.list_lifts(Some("Bench")).unwrap().pop().unwrap();
    assert_eq!(bench.executions.len(), 1);
    let stored = &bench.executions[0];
    assert_eq!(stored.sets, 3);
    assert_eq!(stored.weight.to_string(), "135 lb");
    assert_eq!(stored.notes, "solid");
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
        notes: String::new(),
    };
    let exec2 = LiftExecution {
        id: None,
        date: NaiveDate::from_ymd_opt(2024, 1, 2).unwrap(),
        sets: 3,
        reps: 5,
        weight: Weight::Raw(120.0),
        rpe: None,
        notes: String::new(),
    };
    let exec3 = LiftExecution {
        id: None,
        date: NaiveDate::from_ymd_opt(2024, 1, 3).unwrap(),
        sets: 2,
        reps: 3,
        weight: Weight::Raw(150.0),
        rpe: Some(9.0),
        notes: String::new(),
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

#[test]
fn upgrades_legacy_database() {
    let path = "test_legacy.db";
    // remove any leftover file from previous runs
    let _ = std::fs::remove_file(path);

    // Simulate a v4 database lacking notes columns
    {
        let conn = Connection::open(path).unwrap();
        conn.execute(
            "CREATE TABLE lifts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE,
                region TEXT NOT NULL,
                main_lift TEXT,
                muscles TEXT NOT NULL DEFAULT ''
            )",
            [],
        )
        .unwrap();
        conn.execute(
            "CREATE TABLE lift_records (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                lift_id INTEGER NOT NULL,
                date TEXT NOT NULL,
                sets INTEGER NOT NULL,
                reps INTEGER NOT NULL,
                weight TEXT NOT NULL,
                rpe REAL,
                FOREIGN KEY(lift_id) REFERENCES lifts(id)
            )",
            [],
        )
        .unwrap();
        conn.pragma_update(None, "user_version", &4).unwrap();
    }

    // Opening through SqliteDb should upgrade to the latest schema
    let _db = SqliteDb::new(path).expect("db open");

    let conn = Connection::open(path).unwrap();
    let user_version: i32 = conn
        .query_row("PRAGMA user_version", [], |row| row.get(0))
        .unwrap();
    assert_eq!(user_version, 6);

    let lift_cols: Vec<String> = conn
        .prepare("PRAGMA table_info(lifts)")
        .unwrap()
        .query_map([], |row| row.get(1))
        .unwrap()
        .collect::<Result<_, _>>()
        .unwrap();
    assert!(lift_cols.contains(&"notes".to_string()));

    let record_cols: Vec<String> = conn
        .prepare("PRAGMA table_info(lift_records)")
        .unwrap()
        .query_map([], |row| row.get(1))
        .unwrap()
        .collect::<Result<_, _>>()
        .unwrap();
    assert!(record_cols.contains(&"notes".to_string()));

    std::fs::remove_file(path).unwrap();
}
