#[path = "../src/list.rs"]
mod list;
#[path = "../src/models/mod.rs"]
mod models;
#[path = "../src/weight.rs"]
mod weight;

use list::filter_lifts;
use models::{Lift, LiftRegion, LiftType, Muscle};

#[test]
fn filter_lifts_by_muscle() {
    let lifts = vec![
        Lift {
            name: "Bench".into(),
            region: LiftRegion::UPPER,
            main: Some(LiftType::BenchPress),
            muscles: vec![Muscle::Chest, Muscle::Tricep],
            notes: String::new(),
        },
        Lift {
            name: "Squat".into(),
            region: LiftRegion::LOWER,
            main: Some(LiftType::Squat),
            muscles: vec![Muscle::Quad],
            notes: String::new(),
        },
    ];

    let filtered = filter_lifts(lifts, None, vec![Muscle::Chest]);
    assert_eq!(filtered.len(), 1);
    assert_eq!(filtered[0].name, "Bench");
}
