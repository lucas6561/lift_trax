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
use lift_execution::LiftExecution;
use lift_region::LiftRegion;
use lift_type::LiftType;
use muscle::Muscle;

#[test]
fn create_lift_with_execution() {
    let exec = LiftExecution {
        id: None,
        date: NaiveDate::from_ymd_opt(2024, 1, 1).unwrap(),
        sets: 5,
        reps: 3,
        weight: Weight::Raw(200.0),
        rpe: None,
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
    assert_eq!(lift.executions[0].reps, 3);
}
