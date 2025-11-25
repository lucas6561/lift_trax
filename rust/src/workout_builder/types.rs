use std::collections::HashMap;

use chrono::Weekday;

use crate::database::{Database, DbResult};
use crate::models::{Lift, SetMetric};

/// Types of accommodating resistance applied to a lift.
#[derive(Clone, Debug, PartialEq)]
pub enum AccommodatingResistance {
    Straight,
    Chains,
    Bands,
}

/// Represents a single lift with optional suggested metrics.
#[derive(Clone)]
pub struct SingleLift {
    pub lift: Lift,
    pub metric: Option<SetMetric>,
    pub percent: Option<u32>,
    pub rpe: Option<f32>,
    pub accommodating_resistance: Option<AccommodatingResistance>,
    pub deload: bool,
}

/// Represents a circuit of lifts.
#[derive(Clone)]
pub struct CircuitLift {
    pub circuit_lifts: Vec<SingleLift>,
    pub rounds: u32,
    pub warmup: bool,
}
#[derive(Clone)]
pub struct WorkoutLift {
    pub name: String,
    pub kind: WorkoutLiftKind,
}

#[derive(Clone)]
pub enum WorkoutLiftKind {
    Single(SingleLift),
    Circuit(CircuitLift),
}

/// Collection of lifts to be performed in a workout.
#[derive(Clone)]
pub struct Workout {
    pub lifts: Vec<WorkoutLift>,
}

/// Mapping of weekday to its scheduled workout.
pub type WorkoutWeek = HashMap<Weekday, Workout>;

/// Builder capable of generating multi-week workout waves.
pub trait WorkoutBuilder {
    fn get_wave(&self, num_weeks: usize, db: &dyn Database) -> DbResult<Vec<WorkoutWeek>>;
}
