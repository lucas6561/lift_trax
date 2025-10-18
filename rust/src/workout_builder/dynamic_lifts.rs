use rand::seq::SliceRandom;
use rand::thread_rng;

use crate::database::{Database, DbResult};
use crate::models::Lift;

use super::AccommodatingResistance;

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
            AccommodatingResistance::Straight,
            AccommodatingResistance::Chains,
            AccommodatingResistance::Bands,
        ];

        let mut pick = |lifts: Vec<Lift>| -> DbResult<DynamicLift> {
            let lift = lifts
                .choose(&mut rng)
                .ok_or("not enough lifts available")?
                .clone();
            let ar = ar_opts.choose(&mut rng).unwrap().clone();
            Ok(DynamicLift { lift, ar })
        };

        let squat = db.get_lift("Squat")?;
        let deadlift = db.get_lift("Deadlift")?;
        let bench = db.get_lift("Bench Press")?;
        let overhead = db.get_lift("Overhead Press")?;

        Ok(Self {
            squat: pick(vec![squat])?,
            deadlift: pick(vec![deadlift])?,
            bench: pick(vec![bench])?,
            overhead: pick(vec![overhead])?,
        })
    }
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
                executions: Vec::<LiftExecution>::new(),
            })
        }

        fn lifts_by_type(&self, _lift_type: LiftType) -> DbResult<Vec<Lift>> {
            unimplemented!()
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
            AccommodatingResistance::Straight,
            AccommodatingResistance::Chains,
            AccommodatingResistance::Bands,
        ];

        for lift in [&lifts.squat, &lifts.deadlift, &lifts.bench, &lifts.overhead] {
            assert!(valid.contains(&lift.ar));
        }
    }
}
