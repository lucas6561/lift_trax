#[path = "../src/database.rs"]
mod database;
#[path = "../src/models/mod.rs"]
mod models;
#[path = "../src/random_stack.rs"]
pub mod random_stack;
#[path = "../src/sqlite_db.rs"]
mod sqlite_db;
#[path = "../src/wave_view.rs"]
mod wave_view;
#[path = "../src/weight.rs"]
mod weight;
#[path = "../src/workout_builder/mod.rs"]
mod workout_builder;

use chrono::NaiveDate;
use database::Database;
use models::{ExecutionSet, LiftExecution, LiftRegion, LiftType, SetMetric};
use sqlite_db::SqliteDb;
use weight::Weight;
use workout_builder::{SingleLift, Workout, WorkoutLift, WorkoutLiftKind};

#[test]
fn wave_shows_last_one_rep_max() {
    let db = SqliteDb::new(":memory:").expect("db open");
    db.add_lift("Bench", LiftRegion::UPPER, LiftType::BenchPress, &[], "")
        .unwrap();

    let one_rm = LiftExecution {
        id: None,
        date: NaiveDate::from_ymd_opt(2024, 5, 1).unwrap(),
        sets: vec![ExecutionSet {
            metric: SetMetric::Reps(1),
            weight: Weight::Raw(220.0),
            rpe: None,
        }],
        warmup: false,
        deload: false,
        notes: String::new(),
    };
    db.add_lift_execution("Bench", &one_rm).unwrap();

    let recent = LiftExecution {
        id: None,
        date: NaiveDate::from_ymd_opt(2024, 6, 1).unwrap(),
        sets: vec![ExecutionSet {
            metric: SetMetric::Reps(5),
            weight: Weight::Raw(150.0),
            rpe: None,
        }],
        warmup: false,
        deload: false,
        notes: String::new(),
    };
    db.add_lift_execution("Bench", &recent).unwrap();

    let lift = db.get_lift("Bench").unwrap();
    let sl = SingleLift {
        lift: lift.clone(),
        metric: Some(SetMetric::Reps(5)),
        percent: None,
        rpe: None,
        accommodating_resistance: None,
        deload: false,
    };
    let wl = WorkoutLift {
        name: lift.name.clone(),
        kind: WorkoutLiftKind::Single(sl),
    };
    let workout = Workout { lifts: vec![wl] };
    let lines = wave_view::workout_lines(&workout, &db);
    assert!(
        lines.iter().any(|l| l.contains("Last 1RM: 220 lb")),
        "lines did not contain last 1RM: {:?}",
        lines
    );
}

#[test]
fn wave_includes_lift_and_exec_notes() {
    let db = SqliteDb::new(":memory:").expect("db open");
    db.add_lift(
        "Bench",
        LiftRegion::UPPER,
        LiftType::BenchPress,
        &[],
        "bench notes",
    )
    .unwrap();

    let exec = LiftExecution {
        id: None,
        date: NaiveDate::from_ymd_opt(2024, 6, 1).unwrap(),
        sets: vec![ExecutionSet {
            metric: SetMetric::Reps(5),
            weight: Weight::Raw(150.0),
            rpe: None,
        }],
        warmup: false,
        deload: false,
        notes: "felt strong".into(),
    };
    db.add_lift_execution("Bench", &exec).unwrap();

    let lift = db.get_lift("Bench").unwrap();
    let sl = SingleLift {
        lift: lift.clone(),
        metric: Some(SetMetric::Reps(5)),
        percent: None,
        rpe: None,
        accommodating_resistance: None,
        deload: false,
    };
    let wl = WorkoutLift {
        name: lift.name.clone(),
        kind: WorkoutLiftKind::Single(sl),
    };
    let workout = Workout { lifts: vec![wl] };
    let lines = wave_view::workout_lines(&workout, &db);
    assert!(
        lines.iter().any(|l| l.contains("Notes: bench notes")),
        "lines did not contain lift notes: {:?}",
        lines
    );
    assert!(
        lines.iter().any(|l| l.contains("felt strong")),
        "lines did not contain execution notes: {:?}",
        lines
    );
}

#[test]
fn wave_skips_deload_history_for_normal_weeks() {
    let db = SqliteDb::new(":memory:").expect("db open");
    db.add_lift("Bench", LiftRegion::UPPER, LiftType::BenchPress, &[], "")
        .unwrap();

    let normal_exec = LiftExecution {
        id: None,
        date: NaiveDate::from_ymd_opt(2024, 6, 1).unwrap(),
        sets: vec![ExecutionSet {
            metric: SetMetric::Reps(5),
            weight: Weight::Raw(150.0),
            rpe: None,
        }],
        warmup: false,
        deload: false,
        notes: String::new(),
    };
    db.add_lift_execution("Bench", &normal_exec).unwrap();

    let deload_exec = LiftExecution {
        id: None,
        date: NaiveDate::from_ymd_opt(2024, 6, 8).unwrap(),
        sets: vec![ExecutionSet {
            metric: SetMetric::Reps(3),
            weight: Weight::Raw(95.0),
            rpe: None,
        }],
        warmup: false,
        deload: true,
        notes: String::new(),
    };
    db.add_lift_execution("Bench", &deload_exec).unwrap();

    let lift = db.get_lift("Bench").unwrap();
    let sl = SingleLift {
        lift: lift.clone(),
        metric: Some(SetMetric::Reps(5)),
        percent: None,
        rpe: None,
        accommodating_resistance: None,
        deload: false,
    };
    let wl = WorkoutLift {
        name: lift.name.clone(),
        kind: WorkoutLiftKind::Single(sl),
    };
    let workout = Workout { lifts: vec![wl] };
    let lines = wave_view::workout_lines(&workout, &db);
    let last_line = lines
        .iter()
        .find(|l| l.contains("- Last:"))
        .expect("expected last line");
    assert!(last_line.contains("5 reps"), "unexpected last line: {last_line}");
    assert!(
        !last_line.contains("deload"),
        "deload history should be hidden outside deload weeks: {last_line}"
    );
}

#[test]
fn wave_includes_deload_history_during_deload_weeks() {
    let db = SqliteDb::new(":memory:").expect("db open");
    db.add_lift("Bench", LiftRegion::UPPER, LiftType::BenchPress, &[], "")
        .unwrap();

    let normal_exec = LiftExecution {
        id: None,
        date: NaiveDate::from_ymd_opt(2024, 6, 1).unwrap(),
        sets: vec![ExecutionSet {
            metric: SetMetric::Reps(5),
            weight: Weight::Raw(150.0),
            rpe: None,
        }],
        warmup: false,
        deload: false,
        notes: String::new(),
    };
    db.add_lift_execution("Bench", &normal_exec).unwrap();

    let deload_exec = LiftExecution {
        id: None,
        date: NaiveDate::from_ymd_opt(2024, 6, 8).unwrap(),
        sets: vec![ExecutionSet {
            metric: SetMetric::Reps(3),
            weight: Weight::Raw(95.0),
            rpe: None,
        }],
        warmup: false,
        deload: true,
        notes: String::new(),
    };
    db.add_lift_execution("Bench", &deload_exec).unwrap();

    let lift = db.get_lift("Bench").unwrap();
    let sl = SingleLift {
        lift: lift.clone(),
        metric: Some(SetMetric::Reps(3)),
        percent: None,
        rpe: None,
        accommodating_resistance: None,
        deload: true,
    };
    let wl = WorkoutLift {
        name: lift.name.clone(),
        kind: WorkoutLiftKind::Single(sl),
    };
    let workout = Workout { lifts: vec![wl] };
    let lines = wave_view::workout_lines(&workout, &db);
    let last_line = lines
        .iter()
        .find(|l| l.contains("- Last:"))
        .expect("expected last line");
    assert!(last_line.contains("3 reps"), "unexpected last line: {last_line}");
    assert!(
        last_line.contains("deload"),
        "deload history should be shown during deload weeks: {last_line}"
    );
}
