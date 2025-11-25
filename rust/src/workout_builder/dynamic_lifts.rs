use rand::{seq::SliceRandom, thread_rng};

use crate::database::{Database, DbResult};
use crate::models::{Lift, LiftType};

use super::{
    AccommodatingResistance,
    dynamic_lift_selector::{DynamicLiftChoices, edit_dynamic_lifts},
};

pub(crate) struct DynamicLift {
    pub(crate) lift: Lift,
    pub(crate) ar: AccommodatingResistance,
}

pub(crate) struct DynamicLifts {
    pub(crate) squat: DynamicLift,
    pub(crate) deadlift: DynamicLift,
    pub(crate) bench: DynamicLift,
    pub(crate) overhead: DynamicLift,
}

impl DynamicLifts {
    pub(crate) fn new(db: &dyn Database) -> DbResult<Self> {
        let mut rng = thread_rng();
        let ar_opts = [
            AccommodatingResistance::Chains,
            AccommodatingResistance::Bands,
        ];

        let squat_options = lifts_for_type(db, LiftType::Squat, "Squat")?;
        let deadlift_options = lifts_for_type(db, LiftType::Deadlift, "Deadlift")?;
        let bench_options = lifts_for_type(db, LiftType::BenchPress, "Bench Press")?;
        let overhead_options = lifts_for_type(db, LiftType::OverheadPress, "Overhead Press")?;

        let default_choices = DynamicLiftChoices {
            squat: squat_options
                .first()
                .expect("squat options guaranteed to be non-empty")
                .clone(),
            deadlift: deadlift_options
                .first()
                .expect("deadlift options guaranteed to be non-empty")
                .clone(),
            bench: bench_options
                .first()
                .expect("bench options guaranteed to be non-empty")
                .clone(),
            overhead: overhead_options
                .first()
                .expect("overhead options guaranteed to be non-empty")
                .clone(),
        };

        let selections = edit_dynamic_lifts(
            squat_options.clone(),
            deadlift_options.clone(),
            bench_options.clone(),
            overhead_options.clone(),
            default_choices.clone(),
        )?;

        let mut pick_ar = || ar_opts.choose(&mut rng).unwrap().clone();

        Ok(Self {
            squat: DynamicLift {
                lift: selections.squat,
                ar: pick_ar(),
            },
            deadlift: DynamicLift {
                lift: selections.deadlift,
                ar: pick_ar(),
            },
            bench: DynamicLift {
                lift: selections.bench,
                ar: pick_ar(),
            },
            overhead: DynamicLift {
                lift: selections.overhead,
                ar: pick_ar(),
            },
        })
    }
}

fn lifts_for_type(db: &dyn Database, lift_type: LiftType, fallback: &str) -> DbResult<Vec<Lift>> {
    let mut lifts = db.lifts_by_type(lift_type)?;
    if let Some(idx) = lifts.iter().position(|lift| lift.name == fallback) {
        if idx != 0 {
            let lift = lifts.remove(idx);
            lifts.insert(0, lift);
        }
    } else {
        lifts.insert(0, db.get_lift(fallback)?);
    }
    Ok(lifts)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::database::DbResult;
    use crate::models::{LiftExecution, LiftRegion, LiftType, Muscle};

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
}
