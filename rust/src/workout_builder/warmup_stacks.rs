use crate::database::{Database, DbResult};
use crate::models::{Lift, LiftRegion, LiftType, Muscle};
use crate::random_stack::RandomStack;

use super::{CircuitLift, SingleLift, WorkoutLift, WorkoutLiftKind};

pub struct WarmupStacks {
    core: RandomStack<Lift>,
    lower_mobility: RandomStack<Lift>,
    upper_mobility: RandomStack<Lift>,
    lower_accessories: RandomStack<Lift>,
    upper_accessories: RandomStack<Lift>,
}

impl WarmupStacks {
    pub fn new(db: &dyn Database) -> DbResult<Self> {
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

    pub fn warmup(&mut self, region: LiftRegion) -> DbResult<WorkoutLift> {
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
