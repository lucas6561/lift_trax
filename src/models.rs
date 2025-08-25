//! Core data structures representing lifts and their execution history.

use crate::weight::Weight;
use chrono::NaiveDate;

/// A weight-lifting movement tracked by the application.
///
/// Lifts are uniquely identified by their [`name`]; any database identifiers
/// are treated as internal implementation details.
#[derive(Debug)]
pub struct Lift {
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
