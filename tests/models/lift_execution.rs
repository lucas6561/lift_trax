#[path = "../../src/models/lift_execution.rs"]
mod lift_execution;

use crate::weight::Weight;
use chrono::NaiveDate;
use lift_execution::LiftExecution;

#[test]
fn create_lift_execution() {
    let exec = LiftExecution {
        id: None,
        date: NaiveDate::from_ymd_opt(2024, 5, 20).unwrap(),
        sets: 3,
        reps: 5,
        weight: Weight::Raw(100.0),
        rpe: Some(8.5),
        notes: "felt good".into(),
    };
    assert_eq!(exec.sets, 3);
    assert_eq!(exec.weight.to_string(), "100 lb");
    assert_eq!(exec.notes, "felt good");
}
