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
    db.add_lift("Squat", LiftRegion::LOWER, LiftType::Squat, &[], "")
        .unwrap();
    db.add_lift("Back Squat", LiftRegion::LOWER, LiftType::Squat, &[], "")
        .unwrap();
    db.add_lift("Front Squat", LiftRegion::LOWER, LiftType::Squat, &[], "")
        .unwrap();
    db.add_lift("Box Squat", LiftRegion::LOWER, LiftType::Squat, &[], "")
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
        "Sumo Deadlift",
        LiftRegion::LOWER,
        LiftType::Deadlift,
        &[],
        "",
    )
    .unwrap();
    db.add_lift(
        "Deficit Deadlift",
        LiftRegion::LOWER,
        LiftType::Deadlift,
        &[],
        "",
    )
    .unwrap();

    // add bench and overhead press lifts
    db.add_lift(
        "Bench Press",
        LiftRegion::UPPER,
        LiftType::BenchPress,
        &[],
        "",
    )
    .unwrap();
    db.add_lift(
        "Close-Grip Bench Press",
        LiftRegion::UPPER,
        LiftType::BenchPress,
        &[],
        "",
    )
    .unwrap();
    db.add_lift(
        "Floor Press",
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
        "Push Press",
        LiftRegion::UPPER,
        LiftType::OverheadPress,
        &[],
        "",
    )
    .unwrap();
    db.add_lift(
        "Seated Overhead Press",
        LiftRegion::UPPER,
        LiftType::OverheadPress,
        &[],
        "",
    )
    .unwrap();

    // warmup lifts
    db.add_lift(
        "Jump Rope",
        LiftRegion::LOWER,
        LiftType::Conditioning,
        &[],
        "",
    )
    .unwrap();
    db.add_lift(
        "Upper Mobility",
        LiftRegion::UPPER,
        LiftType::Mobility,
        &[],
        "",
    )
    .unwrap();
    db.add_lift(
        "Lower Mobility",
        LiftRegion::LOWER,
        LiftType::Mobility,
        &[],
        "",
    )
    .unwrap();
    db.add_lift(
        "Upper Accessory 1",
        LiftRegion::UPPER,
        LiftType::Accessory,
        &[],
        "",
    )
    .unwrap();
    db.add_lift(
        "Upper Accessory 2",
        LiftRegion::UPPER,
        LiftType::Accessory,
        &[],
        "",
    )
    .unwrap();
    db.add_lift(
        "Lower Accessory 1",
        LiftRegion::LOWER,
        LiftType::Accessory,
        &[],
        "",
    )
    .unwrap();
    db.add_lift(
        "Lower Accessory 2",
        LiftRegion::LOWER,
        LiftType::Accessory,
        &[],
        "",
    )
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

    let mut de_squat_ar = AccommodatingResistance::None;
    let mut de_dead_ar = AccommodatingResistance::None;
    let mut de_bench_ar = AccommodatingResistance::None;
    let mut de_ohp_ar = AccommodatingResistance::None;

    for (i, week) in wave.iter().enumerate() {
        let mon = week.get(&Weekday::Mon).expect("monday");
        assert_eq!(mon.lifts.len(), 2);
        match &mon.lifts[0] {
            WorkoutLift::Circuit(c) => {
                assert_eq!(c.circuit_lifts.len(), 4);
                let lifts = &c.circuit_lifts;
                assert_eq!(lifts[0].lift.main, Some(LiftType::Conditioning));
                assert_eq!(lifts[1].lift.main, Some(LiftType::Mobility));
                assert_eq!(lifts[2].lift.main, Some(LiftType::Accessory));
                assert_eq!(lifts[3].lift.main, Some(LiftType::Accessory));
                assert_eq!(lifts[1].lift.region, LiftRegion::LOWER);
                assert_eq!(lifts[2].lift.region, LiftRegion::LOWER);
                assert_eq!(lifts[3].lift.region, LiftRegion::LOWER);
            }
            _ => panic!("expected circuit"),
        }
        match &mon.lifts[1] {
            WorkoutLift::Single(s) => assert_eq!(s.lift.main, Some(expected_lower[i])),
            _ => panic!("expected single"),
        }

        let tue = week.get(&Weekday::Tue).expect("tuesday");
        assert_eq!(tue.lifts.len(), 2);
        match &tue.lifts[0] {
            WorkoutLift::Circuit(c) => {
                assert_eq!(c.circuit_lifts.len(), 4);
                let lifts = &c.circuit_lifts;
                assert_eq!(lifts[0].lift.main, Some(LiftType::Conditioning));
                assert_eq!(lifts[1].lift.main, Some(LiftType::Mobility));
                assert_eq!(lifts[2].lift.main, Some(LiftType::Accessory));
                assert_eq!(lifts[3].lift.main, Some(LiftType::Accessory));
                assert_eq!(lifts[1].lift.region, LiftRegion::UPPER);
                assert_eq!(lifts[2].lift.region, LiftRegion::UPPER);
                assert_eq!(lifts[3].lift.region, LiftRegion::UPPER);
            }
            _ => panic!("expected circuit"),
        }
        match &tue.lifts[1] {
            WorkoutLift::Single(s) => assert_eq!(s.lift.main, Some(expected_upper[i])),
            _ => panic!("expected single"),
        }

        let thu = week.get(&Weekday::Thu).expect("thursday");
        assert_eq!(thu.lifts.len(), 3);
        match &thu.lifts[0] {
            WorkoutLift::Circuit(c) => {
                assert_eq!(c.circuit_lifts.len(), 4);
                let lifts = &c.circuit_lifts;
                assert_eq!(lifts[0].lift.main, Some(LiftType::Conditioning));
                assert_eq!(lifts[1].lift.main, Some(LiftType::Mobility));
                assert_eq!(lifts[2].lift.main, Some(LiftType::Accessory));
                assert_eq!(lifts[3].lift.main, Some(LiftType::Accessory));
                assert_eq!(lifts[1].lift.region, LiftRegion::LOWER);
                assert_eq!(lifts[2].lift.region, LiftRegion::LOWER);
                assert_eq!(lifts[3].lift.region, LiftRegion::LOWER);
            }
            _ => panic!("expected circuit"),
        }
        match &thu.lifts[1] {
            WorkoutLift::Single(s) => {
                assert_eq!(s.lift.name, "Squat");
                assert_eq!(s.lift.main, Some(LiftType::Squat));
                assert_eq!(s.percent, Some(50 + i as u32 * 5));
                let ar = s.accommodating_resistance.clone().expect("ar");
                if i == 0 {
                    de_squat_ar = ar;
                } else {
                    assert_eq!(ar, de_squat_ar);
                }
            }
            _ => panic!("expected single"),
        }
        match &thu.lifts[2] {
            WorkoutLift::Single(s) => {
                assert_eq!(s.lift.name, "Deadlift");
                assert_eq!(s.lift.main, Some(LiftType::Deadlift));
                assert_eq!(s.percent, Some(50 + i as u32 * 5));
                let ar = s.accommodating_resistance.clone().expect("ar");
                if i == 0 {
                    de_dead_ar = ar;
                } else {
                    assert_eq!(ar, de_dead_ar);
                }
            }
            _ => panic!("expected single"),
        }

        let fri = week.get(&Weekday::Fri).expect("friday");
        assert_eq!(fri.lifts.len(), 3);
        match &fri.lifts[0] {
            WorkoutLift::Circuit(c) => {
                assert_eq!(c.circuit_lifts.len(), 4);
                let lifts = &c.circuit_lifts;
                assert_eq!(lifts[0].lift.main, Some(LiftType::Conditioning));
                assert_eq!(lifts[1].lift.main, Some(LiftType::Mobility));
                assert_eq!(lifts[2].lift.main, Some(LiftType::Accessory));
                assert_eq!(lifts[3].lift.main, Some(LiftType::Accessory));
                assert_eq!(lifts[1].lift.region, LiftRegion::UPPER);
                assert_eq!(lifts[2].lift.region, LiftRegion::UPPER);
                assert_eq!(lifts[3].lift.region, LiftRegion::UPPER);
            }
            _ => panic!("expected circuit"),
        }
        match &fri.lifts[1] {
            WorkoutLift::Single(s) => {
                assert_eq!(s.lift.name, "Bench Press");
                assert_eq!(s.lift.main, Some(LiftType::BenchPress));
                assert_eq!(s.percent, Some(50 + i as u32 * 5));
                let ar = s.accommodating_resistance.clone().expect("ar");
                if i == 0 {
                    de_bench_ar = ar;
                } else {
                    assert_eq!(ar, de_bench_ar);
                }
            }
            _ => panic!("expected single"),
        }
        match &fri.lifts[2] {
            WorkoutLift::Single(s) => {
                assert_eq!(s.lift.name, "Overhead Press");
                assert_eq!(s.lift.main, Some(LiftType::OverheadPress));
                assert_eq!(s.percent, Some(50 + i as u32 * 5));
                let ar = s.accommodating_resistance.clone().expect("ar");
                if i == 0 {
                    de_ohp_ar = ar;
                } else {
                    assert_eq!(ar, de_ohp_ar);
                }
            }
            _ => panic!("expected single"),
        }
    }
}
