use crate::database::{Database, DbResult};
use crate::models::{Lift, LiftRegion, LiftType, Muscle};
use crate::random_stack::RandomStack;

use super::{CircuitLift, SingleLift, WorkoutLift, WorkoutLiftKind};

pub(crate) struct WarmupStacks {
    core: RandomStack<Lift>,
    lower_mobility: RandomStack<Lift>,
    upper_mobility: RandomStack<Lift>,
    lower_accessories: RandomStack<Lift>,
    upper_accessories: RandomStack<Lift>,
}

impl WarmupStacks {
    pub(crate) fn new(db: &dyn Database) -> DbResult<Self> {
        let mut lower_accessories =
            db.lifts_by_region_and_type(LiftRegion::LOWER, LiftType::Accessory)?;
        lower_accessories.retain(|l| {
            !l.muscles.contains(&Muscle::Forearm) && !l.muscles.contains(&Muscle::Core)
        });
        if lower_accessories.len() < 2 {
            return Err("not enough accessory lifts available".into());
        }

        let mut upper_accessories =
            db.lifts_by_region_and_type(LiftRegion::UPPER, LiftType::Accessory)?;
        upper_accessories.retain(|l| {
            !l.muscles.contains(&Muscle::Forearm) && !l.muscles.contains(&Muscle::Core)
        });
        if upper_accessories.len() < 2 {
            return Err("not enough accessory lifts available".into());
        }

        let core = RandomStack::new(db.get_accessories_by_muscle(Muscle::Core)?);
        if core.is_empty() {
            return Err("not enough core lifts available".into());
        }

        let lower_mobility =
            RandomStack::new(db.lifts_by_region_and_type(LiftRegion::LOWER, LiftType::Mobility)?);
        if lower_mobility.is_empty() {
            return Err("not enough mobility lifts available".into());
        }

        let upper_mobility =
            RandomStack::new(db.lifts_by_region_and_type(LiftRegion::UPPER, LiftType::Mobility)?);
        if upper_mobility.is_empty() {
            return Err("not enough mobility lifts available".into());
        }

        Ok(Self {
            core,
            lower_mobility,
            upper_mobility,
            lower_accessories: RandomStack::new(lower_accessories),
            upper_accessories: RandomStack::new(upper_accessories),
        })
    }

    pub(crate) fn warmup(&mut self, region: LiftRegion) -> DbResult<WorkoutLift> {
        let core = self.core.pop().ok_or("not enough core lifts available")?;

        let (mobility_stack, accessory_stack) = match region {
            LiftRegion::LOWER => (&mut self.lower_mobility, &mut self.lower_accessories),
            LiftRegion::UPPER => (&mut self.upper_mobility, &mut self.upper_accessories),
        };

        let mobility = mobility_stack
            .pop()
            .ok_or("not enough mobility lifts available")?;
        let accessory_one = accessory_stack
            .pop()
            .ok_or("not enough accessory lifts available")?;
        let accessory_two = accessory_stack
            .pop()
            .ok_or("not enough accessory lifts available")?;

        let mk = |lift: Lift| SingleLift {
            lift,
            metric: None,
            percent: None,
            rpe: None,
            accommodating_resistance: None,
            deload: false,
        };

        Ok(WorkoutLift {
            name: "Warmup Circuit".to_string(),
            kind: WorkoutLiftKind::Circuit(CircuitLift {
                circuit_lifts: vec![mk(mobility), mk(accessory_one), mk(accessory_two), mk(core)],
                rounds: 3,
                warmup: true,
            }),
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::database::DbResult;
    use crate::models::{LiftExecution, LiftType};

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
}
