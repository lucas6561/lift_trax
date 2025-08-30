use crate::weight::Weight;
use chrono::NaiveDate;
use serde::{Deserialize, Serialize};

/// Details for a single set within a lift execution.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ExecutionSet {
    /// Repetitions completed in this set.
    pub reps: i32,
    /// Weight used for this set.
    pub weight: Weight,
    /// Optional rating of perceived exertion for this set.
    pub rpe: Option<f32>,
}

/// A single performance of a lift on a given day.
#[derive(Debug, Clone)]
pub struct LiftExecution {
    /// Database identifier for this execution record.
    pub id: Option<i32>,
    /// Date the lift was performed.
    pub date: NaiveDate,
    /// Individual set details.
    pub sets: Vec<ExecutionSet>,
    /// Free-form notes about this execution.
    pub notes: String,
}
