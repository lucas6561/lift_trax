use std::collections::HashSet;

use lift_trax_cli::database::{Database, DbResult};
use lift_trax_cli::models::{Lift, LiftExecution, LiftRegion, LiftType, Muscle};
use lift_trax_cli::workout_builder::MaxEffortLiftPools;

struct TestDb {
    squats: Vec<Lift>,
    deadlifts: Vec<Lift>,
    benches: Vec<Lift>,
    overheads: Vec<Lift>,
}

impl TestDb {
    fn new() -> Self {
        let make = |name: &str, region: LiftRegion, lift_type: LiftType| Lift {
            name: name.to_string(),
            region,
            main: Some(lift_type),
            muscles: Vec::new(),
            notes: String::new(),
        };
        Self {
            squats: vec![
                make("SSB Squat", LiftRegion::LOWER, LiftType::Squat),
                make("Pause Squat", LiftRegion::LOWER, LiftType::Squat),
            ],
            deadlifts: vec![
                make("Block Pull", LiftRegion::LOWER, LiftType::Deadlift),
                make("Deficit Deadlift", LiftRegion::LOWER, LiftType::Deadlift),
            ],
            benches: vec![
                make("Close Grip Bench", LiftRegion::UPPER, LiftType::BenchPress),
                make("Wide Grip Bench", LiftRegion::UPPER, LiftType::BenchPress),
            ],
            overheads: vec![
                make("Push Press", LiftRegion::UPPER, LiftType::OverheadPress),
                make("Z Press", LiftRegion::UPPER, LiftType::OverheadPress),
            ],
        }
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

    fn lifts_by_type(&self, lift_type: LiftType) -> DbResult<Vec<Lift>> {
        let lifts = match lift_type {
            LiftType::Squat => &self.squats,
            LiftType::Deadlift => &self.deadlifts,
            LiftType::BenchPress => &self.benches,
            LiftType::OverheadPress => &self.overheads,
            _ => panic!("unexpected type"),
        };
        Ok(lifts.clone())
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
fn alternates_movement_patterns() {
    let db = TestDb::new();
    let squat_names = db
        .squats
        .iter()
        .map(|l| l.name.clone())
        .collect::<HashSet<_>>();
    let deadlift_names = db
        .deadlifts
        .iter()
        .map(|l| l.name.clone())
        .collect::<HashSet<_>>();
    let bench_names = db
        .benches
        .iter()
        .map(|l| l.name.clone())
        .collect::<HashSet<_>>();
    let overhead_names = db
        .overheads
        .iter()
        .map(|l| l.name.clone())
        .collect::<HashSet<_>>();
    let pools = MaxEffortLiftPools::new(4, &db).expect("pools to build");
    let (lower, upper) = pools.schedule();

    assert_eq!(lower.len(), 4);
    assert_eq!(upper.len(), 4);

    for (idx, lift) in lower.iter().enumerate() {
        if idx % 2 == 0 {
            assert!(squat_names.contains(&lift.name));
        } else {
            assert!(deadlift_names.contains(&lift.name));
        }
    }

    for (idx, lift) in upper.iter().enumerate() {
        if idx % 2 == 0 {
            assert!(bench_names.contains(&lift.name));
        } else {
            assert!(overhead_names.contains(&lift.name));
        }
    }
}
