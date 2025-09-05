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
use models::{ExecutionSet, LiftExecution, LiftRegion, LiftType, Muscle, SetMetric};
use rusqlite::Connection;
use serde::Serialize;
use sqlite_db::SqliteDb;
use weight::Weight;

#[test]
fn add_and_list_lift_with_execution() {
    let db = SqliteDb::new(":memory:").expect("db open");
    db.add_lift(
        "Bench",
        LiftRegion::UPPER,
        LiftType::BenchPress,
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
        sets: vec![
            ExecutionSet {
                metric: SetMetric::Reps(5),
                weight: Weight::Raw(135.0),
                rpe: Some(9.0),
            };
            3
        ],
        warmup: false,
        notes: "solid".into(),
    };
    db.add_lift_execution("Bench", &exec).unwrap();

    let bench = db.list_lifts(Some("Bench")).unwrap().pop().unwrap();
    assert_eq!(bench.executions.len(), 1);
    let stored = &bench.executions[0];
    assert_eq!(stored.sets.len(), 3);
    assert_eq!(stored.sets[0].weight.to_string(), "135 lb");
    assert_eq!(stored.notes, "solid");
}

#[test]
fn delete_execution_removes_record() {
    let db = SqliteDb::new(":memory:").expect("db open");
    db.add_lift("Bench", LiftRegion::UPPER, LiftType::Accessory, &[], "")
        .unwrap();

    let exec = LiftExecution {
        id: None,
        date: NaiveDate::from_ymd_opt(2024, 6, 1).unwrap(),
        sets: vec![ExecutionSet {
            metric: SetMetric::Reps(5),
            weight: Weight::Raw(135.0),
            rpe: None,
        }],
        warmup: false,
        notes: String::new(),
    };
    db.add_lift_execution("Bench", &exec).unwrap();

    let bench = db.list_lifts(Some("Bench")).unwrap().pop().unwrap();
    let exec_id = bench.executions[0].id.unwrap();
    db.delete_lift_execution(exec_id).unwrap();

    let bench = db.list_lifts(Some("Bench")).unwrap().pop().unwrap();
    assert!(bench.executions.is_empty());
}

#[test]
fn delete_lift_removes_lift_and_history() {
    let db = SqliteDb::new(":memory:").expect("db open");
    db.add_lift("Bench", LiftRegion::UPPER, LiftType::Accessory, &[], "")
        .unwrap();

    let exec = LiftExecution {
        id: None,
        date: NaiveDate::from_ymd_opt(2024, 6, 1).unwrap(),
        sets: vec![ExecutionSet {
            metric: SetMetric::Reps(5),
            weight: Weight::Raw(135.0),
            rpe: None,
        }],
        warmup: false,
        notes: String::new(),
    };
    db.add_lift_execution("Bench", &exec).unwrap();

    db.delete_lift("Bench").unwrap();
    assert!(db.list_lifts(None).unwrap().is_empty());
}

#[test]
fn list_lifts_returns_all_sorted() {
    let db = SqliteDb::new(":memory:").expect("db open");
    db.add_lift("Zpress", LiftRegion::UPPER, LiftType::Accessory, &[], "")
        .unwrap();
    db.add_lift("Bench", LiftRegion::UPPER, LiftType::Accessory, &[], "")
        .unwrap();
    db.add_lift("Curl", LiftRegion::UPPER, LiftType::Accessory, &[], "")
        .unwrap();
    let names: Vec<_> = db
        .list_lifts(None)
        .unwrap()
        .into_iter()
        .map(|l| l.name)
        .collect();
    assert_eq!(names, vec!["Bench", "Curl", "Zpress"]);
}

#[test]
fn lifts_by_type_filters_main_lifts() {
    let db = SqliteDb::new(":memory:").expect("db open");
    db.add_lift(
        "Back Squat",
        LiftRegion::LOWER,
        LiftType::Squat,
        &[],
        "",
    )
    .unwrap();
    db.add_lift(
        "Bench",
        LiftRegion::UPPER,
        LiftType::BenchPress,
        &[],
        "",
    )
    .unwrap();
    let lifts = db.lifts_by_type(LiftType::Squat).unwrap();
    assert_eq!(lifts.len(), 1);
    assert_eq!(lifts[0].name, "Back Squat");
}

#[test]
fn lifts_by_region_and_type_filters() {
    let db = SqliteDb::new(":memory:").expect("db open");
    db.add_lift(
        "Curl",
        LiftRegion::UPPER,
        LiftType::Accessory,
        &[],
        "",
    )
    .unwrap();
    db.add_lift(
        "Squat",
        LiftRegion::LOWER,
        LiftType::Accessory,
        &[],
        "",
    )
    .unwrap();
    db.add_lift(
        "Bench",
        LiftRegion::UPPER,
        LiftType::BenchPress,
        &[],
        "",
    )
    .unwrap();
    let lifts = db
        .lifts_by_region_and_type(LiftRegion::UPPER, LiftType::Accessory)
        .unwrap();
    assert_eq!(lifts.len(), 1);
    assert_eq!(lifts[0].name, "Curl");
}

#[test]
fn reads_legacy_execution_sets() {
    let path = "test_old_sets.db";
    let _ = std::fs::remove_file(path);
    let db = SqliteDb::new(path).expect("db open");
    db.add_lift("Row", LiftRegion::UPPER, LiftType::Accessory, &[], "")
        .unwrap();

    #[derive(Serialize)]
    struct LegacySet {
        reps: i32,
        weight: Weight,
        rpe: Option<f32>,
    }

    let conn = Connection::open(path).unwrap();
    let lift_id: i32 = conn
        .query_row("SELECT id FROM lifts WHERE name = 'Row'", [], |row| {
            row.get(0)
        })
        .unwrap();
    let legacy_sets = vec![LegacySet {
        reps: 10,
        weight: Weight::Raw(50.0),
        rpe: None,
    }];
    let sets_json = serde_json::to_string(&legacy_sets).unwrap();
    conn.execute(
        "INSERT INTO lift_records (lift_id, date, sets, notes) VALUES (?1, '2024-01-01', ?2, '')",
        rusqlite::params![lift_id, sets_json],
    )
    .unwrap();
    drop(conn);

    let lift = db.list_lifts(Some("Row")).unwrap().pop().unwrap();
    assert_eq!(lift.executions.len(), 1);
    let set = &lift.executions[0].sets[0];
    match set.metric {
        SetMetric::Reps(r) => assert_eq!(r, 10),
        _ => panic!("expected reps"),
    }
    assert_eq!(set.weight.to_string(), "50 lb");
    // drop database connection before removing file on Windows
    drop(db);
    std::fs::remove_file(path).unwrap();
}

#[test]
fn lift_stats_handles_empty_history() {
    let db = SqliteDb::new(":memory:").expect("db open");
    db.add_lift("Bench", LiftRegion::UPPER, LiftType::Accessory, &[], "")
        .unwrap();
    let stats = db.lift_stats("Bench").unwrap();
    assert!(stats.last.is_none());
    assert!(stats.best_by_reps.is_empty());
}

#[test]
fn lift_stats_provides_summary() {
    let db = SqliteDb::new(":memory:").expect("db open");
    db.add_lift("Squat", LiftRegion::LOWER, LiftType::Accessory, &[], "")
        .unwrap();

    let exec1 = LiftExecution {
        id: None,
        date: NaiveDate::from_ymd_opt(2024, 1, 1).unwrap(),
        sets: vec![
            ExecutionSet {
                metric: SetMetric::Reps(5),
                weight: Weight::Raw(100.0),
                rpe: None,
            };
            3
        ],
        warmup: false,
        notes: String::new(),
    };
    let exec2 = LiftExecution {
        id: None,
        date: NaiveDate::from_ymd_opt(2024, 1, 2).unwrap(),
        sets: vec![
            ExecutionSet {
                metric: SetMetric::Reps(5),
                weight: Weight::Raw(120.0),
                rpe: None,
            };
            3
        ],
        warmup: false,
        notes: String::new(),
    };
    let exec3 = LiftExecution {
        id: None,
        date: NaiveDate::from_ymd_opt(2024, 1, 3).unwrap(),
        sets: vec![
            ExecutionSet {
                metric: SetMetric::Reps(3),
                weight: Weight::Raw(150.0),
                rpe: Some(9.0),
            };
            2
        ],
        warmup: false,
        notes: String::new(),
    };
    db.add_lift_execution("Squat", &exec1).unwrap();
    db.add_lift_execution("Squat", &exec2).unwrap();
    db.add_lift_execution("Squat", &exec3).unwrap();

    let stats = db.lift_stats("Squat").unwrap();
    let last = stats.last.unwrap();
    match last.sets[0].metric {
        SetMetric::Reps(r) => assert_eq!(r, 3),
        _ => panic!("expected reps"),
    }
    assert_eq!(last.sets[0].weight.to_string(), "150 lb");
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
    {
        let _db = SqliteDb::new(path).expect("db open");
        // dropped here
    }

    let conn = Connection::open(path).unwrap();
    let user_version: i32 = conn
        .query_row("PRAGMA user_version", [], |row| row.get(0))
        .unwrap();
    assert_eq!(user_version, 8);

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

    // close connection before removing file
    drop(conn);
    std::fs::remove_file(path).unwrap();
    // remove generated backup
    for entry in std::fs::read_dir(".").unwrap() {
        let entry = entry.unwrap();
        let name = entry.file_name().to_string_lossy().into_owned();
        if name.starts_with("test_legacy.db.backup-") {
            let _ = std::fs::remove_file(entry.path());
        }
    }
}

#[test]
fn upgrades_unversioned_database() {
    let path = "test_unversioned.db";
    let _ = std::fs::remove_file(path);

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
        // leave user_version at default 0
    }

    {
        let _db = SqliteDb::new(path).expect("db open");
        // dropped here
    }

    let conn = Connection::open(path).unwrap();
    let user_version: i32 = conn
        .query_row("PRAGMA user_version", [], |row| row.get(0))
        .unwrap();
    assert_eq!(user_version, 8);

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

    // close connection before removing file
    drop(conn);
    std::fs::remove_file(path).unwrap();
    for entry in std::fs::read_dir(".").unwrap() {
        let entry = entry.unwrap();
        let name = entry.file_name().to_string_lossy().into_owned();
        if name.starts_with("test_unversioned.db.backup-") {
            let _ = std::fs::remove_file(entry.path());
        }
    }
}

#[test]
fn repairs_misreported_version() {
    let path = "test_misreported.db";
    let _ = std::fs::remove_file(path);

    // Create a legacy v4 schema but claim to be the latest version.
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
        // Incorrectly set user_version ahead of the actual schema.
        conn.pragma_update(None, "user_version", &6).unwrap();
    }

    // Opening should reconcile the mismatch and add missing columns.
    {
        let _db = SqliteDb::new(path).expect("db open");
        // dropped here
    }

    let conn = Connection::open(path).unwrap();
    let cols: Vec<String> = conn
        .prepare("PRAGMA table_info(lift_records)")
        .unwrap()
        .query_map([], |row| row.get(1))
        .unwrap()
        .collect::<Result<_, _>>()
        .unwrap();
    assert!(cols.contains(&"notes".to_string()));

    let user_version: i32 = conn
        .query_row("PRAGMA user_version", [], |row| row.get(0))
        .unwrap();
    assert_eq!(user_version, 8);

    // close connection before removing file
    drop(conn);
    std::fs::remove_file(path).unwrap();
    for entry in std::fs::read_dir(".").unwrap() {
        let entry = entry.unwrap();
        let name = entry.file_name().to_string_lossy().into_owned();
        if name.starts_with("test_misreported.db.backup-") {
            let _ = std::fs::remove_file(entry.path());
        }
    }
}

#[test]
fn creates_backup_on_open() {
    let path = "backup_test.db";
    // ensure clean state
    let _ = std::fs::remove_file(path);
    for entry in std::fs::read_dir(".").unwrap() {
        let entry = entry.unwrap();
        let name = entry.file_name().to_string_lossy().into_owned();
        if name.starts_with("backup_test.db.backup-") {
            let _ = std::fs::remove_file(entry.path());
        }
    }

    {
        // create initial database
        let _db = SqliteDb::new(path).expect("db open");
    }
    // reopening should create a backup of existing file
    {
        let _db = SqliteDb::new(path).expect("db open");
    }

    let backups: Vec<_> = std::fs::read_dir(".")
        .unwrap()
        .filter_map(|e| {
            let name = e.unwrap().file_name().to_string_lossy().into_owned();
            if name.starts_with("backup_test.db.backup-") {
                Some(name)
            } else {
                None
            }
        })
        .collect();
    assert!(!backups.is_empty());

    // clean up
    std::fs::remove_file(path).unwrap();
    for name in backups {
        let _ = std::fs::remove_file(name);
    }
}
