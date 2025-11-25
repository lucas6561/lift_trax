use std::collections::HashMap;

use lift_trax_cli::database::{Database, DbResult};
use lift_trax_cli::models::{Lift, LiftExecution, LiftRegion, LiftType, Muscle, SetMetric};
use lift_trax_cli::workout_builder::AccessoryStacks;

struct TestDb {
    accessories: HashMap<Muscle, Vec<Lift>>,
}

impl TestDb {
    fn new() -> Self {
        let mut accessories = HashMap::new();
        for muscle in [
            Muscle::Hamstring,
            Muscle::Quad,
            Muscle::Calf,
            Muscle::Lat,
            Muscle::Tricep,
            Muscle::RearDelt,
            Muscle::Shoulder,
            Muscle::FrontDelt,
            Muscle::Trap,
            Muscle::Core,
            Muscle::Bicep,
            Muscle::Forearm,
        ] {
            accessories.insert(
                muscle,
                vec![Lift {
                    name: format!("{muscle:?} Accessory"),
                    region: if matches!(muscle, Muscle::Hamstring | Muscle::Quad | Muscle::Calf) {
                        LiftRegion::LOWER
                    } else {
                        LiftRegion::UPPER
                    },
                    main: Some(LiftType::Accessory),
                    muscles: vec![muscle],
                    notes: String::new(),
                }],
            );
        }
        Self { accessories }
    }
}

impl Database for TestDb {
    fn add_lift(
        &self,
        _name: &str,
        _region: LiftRegion,
        _main: LiftType,
        _muscles: &[Muscle],
        _notes: &str,
    ) -> DbResult<()> {
        unimplemented!()
    }

    fn add_lift_execution(&self, _name: &str, _execution: &LiftExecution) -> DbResult<()> {
        unimplemented!()
    }

    fn update_lift(
        &self,
        _current_name: &str,
        _new_name: &str,
        _region: LiftRegion,
        _main: Option<LiftType>,
        _muscles: &[Muscle],
        _notes: &str,
    ) -> DbResult<()> {
        unimplemented!()
    }

    fn delete_lift(&self, _name: &str) -> DbResult<()> {
        unimplemented!()
    }

    fn get_executions(&self, _lift_name: &str) -> Vec<LiftExecution> {
        unimplemented!()
    }

    fn update_lift_execution(&self, _exec_id: i32, _execution: &LiftExecution) -> DbResult<()> {
        unimplemented!()
    }

    fn delete_lift_execution(&self, _exec_id: i32) -> DbResult<()> {
        unimplemented!()
    }

    fn lift_stats(&self, _name: &str) -> DbResult<lift_trax_cli::models::LiftStats> {
        unimplemented!()
    }

    fn list_lifts(&self) -> DbResult<Vec<Lift>> {
        unimplemented!()
    }

    fn get_lift(&self, _name: &str) -> DbResult<Lift> {
        unimplemented!()
    }

    fn lifts_by_type(&self, _lift_type: LiftType) -> DbResult<Vec<Lift>> {
        unimplemented!()
    }

    fn get_accessories_by_muscle(&self, muscle: Muscle) -> DbResult<Vec<Lift>> {
        Ok(self.accessories.get(&muscle).cloned().unwrap_or_default())
    }

    fn lifts_by_region_and_type(
        &self,
        _region: LiftRegion,
        _lift_type: LiftType,
    ) -> DbResult<Vec<Lift>> {
        unimplemented!()
    }
}

#[test]
fn returns_single_with_expected_reps() {
    let db = TestDb::new();
    let mut stacks = AccessoryStacks::new(&db).expect("stacks to build");

    let lift = stacks.single(Muscle::Lat).expect("lat accessory to exist");

    match lift.metric {
        Some(SetMetric::RepsRange { min, max }) => {
            assert_eq!((min, max), (8, 12));
        }
        other => panic!("unexpected metric: {other:?}"),
    }
}

#[test]
fn returns_forearm_optional() {
    let db = TestDb::new();
    let mut stacks = AccessoryStacks::new(&db).expect("stacks to build");

    let lift = stacks.forearm().expect("forearm accessory to exist");
    assert_eq!(lift.metric, Some(SetMetric::Reps(5)));
    assert_eq!(lift.percent, Some(80));
}
