use std::fmt;

#[derive(Clone, Copy, PartialEq, Eq, Debug)]
pub enum WeightUnit {
    POUNDS,
    KILOGRAMS,
}
#[derive(Clone, Copy, Debug)]
pub struct Weight {
    pounds: f64,
}

const POUNDS_PER_KILOGRAMS: f64 = 2.20462;

impl fmt::Display for Weight {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        write!(f, "{} lb", self.pounds())
    }
}
impl Weight {
    pub fn new(unit: WeightUnit, value: f64) -> Weight {
        match unit {
            WeightUnit::POUNDS => Weight { pounds: value },
            WeightUnit::KILOGRAMS => Weight {
                pounds: value * POUNDS_PER_KILOGRAMS,
            },
        }
    }

    pub fn pounds(&self) -> f64 {
        self.pounds
    }

    pub fn kilograms(&self) -> f64 {
        self.pounds / POUNDS_PER_KILOGRAMS
    }
}
