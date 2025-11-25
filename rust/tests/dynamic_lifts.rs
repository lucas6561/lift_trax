use lift_trax_cli::database::{Database, DbResult};
use lift_trax_cli::models::{Lift, LiftExecution, LiftRegion, LiftType, Muscle};
use lift_trax_cli::workout_builder::{AccommodatingResistance, DynamicLifts};

struct TestDb;

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

    fn get_lift(&self, name: &str) -> DbResult<Lift> {
        Ok(Lift {
            name: name.to_string(),
            region: if name.contains("Press") {
                LiftRegion::UPPER
            } else {
                LiftRegion::LOWER
            },
            main: Some(match name {
                "Squat" => LiftType::Squat,
                "Deadlift" => LiftType::Deadlift,
                "Bench Press" => LiftType::BenchPress,
                "Overhead Press" => LiftType::OverheadPress,
                _ => LiftType::Accessory,
            }),
            muscles: Vec::new(),
            notes: String::new(),
        })
    }

    fn lifts_by_type(&self, lift_type: LiftType) -> DbResult<Vec<Lift>> {
        let lift = match lift_type {
            LiftType::Squat => self.get_lift("Squat")?,
            LiftType::Deadlift => self.get_lift("Deadlift")?,
            LiftType::BenchPress => self.get_lift("Bench Press")?,
            LiftType::OverheadPress => self.get_lift("Overhead Press")?,
            _ => return Ok(Vec::new()),
        };
        Ok(vec![lift])
    }

    fn get_accessories_by_muscle(&self, _muscle: Muscle) -> DbResult<Vec<Lift>> {
        unimplemented!()
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
fn builds_dynamic_variations_for_each_pattern() {
    unsafe { std::env::set_var("LIFT_TRAX_HEADLESS", "1") };
    let lifts = DynamicLifts::new(&TestDb).expect("dynamic lifts to build");

    assert_eq!(lifts.squat.lift.name, "Squat");
    assert_eq!(lifts.deadlift.lift.name, "Deadlift");
    assert_eq!(lifts.bench.lift.name, "Bench Press");
    assert_eq!(lifts.overhead.lift.name, "Overhead Press");

    let valid = [
        AccommodatingResistance::Chains,
        AccommodatingResistance::Bands,
    ];

    for lift in [&lifts.squat, &lifts.deadlift, &lifts.bench, &lifts.overhead] {
        assert!(valid.contains(&lift.ar));
    }
}
