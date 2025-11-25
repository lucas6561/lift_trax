use std::collections::HashMap;

use crate::database::{Database, DbResult};
use crate::models::{Lift, Muscle, SetMetric};
use crate::random_stack::RandomStack;

use super::SingleLift;

pub struct AccessoryStacks {
    stacks: HashMap<Muscle, RandomStack<Lift>>,
    forearms: Option<RandomStack<Lift>>,
}

impl AccessoryStacks {
    pub fn new(db: &dyn Database) -> DbResult<Self> {
        let required = [
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
        ];

        let mut stacks = HashMap::new();
        for muscle in required {
            let lifts = db.get_accessories_by_muscle(muscle)?;
            if lifts.is_empty() {
                return Err(format!("not enough accessory lifts available for {}", muscle).into());
            }
            stacks.insert(muscle, RandomStack::new(lifts));
        }

        let forearms = {
            let lifts = db.get_accessories_by_muscle(Muscle::Forearm)?;
            if lifts.is_empty() {
                None
            } else {
                Some(RandomStack::new(lifts))
            }
        };

        Ok(Self { stacks, forearms })
    }

    pub fn single(&mut self, muscle: Muscle) -> DbResult<SingleLift> {
        let stack = match self.stacks.get_mut(&muscle) {
            Some(stack) => stack,
            None => {
                return Err(format!("not enough accessory lifts available for {}", muscle).into());
            }
        };
        let lift = match stack.pop() {
            Some(lift) => lift,
            None => {
                return Err(format!("not enough accessory lifts available for {}", muscle).into());
            }
        };
        Ok(SingleLift {
            lift,
            metric: Some(SetMetric::RepsRange { min: 8, max: 12 }),
            percent: None,
            rpe: None,
            accommodating_resistance: None,
            deload: false,
        })
    }

    pub fn forearm(&mut self) -> Option<SingleLift> {
        let stack = self.forearms.as_mut()?;
        stack.pop().map(|lift| SingleLift {
            lift,
            metric: Some(SetMetric::Reps(5)),
            percent: Some(80),
            rpe: None,
            accommodating_resistance: None,
            deload: false,
        })
    }
}
