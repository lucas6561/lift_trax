#[path = "../src/database.rs"]
mod database;
#[path = "../src/models/mod.rs"]
mod models;
#[path = "../src/random_stack.rs"]
pub mod random_stack;
#[path = "../src/sqlite_db.rs"]
mod sqlite_db;
#[path = "../src/weight.rs"]
mod weight;
#[path = "../src/workout_builder/mod.rs"]
mod workout_builder;

use chrono::Weekday;
use database::Database;
use models::{LiftRegion, LiftType, Muscle, SetMetric};
use sqlite_db::SqliteDb;
use std::collections::HashSet;
use workout_builder::{
    AccommodatingResistance, ConjugateWorkoutBuilder, WorkoutBuilder, WorkoutLiftKind,
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
        "Assault Bike",
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

    // targeted accessory lifts for circuits
    db.add_lift(
        "Hamstring Curl",
        LiftRegion::LOWER,
        LiftType::Accessory,
        &[Muscle::Hamstring],
        "",
    )
    .unwrap();
    db.add_lift(
        "Leg Extension",
        LiftRegion::LOWER,
        LiftType::Accessory,
        &[Muscle::Quad],
        "",
    )
    .unwrap();
    db.add_lift(
        "Calf Raise",
        LiftRegion::LOWER,
        LiftType::Accessory,
        &[Muscle::Calf],
        "",
    )
    .unwrap();
    db.add_lift(
        "Plank",
        LiftRegion::LOWER,
        LiftType::Accessory,
        &[Muscle::Core],
        "",
    )
    .unwrap();
    db.add_lift(
        "Sit-up",
        LiftRegion::LOWER,
        LiftType::Accessory,
        &[Muscle::Core],
        "",
    )
    .unwrap();
    db.add_lift(
        "Crunch",
        LiftRegion::LOWER,
        LiftType::Accessory,
        &[Muscle::Core],
        "",
    )
    .unwrap();
    db.add_lift(
        "Leg Raise",
        LiftRegion::LOWER,
        LiftType::Accessory,
        &[Muscle::Core],
        "",
    )
    .unwrap();
    db.add_lift(
        "Lat Pulldown",
        LiftRegion::UPPER,
        LiftType::Accessory,
        &[Muscle::Lat],
        "",
    )
    .unwrap();
    db.add_lift(
        "Tricep Pushdown",
        LiftRegion::UPPER,
        LiftType::Accessory,
        &[Muscle::Tricep],
        "",
    )
    .unwrap();
    db.add_lift(
        "Face Pull",
        LiftRegion::UPPER,
        LiftType::Accessory,
        &[Muscle::RearDelt],
        "",
    )
    .unwrap();
    db.add_lift(
        "Shoulder Raise",
        LiftRegion::UPPER,
        LiftType::Accessory,
        &[Muscle::Shoulder],
        "",
    )
    .unwrap();
    db.add_lift(
        "Front Raise",
        LiftRegion::UPPER,
        LiftType::Accessory,
        &[Muscle::FrontDelt],
        "",
    )
    .unwrap();
    db.add_lift(
        "Shrug",
        LiftRegion::UPPER,
        LiftType::Accessory,
        &[Muscle::Trap],
        "",
    )
    .unwrap();
    db.add_lift(
        "Bicep Curl",
        LiftRegion::UPPER,
        LiftType::Accessory,
        &[Muscle::Bicep],
        "",
    )
    .unwrap();
    db.add_lift(
        "Wrist Curl",
        LiftRegion::UPPER,
        LiftType::Accessory,
        &[Muscle::Forearm],
        "",
    )
    .unwrap();

    let builder = ConjugateWorkoutBuilder;
    let wave = builder.get_wave(6, &db).unwrap();
    assert_eq!(wave.len(), 6);

    let mut last_conditioning = None;
    for week in &wave {
        for day in [Weekday::Mon, Weekday::Tue, Weekday::Thu, Weekday::Fri] {
            if let Some(workout) = week.get(&day) {
                for lift in &workout.lifts {
                    if let WorkoutLiftKind::Single(single) = &lift.kind {
                        if single.lift.main == Some(LiftType::Conditioning) {
                            if let Some(prev) = &last_conditioning {
                                assert_ne!(prev, &single.lift.name);
                            }
                            last_conditioning = Some(single.lift.name.clone());
                        }
                    }
                }
            }
        }
    }

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

    let mut de_squat_resisted: Option<AccommodatingResistance> = None;
    let mut de_dead_resisted: Option<AccommodatingResistance> = None;
    let mut de_bench_resisted: Option<AccommodatingResistance> = None;
    let mut de_ohp_resisted: Option<AccommodatingResistance> = None;
    let dynamic_percents = [60, 65, 70, 50, 55, 60];

    for (i, week) in wave.iter().enumerate() {
        let mut warmup_cores = HashSet::new();
        let week_in_cycle = i % dynamic_percents.len();
        let expected_dynamic_percent = dynamic_percents[week_in_cycle];

        let mon = week.get(&Weekday::Mon).expect("monday");
        assert!(mon.lifts.len() == 9 || mon.lifts.len() == 10);
        match &mon.lifts[0].kind {
            WorkoutLiftKind::Circuit(c) => {
                assert_eq!(c.circuit_lifts.len(), 4);
                assert_eq!(c.rounds, 3);
                let lifts = &c.circuit_lifts;
                assert_eq!(lifts[0].lift.main, Some(LiftType::Mobility));
                assert_eq!(lifts[1].lift.main, Some(LiftType::Accessory));
                assert_eq!(lifts[2].lift.main, Some(LiftType::Accessory));
                assert_eq!(lifts[3].lift.main, Some(LiftType::Accessory));
                assert_eq!(lifts[1].lift.region, LiftRegion::LOWER);
                assert_eq!(lifts[2].lift.region, LiftRegion::LOWER);
                assert!(lifts.iter().all(|l| l.percent.is_none()));
                assert!(!lifts[1].lift.muscles.contains(&Muscle::Forearm));
                assert!(!lifts[1].lift.muscles.contains(&Muscle::Core));
                assert!(!lifts[2].lift.muscles.contains(&Muscle::Forearm));
                assert!(!lifts[2].lift.muscles.contains(&Muscle::Core));
                assert!(lifts[3].lift.muscles.contains(&Muscle::Core));
                warmup_cores.insert(lifts[3].lift.name.clone());
            }
            _ => panic!("expected circuit"),
        }
        let main_name = match &mon.lifts[1].kind {
            WorkoutLiftKind::Single(s) => {
                assert_eq!(s.lift.main, Some(expected_lower[i]));
                s.lift.name.clone()
            }
            _ => panic!("expected single"),
        };
        let has_forearm = mon
            .lifts
            .last()
            .map(|l| l.name == "Forearm Finisher")
            .unwrap_or(false);
        let base = if has_forearm { 8 } else { 7 };
        let backoff_count = mon.lifts.len() - base;
        assert_eq!(backoff_count, 2);
        for b in &mon.lifts[2..4] {
            match &b.kind {
                WorkoutLiftKind::Single(s) => {
                    assert_eq!(s.lift.name, main_name);
                    assert_eq!(s.percent, Some(70));
                    assert_eq!(s.metric, Some(SetMetric::Reps(5)));
                    assert_eq!(s.rpe, Some(7.0));
                }
                _ => panic!("expected single"),
            }
        }
        let next_lift = expected_lower[(i + 1) % expected_lower.len()];
        for b in &mon.lifts[2 + backoff_count..5 + backoff_count] {
            match &b.kind {
                WorkoutLiftKind::Single(s) => {
                    assert_eq!(s.lift.main, Some(next_lift));
                    assert_eq!(s.percent, Some(80));
                    assert_eq!(s.metric, Some(SetMetric::Reps(5)));
                }
                _ => panic!("expected single"),
            }
        }
        match &mon.lifts[5 + backoff_count].kind {
            WorkoutLiftKind::Circuit(c) => {
                assert_eq!(c.circuit_lifts.len(), 3);
                assert_eq!(c.rounds, 3);
                for l in &c.circuit_lifts {
                    assert_eq!(l.lift.main, Some(LiftType::Accessory));
                    match l.metric {
                        Some(SetMetric::RepsRange { min, max }) => {
                            assert_eq!((min, max), (8, 12));
                        }
                        _ => panic!("expected rep range"),
                    }
                }
            }
            _ => panic!("expected circuit"),
        }
        let cond_idx = 6 + backoff_count;
        match &mon.lifts[cond_idx].kind {
            WorkoutLiftKind::Single(s) => {
                assert_eq!(s.lift.main, Some(LiftType::Conditioning));
                assert_eq!(s.metric, Some(SetMetric::TimeSecs(600)));
                assert_eq!(s.percent, None);
            }
            _ => panic!("expected single"),
        }
        if has_forearm {
            match &mon.lifts[cond_idx + 1].kind {
                WorkoutLiftKind::Single(s) => {
                    assert_eq!(s.lift.main, Some(LiftType::Accessory));
                }
                _ => panic!("expected single"),
            }
        }

        let tue = week.get(&Weekday::Tue).expect("tuesday");
        assert!(tue.lifts.len() == 9 || tue.lifts.len() == 10);
        match &tue.lifts[0].kind {
            WorkoutLiftKind::Circuit(c) => {
                assert_eq!(c.circuit_lifts.len(), 4);
                assert_eq!(c.rounds, 3);
                let lifts = &c.circuit_lifts;
                assert_eq!(lifts[0].lift.main, Some(LiftType::Mobility));
                assert_eq!(lifts[1].lift.main, Some(LiftType::Accessory));
                assert_eq!(lifts[2].lift.main, Some(LiftType::Accessory));
                assert_eq!(lifts[3].lift.main, Some(LiftType::Accessory));
                assert_eq!(lifts[0].lift.region, LiftRegion::UPPER);
                assert_eq!(lifts[1].lift.region, LiftRegion::UPPER);
                assert_eq!(lifts[2].lift.region, LiftRegion::UPPER);
                assert!(lifts.iter().all(|l| l.percent.is_none()));
                assert!(!lifts[1].lift.muscles.contains(&Muscle::Forearm));
                assert!(!lifts[1].lift.muscles.contains(&Muscle::Core));
                assert!(!lifts[2].lift.muscles.contains(&Muscle::Forearm));
                assert!(!lifts[2].lift.muscles.contains(&Muscle::Core));
                assert!(lifts[3].lift.muscles.contains(&Muscle::Core));
                warmup_cores.insert(lifts[3].lift.name.clone());
            }
            _ => panic!("expected circuit"),
        }
        let main_name = match &tue.lifts[1].kind {
            WorkoutLiftKind::Single(s) => {
                assert_eq!(s.lift.main, Some(expected_upper[i]));
                s.lift.name.clone()
            }
            _ => panic!("expected single"),
        };
        let has_forearm = tue
            .lifts
            .last()
            .map(|l| l.name == "Forearm Finisher")
            .unwrap_or(false);
        let base = if has_forearm { 8 } else { 7 };
        let backoff_count = tue.lifts.len() - base;
        assert_eq!(backoff_count, 2);
        for b in &tue.lifts[2..4] {
            match &b.kind {
                WorkoutLiftKind::Single(s) => {
                    assert_eq!(s.lift.name, main_name);
                    assert_eq!(s.percent, Some(70));
                    assert_eq!(s.metric, Some(SetMetric::Reps(5)));
                    assert_eq!(s.rpe, Some(7.0));
                }
                _ => panic!("expected single"),
            }
        }
        let next_lift = expected_upper[(i + 1) % expected_upper.len()];
        for b in &tue.lifts[2 + backoff_count..5 + backoff_count] {
            match &b.kind {
                WorkoutLiftKind::Single(s) => {
                    assert_eq!(s.lift.main, Some(next_lift));
                    assert_eq!(s.percent, Some(80));
                    assert_eq!(s.metric, Some(SetMetric::Reps(5)));
                }
                _ => panic!("expected single"),
            }
        }
        match &tue.lifts[5 + backoff_count].kind {
            WorkoutLiftKind::Circuit(c) => {
                assert_eq!(c.circuit_lifts.len(), 3);
                assert_eq!(c.rounds, 3);
                for l in &c.circuit_lifts {
                    assert_eq!(l.lift.main, Some(LiftType::Accessory));
                    match l.metric {
                        Some(SetMetric::RepsRange { min, max }) => {
                            assert_eq!((min, max), (8, 12));
                        }
                        _ => panic!("expected rep range"),
                    }
                }
            }
            _ => panic!("expected circuit"),
        }
        let cond_idx = 6 + backoff_count;
        match &tue.lifts[cond_idx].kind {
            WorkoutLiftKind::Single(s) => {
                assert_eq!(s.lift.main, Some(LiftType::Conditioning));
                assert_eq!(s.metric, Some(SetMetric::TimeSecs(600)));
                assert_eq!(s.percent, None);
            }
            _ => panic!("expected single"),
        }
        if has_forearm {
            match &tue.lifts[cond_idx + 1].kind {
                WorkoutLiftKind::Single(s) => {
                    assert_eq!(s.lift.main, Some(LiftType::Accessory));
                }
                _ => panic!("expected single"),
            }
        }

        let thu = week.get(&Weekday::Thu).expect("thursday");
        assert_eq!(thu.lifts.len(), 16);
        match &thu.lifts[0].kind {
            WorkoutLiftKind::Circuit(c) => {
                assert_eq!(c.circuit_lifts.len(), 4);
                assert_eq!(c.rounds, 3);
                let lifts = &c.circuit_lifts;
                assert_eq!(lifts[0].lift.main, Some(LiftType::Mobility));
                assert_eq!(lifts[1].lift.main, Some(LiftType::Accessory));
                assert_eq!(lifts[2].lift.main, Some(LiftType::Accessory));
                assert_eq!(lifts[3].lift.main, Some(LiftType::Accessory));
                assert_eq!(lifts[0].lift.region, LiftRegion::LOWER);
                assert_eq!(lifts[1].lift.region, LiftRegion::LOWER);
                assert_eq!(lifts[2].lift.region, LiftRegion::LOWER);
                assert!(lifts.iter().all(|l| l.percent.is_none()));
                assert!(!lifts[1].lift.muscles.contains(&Muscle::Forearm));
                assert!(!lifts[1].lift.muscles.contains(&Muscle::Core));
                assert!(!lifts[2].lift.muscles.contains(&Muscle::Forearm));
                assert!(!lifts[2].lift.muscles.contains(&Muscle::Core));
                assert!(lifts[3].lift.muscles.contains(&Muscle::Core));
                warmup_cores.insert(lifts[3].lift.name.clone());
            }
            _ => panic!("expected circuit"),
        }
        for l in &thu.lifts[1..7] {
            match &l.kind {
                WorkoutLiftKind::Single(s) => {
                    assert_eq!(s.lift.name, "Squat");
                    assert_eq!(s.lift.main, Some(LiftType::Squat));
                    assert_eq!(s.percent, Some(expected_dynamic_percent));
                    assert_eq!(s.metric, Some(SetMetric::Reps(3)));
                    let ar = s.accommodating_resistance.clone().expect("ar");
                    if week_in_cycle < 3 {
                        assert_eq!(ar, AccommodatingResistance::Straight);
                    } else {
                        assert_ne!(ar, AccommodatingResistance::Straight);
                        assert!(matches!(
                            ar,
                            AccommodatingResistance::Chains | AccommodatingResistance::Bands
                        ));
                        match &mut de_squat_resisted {
                            None => de_squat_resisted = Some(ar.clone()),
                            Some(expected) => assert_eq!(expected, &ar),
                        }
                    }
                }
                _ => panic!("expected single"),
            }
        }
        for l in &thu.lifts[7..13] {
            match &l.kind {
                WorkoutLiftKind::Single(s) => {
                    assert_eq!(s.lift.name, "Deadlift");
                    assert_eq!(s.lift.main, Some(LiftType::Deadlift));
                    assert_eq!(s.percent, Some(expected_dynamic_percent));
                    assert_eq!(s.metric, Some(SetMetric::Reps(2)));
                    let ar = s.accommodating_resistance.clone().expect("ar");
                    if week_in_cycle < 3 {
                        assert_eq!(ar, AccommodatingResistance::Straight);
                    } else {
                        assert_ne!(ar, AccommodatingResistance::Straight);
                        assert!(matches!(
                            ar,
                            AccommodatingResistance::Chains | AccommodatingResistance::Bands
                        ));
                        match &mut de_dead_resisted {
                            None => de_dead_resisted = Some(ar.clone()),
                            Some(expected) => assert_eq!(expected, &ar),
                        }
                    }
                }
                _ => panic!("expected single"),
            }
        }
        match &thu.lifts[13].kind {
            WorkoutLiftKind::Circuit(c) => {
                assert_eq!(c.circuit_lifts.len(), 3);
                assert_eq!(c.rounds, 3);
                for l in &c.circuit_lifts {
                    assert_eq!(l.lift.main, Some(LiftType::Accessory));
                    match l.metric {
                        Some(SetMetric::RepsRange { min, max }) => {
                            assert_eq!((min, max), (8, 12));
                        }
                        _ => panic!("expected rep range"),
                    }
                }
            }
            _ => panic!("expected circuit"),
        }
        match &thu.lifts[14].kind {
            WorkoutLiftKind::Single(s) => {
                assert_eq!(s.lift.main, Some(LiftType::Conditioning));
                assert_eq!(s.metric, Some(SetMetric::TimeSecs(600)));
                assert_eq!(s.percent, None);
            }
            _ => panic!("expected single"),
        }
        match &thu.lifts[15].kind {
            WorkoutLiftKind::Single(s) => {
                assert_eq!(s.lift.main, Some(LiftType::Accessory));
            }
            _ => panic!("expected single"),
        }

        let fri = week.get(&Weekday::Fri).expect("friday");
        assert_eq!(fri.lifts.len(), 19);
        match &fri.lifts[0].kind {
            WorkoutLiftKind::Circuit(c) => {
                assert_eq!(c.circuit_lifts.len(), 4);
                assert_eq!(c.rounds, 3);
                let lifts = &c.circuit_lifts;
                assert_eq!(lifts[0].lift.main, Some(LiftType::Mobility));
                assert_eq!(lifts[1].lift.main, Some(LiftType::Accessory));
                assert_eq!(lifts[2].lift.main, Some(LiftType::Accessory));
                assert_eq!(lifts[3].lift.main, Some(LiftType::Accessory));
                assert_eq!(lifts[0].lift.region, LiftRegion::UPPER);
                assert_eq!(lifts[1].lift.region, LiftRegion::UPPER);
                assert_eq!(lifts[2].lift.region, LiftRegion::UPPER);
                assert!(lifts.iter().all(|l| l.percent.is_none()));
                assert!(!lifts[1].lift.muscles.contains(&Muscle::Forearm));
                assert!(!lifts[1].lift.muscles.contains(&Muscle::Core));
                assert!(!lifts[2].lift.muscles.contains(&Muscle::Forearm));
                assert!(!lifts[2].lift.muscles.contains(&Muscle::Core));
                assert!(lifts[3].lift.muscles.contains(&Muscle::Core));
                warmup_cores.insert(lifts[3].lift.name.clone());
            }
            _ => panic!("expected circuit"),
        }
        for l in &fri.lifts[1..10] {
            match &l.kind {
                WorkoutLiftKind::Single(s) => {
                    assert_eq!(s.lift.name, "Bench Press");
                    assert_eq!(s.lift.main, Some(LiftType::BenchPress));
                    assert_eq!(s.percent, Some(expected_dynamic_percent));
                    assert_eq!(s.metric, Some(SetMetric::Reps(3)));
                    let ar = s.accommodating_resistance.clone().expect("ar");
                    if week_in_cycle < 3 {
                        assert_eq!(ar, AccommodatingResistance::Straight);
                    } else {
                        assert_ne!(ar, AccommodatingResistance::Straight);
                        assert!(matches!(
                            ar,
                            AccommodatingResistance::Chains | AccommodatingResistance::Bands
                        ));
                        match &mut de_bench_resisted {
                            None => de_bench_resisted = Some(ar.clone()),
                            Some(expected) => assert_eq!(expected, &ar),
                        }
                    }
                }
                _ => panic!("expected single"),
            }
        }
        for l in &fri.lifts[10..16] {
            match &l.kind {
                WorkoutLiftKind::Single(s) => {
                    assert_eq!(s.lift.name, "Overhead Press");
                    assert_eq!(s.lift.main, Some(LiftType::OverheadPress));
                    assert_eq!(s.percent, Some(expected_dynamic_percent));
                    assert_eq!(s.metric, Some(SetMetric::Reps(2)));
                    let ar = s.accommodating_resistance.clone().expect("ar");
                    if week_in_cycle < 3 {
                        assert_eq!(ar, AccommodatingResistance::Straight);
                    } else {
                        assert_ne!(ar, AccommodatingResistance::Straight);
                        assert!(matches!(
                            ar,
                            AccommodatingResistance::Chains | AccommodatingResistance::Bands
                        ));
                        match &mut de_ohp_resisted {
                            None => de_ohp_resisted = Some(ar.clone()),
                            Some(expected) => assert_eq!(expected, &ar),
                        }
                    }
                }
                _ => panic!("expected single"),
            }
        }
        match &fri.lifts[16].kind {
            WorkoutLiftKind::Circuit(c) => {
                assert_eq!(c.circuit_lifts.len(), 3);
                assert_eq!(c.rounds, 3);
                for l in &c.circuit_lifts {
                    assert_eq!(l.lift.main, Some(LiftType::Accessory));
                    match l.metric {
                        Some(SetMetric::RepsRange { min, max }) => {
                            assert_eq!((min, max), (8, 12));
                        }
                        _ => panic!("expected rep range"),
                    }
                }
            }
            _ => panic!("expected circuit"),
        }
        match &fri.lifts[17].kind {
            WorkoutLiftKind::Single(s) => {
                assert_eq!(s.lift.main, Some(LiftType::Conditioning));
                assert_eq!(s.metric, Some(SetMetric::TimeSecs(600)));
                assert_eq!(s.percent, None);
            }
            _ => panic!("expected single"),
        }
        match &fri.lifts[18].kind {
            WorkoutLiftKind::Single(s) => {
                assert_eq!(s.lift.main, Some(LiftType::Accessory));
            }
            _ => panic!("expected single"),
        }
        assert_eq!(warmup_cores.len(), 4);
    }
}
