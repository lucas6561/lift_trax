#[path = "../../src/models/lift.rs"]
mod lift;
#[path = "../../src/models/lift_execution.rs"]
mod lift_execution;
#[path = "../../src/models/lift_region.rs"]
mod lift_region;
#[path = "../../src/models/lift_type.rs"]
mod lift_type;
#[path = "../../src/models/muscle.rs"]
mod muscle;

use crate::weight::Weight;
use chrono::NaiveDate;
use lift::Lift;
use lift_execution::{ExecutionSet, LiftExecution, SetMetric};
use lift_region::LiftRegion;
use lift_type::LiftType;
use muscle::Muscle;

#[test]
fn create_lift_with_execution() {
    let exec = LiftExecution {
        id: None,
        date: NaiveDate::from_ymd_opt(2024, 1, 1).unwrap(),
        sets: vec![
            ExecutionSet {
                metric: SetMetric::Reps(3),
                weight: Weight::Raw(200.0),
                rpe: None,
            };
            5
        ],
        warmup: false,
        deload: false,
        notes: String::new(),
    };
    let lift = Lift {
        name: "Squat".to_string(),
        region: LiftRegion::LOWER,
        main: Some(LiftType::Squat),
        muscles: vec![Muscle::Quad, Muscle::Glute],
        notes: String::new(),
        executions: vec![exec.clone()],
    };
    assert_eq!(lift.name, "Squat");
    assert_eq!(lift.executions.len(), 1);
    match lift.executions[0].sets[0].metric {
        SetMetric::Reps(r) => assert_eq!(r, 3),
        _ => panic!("expected reps"),
    }
}
