use rand::{seq::SliceRandom, thread_rng};

use crate::database::{Database, DbResult};
use crate::models::{Lift, LiftType};

pub(crate) struct MaxEffortLiftPools {
    lower_weeks: Vec<Lift>,
    upper_weeks: Vec<Lift>,
}

impl MaxEffortLiftPools {
    pub(crate) fn new(num_weeks: usize, db: &dyn Database) -> DbResult<Self> {
        let mut squats = db.lifts_by_type(LiftType::Squat)?;
        let mut deadlifts = db.lifts_by_type(LiftType::Deadlift)?;
        let mut benches = db.lifts_by_type(LiftType::BenchPress)?;
        let mut overheads = db.lifts_by_type(LiftType::OverheadPress)?;

        let squat_weeks = (num_weeks + 1) / 2;
        let dead_weeks = num_weeks / 2;
        let bench_weeks = (num_weeks + 1) / 2;
        let ohp_weeks = num_weeks / 2;

        if squats.len() < squat_weeks {
            return Err("not enough squat lifts available".into());
        }
        if deadlifts.len() < dead_weeks {
            return Err("not enough deadlift lifts available".into());
        }
        if benches.len() < bench_weeks {
            return Err("not enough bench press lifts available".into());
        }
        if overheads.len() < ohp_weeks {
            return Err("not enough overhead press lifts available".into());
        }

        let mut rng = thread_rng();
        squats.shuffle(&mut rng);
        deadlifts.shuffle(&mut rng);
        benches.shuffle(&mut rng);
        overheads.shuffle(&mut rng);

        let mut lower_weeks = Vec::with_capacity(num_weeks);
        let mut upper_weeks = Vec::with_capacity(num_weeks);
        let mut squat_idx = 0usize;
        let mut dead_idx = 0usize;
        let mut bench_idx = 0usize;
        let mut ohp_idx = 0usize;
        for i in 0..num_weeks {
            if i % 2 == 0 {
                lower_weeks.push(squats[squat_idx].clone());
                squat_idx += 1;
                upper_weeks.push(benches[bench_idx].clone());
                bench_idx += 1;
            } else {
                lower_weeks.push(deadlifts[dead_idx].clone());
                dead_idx += 1;
                upper_weeks.push(overheads[ohp_idx].clone());
                ohp_idx += 1;
            }
        }

        Ok(Self {
            lower_weeks,
            upper_weeks,
        })
    }

    pub(crate) fn schedule(&self) -> (Vec<Lift>, Vec<Lift>) {
        (self.lower_weeks.clone(), self.upper_weeks.clone())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::database::DbResult;
    use crate::models::{LiftExecution, LiftRegion, Muscle};
    use std::collections::HashSet;

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

        fn get_executions(&self, lift_name: &str) -> Vec<LiftExecution> {
            unimplemented!()
        }

        fn update_lift_execution(&self, _exec_id: i32, _execution: &LiftExecution) -> DbResult<()> {
            unimplemented!()
        }

        fn delete_lift_execution(&self, _exec_id: i32) -> DbResult<()> {
            unimplemented!()
        }

        fn lift_stats(&self, _name: &str) -> DbResult<crate::models::LiftStats> {
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
}
