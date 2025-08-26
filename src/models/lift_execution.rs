use crate::weight::Weight;
use chrono::NaiveDate;

/// A single performance of a lift on a given day.
#[derive(Debug, Clone)]
pub struct LiftExecution {
    /// Database identifier for this execution record.
    pub id: Option<i32>,
    /// Date the lift was performed.
    pub date: NaiveDate,
    /// Number of sets performed.
    pub sets: i32,
    /// Repetitions per set.
    pub reps: i32,
    /// Weight used for the lift.
    pub weight: Weight,
    /// Optional rating of perceived exertion.
    pub rpe: Option<f32>,
}
