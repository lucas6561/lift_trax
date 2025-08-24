//! Core data structures representing lifts and their execution history.

use chrono::NaiveDate;
use crate::weight::Weight;

/// A weight-lifting movement tracked by the application.
#[derive(Debug)]
pub struct Lift {
    /// Row identifier of the lift in the database.
    pub id: i32,
    /// Name of the movement, e.g. "Bench".
    pub name: String,
    /// Muscles worked by this lift.
    pub muscles: Vec<String>,
    /// Recorded executions of this lift, most recent first.
    pub executions: Vec<LiftExecution>,
}

/// A single performance of a lift on a given day.
#[derive(Debug)]
pub struct LiftExecution {
    /// Date the lift was performed.
    pub date: NaiveDate,
    /// Number of sets performed.
    pub sets: i32,
    /// Repetitions per set.
    pub reps: i32,
    /// Weight used in pounds.
    pub weight: Weight,
    /// Optional rating of perceived exertion.
    pub rpe: Option<f32>,
}
