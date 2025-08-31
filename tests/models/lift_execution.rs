#[path = "../../src/models/lift_execution.rs"]
mod lift_execution;

use crate::weight::Weight;
use chrono::NaiveDate;
use lift_execution::{ExecutionSet, LiftExecution, SetMetric};

#[test]
fn format_execution_sets_groups_identical_consecutive_sets() {
    let sets = vec![
        ExecutionSet {
            metric: SetMetric::Reps(5),
            weight: Weight::Raw(100.0),
            rpe: None,
        },
        ExecutionSet {
            metric: SetMetric::Reps(5),
            weight: Weight::Raw(100.0),
            rpe: None,
        },
        ExecutionSet {
            metric: SetMetric::Reps(3),
            weight: Weight::Raw(120.0),
            rpe: None,
        },
        ExecutionSet {
            metric: SetMetric::Reps(3),
            weight: Weight::Raw(120.0),
            rpe: Some(8.0),
        },
        ExecutionSet {
            metric: SetMetric::Reps(3),
            weight: Weight::Raw(120.0),
            rpe: Some(8.0),
        },
    ];
    let formatted = lift_execution::format_execution_sets(&sets);
    assert_eq!(
        formatted,
        "2x 5 reps @ 100 lb, 3 reps @ 120 lb, 2x 3 reps @ 120 lb RPE 8"
    );
}

#[test]
fn create_lift_execution() {
    let exec = LiftExecution {
        id: None,
        date: NaiveDate::from_ymd_opt(2024, 5, 20).unwrap(),
        sets: vec![ExecutionSet {
            metric: SetMetric::Reps(5),
            weight: Weight::Raw(100.0),
            rpe: Some(8.5),
        }],
        warmup: false,
        notes: "felt good".into(),
    };
    assert_eq!(exec.sets.len(), 1);
    assert_eq!(exec.sets[0].weight.to_string(), "100 lb");
    assert_eq!(exec.notes, "felt good");
}
