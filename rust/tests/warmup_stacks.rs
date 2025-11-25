use lift_trax_cli::database::{Database, DbResult};
use lift_trax_cli::models::{Lift, LiftExecution, LiftRegion, LiftType, Muscle};
use lift_trax_cli::workout_builder::{WarmupStacks, WorkoutLiftKind};

struct TestDb {
    lower_accessories: Vec<Lift>,
    upper_accessories: Vec<Lift>,
    lower_mobility: Vec<Lift>,
    upper_mobility: Vec<Lift>,
    core: Vec<Lift>,
}

impl TestDb {
    fn new() -> Self {
        Self {
            lower_accessories: vec![
                lift(
                    "Reverse Lunge",
                    LiftRegion::LOWER,
                    LiftType::Accessory,
                    vec![Muscle::Quad],
                ),
                lift(
                    "Hamstring Curl",
                    LiftRegion::LOWER,
                    LiftType::Accessory,
                    vec![Muscle::Hamstring],
                ),
                lift(
                    "Calf Raise",
                    LiftRegion::LOWER,
                    LiftType::Accessory,
                    vec![Muscle::Calf],
                ),
            ],
            upper_accessories: vec![
                lift(
                    "Face Pull",
                    LiftRegion::UPPER,
                    LiftType::Accessory,
                    vec![Muscle::RearDelt],
                ),
                lift(
                    "Tricep Pushdown",
                    LiftRegion::UPPER,
                    LiftType::Accessory,
                    vec![Muscle::Tricep],
                ),
                lift(
                    "Lat Pulldown",
                    LiftRegion::UPPER,
                    LiftType::Accessory,
                    vec![Muscle::Lat],
                ),
            ],
            lower_mobility: vec![lift(
                "Hip Airplane",
                LiftRegion::LOWER,
                LiftType::Mobility,
                vec![Muscle::Quad],
            )],
            upper_mobility: vec![lift(
                "Band Pull Apart",
                LiftRegion::UPPER,
                LiftType::Mobility,
                vec![Muscle::Shoulder],
            )],
            core: vec![lift(
                "Plank",
                LiftRegion::UPPER,
                LiftType::Accessory,
                vec![Muscle::Core],
            )],
        }
    }
}

fn lift(name: &str, region: LiftRegion, main: LiftType, muscles: Vec<Muscle>) -> Lift {
    Lift {
        name: name.to_string(),
        region,
        main: Some(main),
        muscles,
        notes: String::new(),
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
        Ok(self
            .core
            .iter()
            .filter(|l| l.muscles.contains(&muscle))
            .cloned()
            .collect())
    }

    fn lifts_by_region_and_type(
        &self,
        region: LiftRegion,
        lift_type: LiftType,
    ) -> DbResult<Vec<Lift>> {
        let lifts = match (region, lift_type) {
            (LiftRegion::LOWER, LiftType::Accessory) => &self.lower_accessories,
            (LiftRegion::UPPER, LiftType::Accessory) => &self.upper_accessories,
            (LiftRegion::LOWER, LiftType::Mobility) => &self.lower_mobility,
            (LiftRegion::UPPER, LiftType::Mobility) => &self.upper_mobility,
            _ => panic!("unexpected query"),
        };
        Ok(lifts.clone())
    }
}

#[test]
fn builds_circuit_with_expected_properties() {
    let db = TestDb::new();
    let mut stacks = WarmupStacks::new(&db).expect("stacks to build");

    let warmup = stacks
        .warmup(LiftRegion::LOWER)
        .expect("warmup to generate");

    assert_eq!(warmup.name, "Warmup Circuit");
    match warmup.kind {
        WorkoutLiftKind::Circuit(circuit) => {
            assert!(circuit.warmup);
            assert_eq!(circuit.rounds, 3);
            assert_eq!(circuit.circuit_lifts.len(), 4);
        }
        _ => panic!("expected circuit"),
    }
}
