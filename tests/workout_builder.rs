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
use workout_builder::{
    AccommodatingResistance, ConjugateWorkoutBuilder, WorkoutBuilder, WorkoutLift,
};

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

    let mut de_squat_name = String::new();
    let mut de_dead_name = String::new();
    let mut de_bench_name = String::new();
    let mut de_ohp_name = String::new();
    let mut de_squat_ar = AccommodatingResistance::None;
    let mut de_dead_ar = AccommodatingResistance::None;
    let mut de_bench_ar = AccommodatingResistance::None;
    let mut de_ohp_ar = AccommodatingResistance::None;

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

        let thu = week.get(&Weekday::Thu).expect("thursday");
        assert_eq!(thu.lifts.len(), 2);
        match &thu.lifts[0] {
            WorkoutLift::Single(s) => {
                assert_eq!(s.lift.main, Some(LiftType::Squat));
                assert_eq!(s.percent, Some(50 + i as u32 * 5));
                let ar = s.accommodating_resistance.clone().expect("ar");
                if i == 0 {
                    de_squat_name = s.lift.name.clone();
                    de_squat_ar = ar;
                } else {
                    assert_eq!(s.lift.name, de_squat_name);
                    assert_eq!(ar, de_squat_ar);
                }
            }
            _ => panic!("expected single"),
        }
        match &thu.lifts[1] {
            WorkoutLift::Single(s) => {
                assert_eq!(s.lift.main, Some(LiftType::Deadlift));
                assert_eq!(s.percent, Some(50 + i as u32 * 5));
                let ar = s.accommodating_resistance.clone().expect("ar");
                if i == 0 {
                    de_dead_name = s.lift.name.clone();
                    de_dead_ar = ar;
                } else {
                    assert_eq!(s.lift.name, de_dead_name);
                    assert_eq!(ar, de_dead_ar);
                }
            }
            _ => panic!("expected single"),
        }

        let fri = week.get(&Weekday::Fri).expect("friday");
        assert_eq!(fri.lifts.len(), 2);
        match &fri.lifts[0] {
            WorkoutLift::Single(s) => {
                assert_eq!(s.lift.main, Some(LiftType::BenchPress));
                assert_eq!(s.percent, Some(50 + i as u32 * 5));
                let ar = s.accommodating_resistance.clone().expect("ar");
                if i == 0 {
                    de_bench_name = s.lift.name.clone();
                    de_bench_ar = ar;
                } else {
                    assert_eq!(s.lift.name, de_bench_name);
                    assert_eq!(ar, de_bench_ar);
                }
            }
            _ => panic!("expected single"),
        }
        match &fri.lifts[1] {
            WorkoutLift::Single(s) => {
                assert_eq!(s.lift.main, Some(LiftType::OverheadPress));
                assert_eq!(s.percent, Some(50 + i as u32 * 5));
                let ar = s.accommodating_resistance.clone().expect("ar");
                if i == 0 {
                    de_ohp_name = s.lift.name.clone();
                    de_ohp_ar = ar;
                } else {
                    assert_eq!(s.lift.name, de_ohp_name);
                    assert_eq!(ar, de_ohp_ar);
                }
            }
            _ => panic!("expected single"),
        }
    }
}
